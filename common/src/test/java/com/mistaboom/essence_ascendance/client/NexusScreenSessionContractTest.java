package com.mistaboom.essence_ascendance.client;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

/** Runs the compiled slotless-screen lifecycle without creating a Minecraft window. */
final class NexusScreenSessionContractTest {
    private static final String CLIENT = "com/mistaboom/essence_ascendance/client/";
    private static final String HOST = CLIENT + "ui/fullscreen/FullscreenScreen";
    private static final String NEXUS = CLIENT + "AscendanceNexusScreen";
    private static final String MENU = "com/mistaboom/essence_ascendance/nexus/AscendanceNexusMenu";
    private static int checks;

    static int run() throws Exception {
        checks = 0;
        var nexus = load(NEXUS);
        check(nexus.superName.equals(HOST) && load(CLIENT + "AscendanceArchiveScreen").superName.equals(HOST),
                "Nexus and Archive must share the ordinary fullscreen host, outside inventory-screen overlays");
        check(nexus.interfaces.contains("net/minecraft/client/gui/screens/inventory/MenuAccess"),
                "The ordinary Nexus must still expose its server authorization session to native screen opening");
        var menu = load(MENU);
        check(menu.methods.stream().flatMap(method -> calls(method).stream()).noneMatch(call -> call.name.equals("addSlot")),
                "The authorization session must not acquire inventory slots");

        var fixture = new Lifecycle(nexus);
        check(fixture.execute("getMenu") == fixture.ownedMenu,
                "Native menu opening must bind the exact menu owned by this screen");
        check(fixture.execute("isPauseScreen").equals(0), "Nexus must not pause the world while its session is open");
        fixture.execute("tick");
        fixture.expect(0, 0, "A live owned session stays open");

        for (String state : List.of("dead", "removed", "no connection", "replacement menu", "no player")) {
            fixture = new Lifecycle(nexus);
            switch (state) {
                case "dead" -> fixture.alive = false;
                case "removed" -> fixture.removed = true;
                case "no connection" -> fixture.connection = null;
                case "replacement menu" -> fixture.currentMenu = new Object();
                case "no player" -> fixture.player = null;
            }
            fixture.execute("tick");
            boolean ownsMenu = fixture.player != null && fixture.currentMenu == fixture.ownedMenu;
            fixture.expect(ownsMenu ? 1 : 0, ownsMenu ? 0 : 1,
                    "Invalid session closes immediately without a draft prompt: " + state);
        }

        fixture = new Lifecycle(nexus);
        fixture.execute("closeNexus");
        fixture.expect(1, 0, "Explicit close must notify the server for the owned session");
        fixture = new Lifecycle(nexus);
        fixture.currentMenu = new Object();
        fixture.execute("closeNexus");
        fixture.expect(0, 1, "An obsolete Nexus may dismiss itself but must not close a replacement menu");
        fixture = new Lifecycle(nexus);
        fixture.activeScreen = new Object();
        fixture.execute("closeNexus");
        fixture.expect(0, 0, "A stale callback must not dismiss a replacement screen");
        fixture = new Lifecycle(nexus);
        fixture.client = null;
        fixture.execute("tick");
        fixture.expect(0, 0, "Teardown before client attachment must remain harmless");

        for (var method : nexus.methods) for (var call : calls(method))
            if (call.owner.equals(HOST) && call.name.equals("onClose"))
                check(method.name.equals("closeNexus"), "Every departure path must use the session-aware close helper");
        var removal = calls(method(nexus, "removed"));
        check(removal.stream().anyMatch(call -> call.owner.equals(MENU) && call.name.equals("removed"))
                        && removal.stream().anyMatch(call -> call.owner.equals(HOST) && call.name.equals("removed")),
                "Screen replacement retains both menu cleanup and shared fullscreen cleanup");
        var input = calls(method(nexus, "keyPressed"));
        check(index(input, HOST, "keyPressed") >= 0 && index(input, HOST, "keyPressed") < index(input, NEXUS, "onClose")
                        && input.stream().anyMatch(call -> call.name.equals("matches")),
                "Inventory-key close must follow composed input and use the normal draft-aware departure policy");
        return checks;
    }

    /** Minimal native boundary: interpret the real compiled guards, recording only their close effects. */
    private static final class Lifecycle {
        final ClassNode nexus;
        final Object screen = new Object(), ownedMenu = new Object();
        Object client = new Object(), player = new Object(), connection = new Object();
        Object activeScreen = screen, currentMenu = ownedMenu;
        boolean alive = true, removed;
        int menuCloses, screenCloses;

