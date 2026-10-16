package com.mistaboom.essence_ascendance.client;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/** Regression for the actual 1.21.1 background/blur dispatch, without an OpenGL context. */
final class FullscreenRenderContractTest {
    private static final String SCREEN = "net/minecraft/client/gui/screens/Screen";
    private static final String CONTAINER = "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen";
    private static final String UI = "com/mistaboom/essence_ascendance/client/ui/fullscreen/";
    private static final String RENDER = "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V";
    private static int checks;

    static int run() throws Exception {
        checks = 0;
        nativeLifecycle(readResource(SCREEN), readResource(CONTAINER));
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve(".gradle/loom-cache/minecraftMaven/net/minecraft")))
            root = root.getParent();
        check(root != null, "Mapped loader cache is available for render lifecycle verification");
        int loaders = 0;
        try (var files = Files.walk(root.resolve(".gradle/loom-cache/minecraftMaven/net/minecraft"))) {
            for (Path path : files.filter(file -> file.getFileName().toString().startsWith("neoforge-")
                    && file.toString().endsWith(".jar") && !file.toString().endsWith("-sources.jar")).toList()) {
                try (var jar = new JarFile(path.toFile())) {
                    nativeLifecycle(read(jar.getInputStream(jar.getJarEntry(SCREEN + ".class"))),
                            read(jar.getInputStream(jar.getJarEntry(CONTAINER + ".class"))));
                    loaders++;
                }
            }
        }
        check(loaders > 0, "NeoForge background dispatch was inspected as well as the common/Fabric mapping");

        ClassNode ordinary = readResource(UI + "FullscreenScreen");
        List<MethodInsnNode> background = calls(method(ordinary, "renderBackground", RENDER));
        check(count(background, UI + "FullscreenComposition", "renderBase") == 1,
                "Ordinary fullscreen draws its composition exactly once through the native background hook");
        check(background.stream().noneMatch(call -> call.name.equals("renderBackground")
                        || call.name.equals("renderBlurredBackground") || call.name.equals("processBlurEffect")),
                "The fullscreen background hook must not blur the composed interface");
        List<MethodInsnNode> render = calls(method(ordinary, "render", RENDER));
        check(count(render, UI + "FullscreenComposition", "renderBase") == 0,
                "Ordinary fullscreen must not draw its base before Minecraft dispatches the background hook");
        hostOrder(render, SCREEN);

        ClassNode container = readResource(UI + "FullscreenContainerScreen");
        hostOrder(calls(method(container, "render", RENDER)), CONTAINER);
        check(count(calls(method(container, "renderBg", "(Lnet/minecraft/client/gui/GuiGraphics;FII)V")),
                        UI + "FullscreenComposition", "renderBase") == 1,
                "Menu fullscreen still draws its composition through the container background hook");
        List<MethodInsnNode> machine = calls(method(readResource(
                "com/mistaboom/essence_ascendance/client/MachineContainerScreen"), "render", RENDER));
        check(count(machine, CONTAINER, "render") == 1
                        && machine.stream().noneMatch(call -> call.name.equals("renderBackground")),
                "Machine screens delegate one background pass to vanilla instead of drawing it twice");
        for (ClassNode host : List.of(ordinary, container))
            check(count(calls(method(host, "init", "()V")), UI + "FullscreenSidebarCompatibility", "register") == 1,
                    "Both fullscreen hosts retain optional FTB Library sidebar registration");
        widgetLayer(ordinary, container);
        return checks;
    }

    private static void widgetLayer(ClassNode ordinary, ClassNode container) throws Exception {
        ClassNode policy = readResource("com/mistaboom/essence_ascendance/mixin/FullscreenWidgetLayerMixin");
        var predicate = method(policy, "essenceAscendance$composedWidgetLayer", "()Z");
        List<String> hosts = new ArrayList<>();
        for (var instruction : predicate.instructions)
            if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.INSTANCEOF) hosts.add(type.desc);
        check(hosts.equals(List.of(UI + "FullscreenScreen", UI + "FullscreenContainerScreen")),
                "Widget policy belongs to both shared fullscreen hosts, not a concrete screen or widget identity");
        for (String handler : List.of("essenceAscendance$composedWidgets", "essenceAscendance$composedRenderables",
                "essenceAscendance$composedEventWidgets")) {
            var hooked = policy.methods.stream().filter(method -> method.name.equals(handler)).findFirst().orElseThrow();
            var calls = calls(hooked);
            check(index(calls, policy.name, "essenceAscendance$composedWidgetLayer") == 0
                            && calls.stream().anyMatch(call -> call.name.equals("cancel") || call.name.equals("setReturnValue")),
                    "Native registration is canceled only for composition-owned fullscreen widgets: " + handler);
        }
        check(injection(policy, "essenceAscendance$composedWidgets").containsAll(List.of("addRenderableWidget", "addWidget"))
                        && injection(policy, "essenceAscendance$composedRenderables").contains("addRenderableOnly")
                        && injection(policy, "essenceAscendance$composedEventWidgets").contains("addEventWidget"),
                "Render, input/narration and native loader-event registration paths are covered");
        check(injection(policy, "essenceAscendance$finishComposedWidgets").containsAll(
                        List.of("init(Lnet/minecraft/client/Minecraft;II)V", "rebuildWidgets"))
                        && injection(policy, "essenceAscendance$composedNarration").contains("runNarration"),
                "Initialization, resize/rebuild and narration also clear direct widget-list additions");
        var cleanup = calls(method(policy, "essenceAscendance$clearNativeWidgets", "()V"));
        check(count(cleanup, policy.name, "clearWidgets") == 1 && count(cleanup, policy.name, "clearFocus") == 1,
                "Cleanup removes native rendering, input, narration and stale focus together");
        for (ClassNode host : List.of(ordinary, container)) {
            var refresh = calls(method(host, "refreshFullscreen", "()V"));
            check(refresh.stream().anyMatch(call -> call.name.equals("clearWidgets"))
                            && refresh.stream().anyMatch(call -> call.name.equals("clearFocus"))
                            && count(refresh, UI + "FullscreenComposition", "update") == 1,
                    "Late native additions are cleared before shared fullscreen render/input without replacing composed controls");
        }
        try (var stream = FullscreenRenderContractTest.class.getResourceAsStream("/essence_ascendance.mixins.json")) {
            check(stream != null && new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                            .contains("\"FullscreenWidgetLayerMixin\""), "Fullscreen widget policy is installed on both loaders");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> injection(ClassNode policy, String handler) {
        var method = policy.methods.stream().filter(candidate -> candidate.name.equals(handler)).findFirst().orElseThrow();
        var annotations = new ArrayList<org.objectweb.asm.tree.AnnotationNode>();
        if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
        var inject = annotations.stream().filter(annotation -> annotation.desc.equals(
                "Lorg/spongepowered/asm/mixin/injection/Inject;")).findFirst().orElseThrow();
        return (List<String>) inject.values.get(inject.values.indexOf("method") + 1);
    }

    private static void nativeLifecycle(ClassNode screen, ClassNode container) {
        List<MethodInsnNode> screenRender = calls(method(screen, "render", RENDER));
        int background = index(screenRender, SCREEN, "renderBackground");
        int widget = index(screenRender, "net/minecraft/client/gui/components/Renderable", "render");
        check(background >= 0 && widget > background && screenRender.get(background).getOpcode() == Opcodes.INVOKEVIRTUAL,
                "Minecraft dispatches the overridden background before rendering widgets");
        check(calls(method(screen, "renderBackground", RENDER)).stream()
                        .anyMatch(call -> call.name.equals("renderBlurredBackground")),
                "Vanilla ordinary-screen background includes the blur that caused the regression");
        List<MethodInsnNode> containerRender = calls(method(container, "render", RENDER));
        // NeoForge inlines Screen.render to insert its background event; Fabric
        // delegates to Screen. Both must still dispatch one background pass.
        long inherited = count(containerRender, SCREEN, "render");
        long direct = count(containerRender, CONTAINER, "renderBackground");
        check(inherited + direct == 1, "Each loader dispatches exactly one container background pass");
        if (direct == 1) check(index(containerRender, CONTAINER, "renderBackground")
                        < index(containerRender, "net/minecraft/client/gui/components/Renderable", "render"),
                "NeoForge's inlined background pass still precedes widgets");
        List<MethodInsnNode> containerBackground = calls(method(container, "renderBackground", RENDER));
        check(count(containerBackground, CONTAINER, "renderBg") == 1
                        && containerBackground.stream().noneMatch(call -> call.name.equals("renderBlurredBackground")),
                "Container background invokes renderBg once without ordinary-screen blur");
        for (String[] lifecycle : new String[][] { { "init", "(Lnet/minecraft/client/Minecraft;II)V" },
                { "rebuildWidgets", "()V" } }) {
            List<MethodInsnNode> lifecycleCalls = calls(method(screen, lifecycle[0], lifecycle[1]));
            int initialize = index(lifecycleCalls, SCREEN, "init");
            int post = index(lifecycleCalls, "net/neoforged/neoforge/client/event/ScreenEvent$Init$Post", "<init>");
            check(initialize >= 0 && (post < 0 || post > initialize),
                    "The initialization return boundary follows both the host init and NeoForge's post listeners");
        }
        check(count(calls(method(screen, "clearWidgets", "()V")), "java/util/List", "clear") == 3,
                "Shared native widget cleanup clears rendering, input and narration collections on each loader");
        var eventAdder = screen.methods.stream().filter(method -> method.name.equals("addEventWidget")).findFirst();
        if (eventAdder.isPresent()) check(count(calls(eventAdder.get()), "java/util/List", "add") == 3
                        && calls(eventAdder.get()).stream().noneMatch(call -> call.name.equals("addRenderableWidget") || call.name.equals("addWidget")),
                "NeoForge's native event adapter bypasses normal widget registration and needs its own generic interception");
        check(count(calls(method(screen, "removeWidget",
                        "(Lnet/minecraft/client/gui/components/events/GuiEventListener;)V")), "java/util/List", "remove") == 3,
                "Native widget removal covers the render, input and narration collections on each loader");
    }

    private static void hostOrder(List<MethodInsnNode> render, String parent) {
        int vanilla = index(render, parent, "render");
        int overlays = index(render, UI + "FullscreenComposition", "renderOverlays");
        check(count(render, parent, "render") == 1 && vanilla >= 0 && overlays > vanilla,
                "Native widgets/container rendering precedes the shared foreground overlays");
        check(render.subList(0, vanilla).stream().anyMatch(call -> call.name.equals("refreshFullscreen")),
                "The composition is prepared before native background dispatch");
    }

    private static ClassNode readResource(String name) throws Exception {
        return read(FullscreenRenderContractTest.class.getResourceAsStream("/" + name + ".class"));
    }

    private static ClassNode read(InputStream stream) throws Exception {
        if (stream == null) throw new AssertionError("Missing render-contract class");
        ClassNode result = new ClassNode();
        try (stream) { new ClassReader(stream).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES); }
        return result;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
                .findFirst().orElseThrow(() -> new AssertionError("Missing " + owner.name + "." + name));
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        List<MethodInsnNode> calls = new ArrayList<>();
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) calls.add(call);
        return calls;
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
