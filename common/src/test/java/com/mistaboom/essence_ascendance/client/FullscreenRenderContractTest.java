package com.mistaboom.essence_ascendance.client;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

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
        inventoryWidgetHook();
        return checks;
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
        check(count(calls(method(screen, "removeWidget",
                        "(Lnet/minecraft/client/gui/components/events/GuiEventListener;)V")), "java/util/List", "remove") == 3,
                "Native widget removal covers the render, input and narration collections on each loader");
    }

    private static void inventoryWidgetHook() throws Exception {
        ClassNode hook = readResource("com/mistaboom/essence_ascendance/mixin/FullscreenInventoryWidgetMixin");
        MethodNode handler = method(hook, "essenceAscendance$suppressInventoryWidgets",
                "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        var annotations = new ArrayList<org.objectweb.asm.tree.AnnotationNode>();
        if (handler.visibleAnnotations != null) annotations.addAll(handler.visibleAnnotations);
        if (handler.invisibleAnnotations != null) annotations.addAll(handler.invisibleAnnotations);
        var inject = annotations.stream().filter(annotation -> annotation.desc.endsWith("/Inject;"))
                .findFirst().orElseThrow(() -> new AssertionError("Missing fullscreen initialization hook"));
        @SuppressWarnings("unchecked")
        List<String> targets = (List<String>)annotationValue(inject, "method");
        check(targets.equals(List.of("init(Lnet/minecraft/client/Minecraft;II)V", "rebuildWidgets()V")),
                "The shared filter covers first initialization and resize/rebuild");
        @SuppressWarnings("unchecked")
        List<org.objectweb.asm.tree.AnnotationNode> at =
                (List<org.objectweb.asm.tree.AnnotationNode>)annotationValue(inject, "at");
        check(at.size() == 1 && "RETURN".equals(annotationValue(at.getFirst(), "value")),
                "Injected controls are filtered after optional initialization listeners have completed");
        check(count(calls(handler), UI + "FullscreenSidebarCompatibility", "suppressInjectedWidgets") == 1,
                "The client hook delegates once to the shared fullscreen policy");
        try (InputStream stream = FullscreenRenderContractTest.class.getResourceAsStream("/essence_ascendance.mixins.json")) {
            check(stream != null && new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                            .contains("\"FullscreenInventoryWidgetMixin\""),
                    "The lifecycle hook is registered in the common client mixin configuration");
        }
    }

    private static Object annotationValue(org.objectweb.asm.tree.AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2)
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        throw new AssertionError("Missing " + key + " annotation value");
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