        Lifecycle(ClassNode nexus) { this.nexus = nexus; }

        Object execute(String name) {
            var method = method(nexus, name);
            var stack = new ArrayList<Object>();
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                int opcode = instruction.getOpcode();
                if (opcode < 0) continue;
                switch (opcode) {
                    case Opcodes.ALOAD -> stack.add(screen);
                    case Opcodes.ICONST_0 -> stack.add(0);
                    case Opcodes.ICONST_1 -> stack.add(1);
                    case Opcodes.GETFIELD -> {
                        if (pop(stack) == null) throw new AssertionError("Lifecycle dereferenced a missing native object in " + name);
                        stack.add(switch (((FieldInsnNode) instruction).name) {
                            case "minecraft" -> client;
                            case "player" -> player;
                            case "screen" -> activeScreen;
                            case "menu" -> ownedMenu;
                            case "containerMenu" -> currentMenu;
                            default -> throw new AssertionError("Unexpected lifecycle field " + ((FieldInsnNode) instruction).name);
                        });
                    }
                    case Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL -> {
                        if (pop(stack) == null) throw new AssertionError("Lifecycle called a missing native object in " + name);
                        var call = (MethodInsnNode) instruction;
                        switch (call.name) {
                            case "tick" -> check(call.owner.equals(HOST), "Tick delegates only to the ordinary host");
                            case "canPromptForExit" -> stack.add(execute(call.name));
                            case "getConnection" -> stack.add(connection);
                            case "isAlive" -> stack.add(alive ? 1 : 0);
                            case "isRemoved" -> stack.add(removed ? 1 : 0);
                            case "closeNexus" -> execute(call.name);
                            case "closeContainer" -> menuCloses++;
                            case "onClose" -> {
                                check(call.owner.equals(HOST), "Invalid session must bypass staged draft prompts");
                                screenCloses++;
                            }
                            default -> throw new AssertionError("Unexpected lifecycle call " + call.name);
                        }
                    }
                    case Opcodes.IFNULL, Opcodes.IFNONNULL, Opcodes.IFEQ, Opcodes.IFNE,
                         Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE, Opcodes.GOTO -> {
                        boolean jump = switch (opcode) {
                            case Opcodes.IFNULL -> pop(stack) == null;
                            case Opcodes.IFNONNULL -> pop(stack) != null;
                            case Opcodes.IFEQ -> pop(stack).equals(0);
                            case Opcodes.IFNE -> !pop(stack).equals(0);
                            case Opcodes.IF_ACMPEQ -> pop(stack) == pop(stack);
                            case Opcodes.IF_ACMPNE -> pop(stack) != pop(stack);
                            default -> true;
                        };
                        if (jump) instruction = ((JumpInsnNode) instruction).label;
                    }
                    case Opcodes.ARETURN, Opcodes.IRETURN -> { return pop(stack); }
                    case Opcodes.RETURN -> { return null; }
                    default -> throw new AssertionError("Unexpected lifecycle opcode " + opcode + " in " + name);
                }
            }
            throw new AssertionError("Lifecycle method did not return: " + name);
        }

        void expect(int menus, int screens, String message) {
            check(menuCloses == menus && screenCloses == screens,
                    message + " (menu closes=" + menuCloses + ", screen closes=" + screenCloses + ")");
        }
    }

    private static Object pop(List<Object> stack) { return stack.removeLast(); }

    private static ClassNode load(String name) throws Exception {
        try (var stream = NexusScreenSessionContractTest.class.getResourceAsStream("/" + name + ".class")) {
            if (stream == null) throw new AssertionError("Missing Nexus lifecycle class " + name);
            var node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream().filter(method -> method.name.equals(name) && (method.access & Opcodes.ACC_BRIDGE) == 0)
                .findFirst().orElseThrow(() -> new AssertionError("Missing " + owner.name + "." + name));
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        var result = new ArrayList<MethodInsnNode>();
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) result.add(call);
        return result;
    }

    private static int index(List<MethodInsnNode> calls, String owner, String name) {
        for (int i = 0; i < calls.size(); i++) if (calls.get(i).owner.equals(owner) && calls.get(i).name.equals(name)) return i;
        return -1;
    }

    private static void check(boolean pass, String message) {
        checks++;
        if (!pass) throw new AssertionError(message);
    }
}
