package com.mistaboom.essence_ascendance.client;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** The 26.1 extraction equivalent of the 1.21.1 fullscreen dispatch contract. */
final class FullscreenRenderContract2612Test {
    private static final String UI = "com/mistaboom/essence_ascendance/client/ui/fullscreen/";
    private static final String ORDINARY = UI + "FullscreenScreen";
    private static final String CONTAINER = UI + "FullscreenContainerScreen";
    private static final String COMPOSITION = UI + "FullscreenComposition";
    private static int checks;

    static int run() throws Exception {
        checks = 0;
        var nativeScreen = load("net/minecraft/client/gui/screens/Screen");
        var frame = calls(method(nativeScreen, "extractRenderStateWithTooltipAndSubtitles"));
        int backgroundIndex = index(frame, nativeScreen.name, "extractBackground");
        int contentIndex = index(frame, nativeScreen.name, "extractRenderState");
        check(backgroundIndex >= 0 && contentIndex > backgroundIndex,
                "Native 26.1 frame extracts the background before content");
        var ordinary = load(ORDINARY);
        var container = load(CONTAINER);
        for (var host : List.of(ordinary, container)) {
            var background = calls(method(host, "extractBackground"));
            check(count(background, COMPOSITION, "renderBase") == 1,
                    "26.1 background extraction must compose exactly one base pass: " + host.name);
            check(count(background, host.name, "beforeFullscreenFrame") == 1
                            && count(background, host.name, "refreshFullscreen") == 1
                            && index(background, host.name, "beforeFullscreenFrame")
                            < index(background, host.name, "refreshFullscreen")
                            && index(background, host.name, "refreshFullscreen")
                            < index(background, COMPOSITION, "renderBase"),
                    "26.1 must prepare one scene before the native background pass: " + host.name);
            check(background.stream().noneMatch(call -> call.name.equals("extractBackground")
                            || call.name.equals("renderBlurredBackground")),
                    "Fullscreen base must not recurse or blur: " + host.name);

            var render = calls(method(host, "extractRenderState"));
            String parent = host == ordinary ? "net/minecraft/client/gui/screens/Screen"
                    : "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen";
            int nativePass = index(render, parent, "extractRenderState");
            int overlays = index(render, COMPOSITION, "renderOverlays");
            check(nativePass >= 0 && overlays > nativePass && count(render, parent, "extractRenderState") == 1,
                    "Native extraction must precede one shared overlay pass: " + host.name);
            check(count(render, COMPOSITION, "renderBase") == 0,
                    "Base composition belongs only to background extraction: " + host.name);
            check(count(render, host.name, "beforeFullscreenFrame") == 0
                            && count(render, host.name, "refreshFullscreen") == 0,
                    "Content extraction must reuse the same scene without advancing state twice: " + host.name);
            var refresh = calls(method(host, "refreshFullscreen"));
            check(refresh.stream().anyMatch(call -> call.name.equals("clearFocus"))
                            && refresh.stream().anyMatch(call -> call.name.equals("clearWidgets"))
                            && count(refresh, COMPOSITION, "update") == 1,
                    "Recomposition must clear native widgets and focus: " + host.name);
            for (var input : List.of("mouseClicked", "mouseDragged", "mouseReleased", "mouseScrolled",
                    "keyPressed", "charTyped")) {
                var inputCalls = calls(method(host, input));
                check(index(inputCalls, host.name, "refreshFullscreen") >= 0
                                && count(inputCalls, COMPOSITION, input) >= 1,
                        "26.1 native input must reach the shared composition: " + host.name + "." + input);
            }
            check(calls(method(host, "mouseClicked")).stream().anyMatch(call ->
                            call.owner.equals(parent) && call.name.equals("mouseClicked")
                                    && call.desc.equals("(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z")),
                    "Unconsumed clicks retain the native double-click event: " + host.name);
            var typed = method(host, "charTyped");
            boolean narrowed = false;
            for (var instruction : typed.instructions)
                if (instruction.getOpcode() == Opcodes.I2C) narrowed = true;
            check(!narrowed, "Unicode input must not narrow native code points: " + host.name);
            check(count(calls(method(host, "init")), UI + "FullscreenSidebarCompatibility", "register") == 1,
                    "Optional sidebar registration stays on both fullscreen hosts");
        }
        check(calls(method(container, "extractLabels")).stream().noneMatch(call -> call.name.equals("renderBase")),
                "Menu labels must not redraw the base");
        var machine = calls(method(load("com/mistaboom/essence_ascendance/client/MachineContainerScreen"),
                "extractRenderState"));
        var nativeMachine = "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen";
        check(count(machine, nativeMachine, "extractRenderState") == 1
                        && index(machine, "com/mistaboom/essence_ascendance/client/MachineContainerScreen",
                        "updateMachineWidgets") < index(machine, nativeMachine, "extractRenderState"),
                "Machine screens must refresh widgets before the native menu extraction path");

        var mixin = load("com/mistaboom/essence_ascendance/mixin/FullscreenWidgetLayerMixin");
        var predicate = method(mixin, "essenceAscendance$composedWidgetLayer");
        var hosts = new ArrayList<String>();
        for (var instruction : predicate.instructions)
            if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.INSTANCEOF)
                hosts.add(type.desc);
        check(hosts.equals(List.of(ORDINARY, CONTAINER)),
                "Widget suppression must cover both fullscreen hosts");
        for (var name : List.of("essenceAscendance$composedWidgets", "essenceAscendance$composedRenderables",
                "essenceAscendance$composedEventWidgets", "essenceAscendance$finishComposedWidgets",
                "essenceAscendance$composedNarration"))
            check(method(mixin, name).visibleAnnotations != null || method(mixin, name).invisibleAnnotations != null,
                    "Required widget lifecycle injection missing: " + name);
        try (var stream = FullscreenRenderContract2612Test.class.getResourceAsStream("/essence_ascendance.mixins.json")) {
            check(stream != null && new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                            .contains("\"FullscreenWidgetLayerMixin\""),
                    "26.1 packaged mixin configuration must install the widget policy");
        }
        return checks;
    }

    private static ClassNode load(String name) throws Exception {
        try (InputStream input = FullscreenRenderContract2612Test.class.getResourceAsStream("/" + name + ".class")) {
            if (input == null) throw new AssertionError("Missing compiled 26.1 class " + name);
            return read(input);
        }
    }

    private static ClassNode read(InputStream input) throws Exception {
        var result = new ClassNode();
        new ClassReader(input).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return result;
    }

    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream().filter(method -> method.name.equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + owner.name + "." + name));
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        var result = new ArrayList<MethodInsnNode>();
        for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call) result.add(call);
        return result;
    }

    private static long count(List<MethodInsnNode> calls, String owner, String name) {
        return calls.stream().filter(call -> call.owner.equals(owner) && call.name.equals(name)).count();
    }

    private static int index(List<MethodInsnNode> calls, String owner, String name) {
        for (int i = 0; i < calls.size(); i++)
            if (calls.get(i).owner.equals(owner) && calls.get(i).name.equals(name)) return i;
        return -1;
    }

    private static void check(boolean pass, String message) {
        checks++;
        if (!pass) throw new AssertionError(message);
    }
}
