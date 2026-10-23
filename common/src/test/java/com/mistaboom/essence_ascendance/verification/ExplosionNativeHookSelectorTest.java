package com.mistaboom.essence_ascendance.verification;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Checks compiled explosion bindings against each loader's exact native artifact without loading game classes. */
public final class ExplosionNativeHookSelectorTest {
    private static final String EXPLOSION = "net/minecraft/world/level/ServerExplosion";
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String WRAP = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static int checks;

    public static void main(String[] args) throws Exception {
        for (String loader : List.of("fabric", "neoforge")) {
            ClassNode nativeClass = read(TargetTestInputs.nativeClass(loader, EXPLOSION));
            inspect(loader, nativeClass, "ExplosionGuardKnockbackMixin", List.of(
                    "explosionGuardHit", "explosionGuardImpulse", "explosionGuardPacket"));
            inspect(loader, nativeClass, "ContainmentExplosionMixin", List.of("containedDamage", "containedKnockback"));
        }
        System.out.println("Explosion native hook selectors passed: " + checks
                + " (compiled hooks, exact Fabric/NeoForge artifacts, ambiguous-selector negative controls; no world)");
    }

    private static void inspect(String loader, ClassNode nativeClass, String name, List<String> handlers) throws Exception {
        ClassNode mixin = read(Files.newInputStream(TargetTestInputs.compiledClass("common",
                "com/mistaboom/essence_ascendance/mixin/" + name)));
        AnnotationNode target = annotation(mixin.visibleAnnotations, mixin.invisibleAnnotations, MIXIN);
        check(value(target, "value").equals(List.of(Type.getObjectType(EXPLOSION))), name + " targets ServerExplosion");
        for (String handler : handlers) {
            String label = loader + " " + name + "." + handler;
            MethodNode method = mixin.methods.stream().filter(candidate -> candidate.name.equals("essenceAscendance$" + handler))
                    .findFirst().orElseThrow(() -> new AssertionError("Missing compiled handler " + label));
            AnnotationNode wrap = annotation(method.visibleAnnotations, method.invisibleAnnotations, WRAP);
            validate(nativeClass, wrap, label);
            if (loader.equals("neoforge")) {
                // Recreate the old annotation against the real bridge + body, not synthetic source text.
                AnnotationNode ambiguous = new AnnotationNode(wrap.desc);
                wrap.accept(ambiguous);
                int selectorIndex = ambiguous.values.indexOf("method") + 1;
                ambiguous.values.set(selectorIndex, List.of("hurtEntities"));
                try {
                    validate(nativeClass, ambiguous, label + " old selector");
                } catch (AssertionError expected) {
                    check(expected.getMessage().contains("Ambiguous unqualified selector"),
                            label + " negative control rejects the old selector for overload ambiguity");
                    continue;
                }
                throw new AssertionError(label + " accepted the old ambiguous selector");
            }
        }
    }

    private static void validate(ClassNode nativeClass, AnnotationNode wrap, String label) {
        for (String bound : List.of("require", "expect", "allow"))
            check(Integer.valueOf(1).equals(value(wrap, bound)), label + " retains " + bound + "=1");
        Object rawSelectors = value(wrap, "method");
        check(rawSelectors instanceof List<?>, label + " has compiled method selectors");
        Set<MethodNode> selected = new LinkedHashSet<>();
        for (Object raw : (List<?>) rawSelectors) {
            String selector = (String) raw;
            check(!selector.contains("*"), label + " requires bounded native signatures");
            boolean exact = selector.indexOf('(') >= 0;
            List<MethodNode> matches = nativeClass.methods.stream().filter(method -> exact
                    ? (method.name + method.desc).equals(selector) : method.name.equals(selector)).toList();
            check(exact || matches.size() <= 1, "Ambiguous unqualified selector " + selector + " in " + label);
            // Exact alternatives may be absent on one loader; the union must still contain the native call.
            selected.addAll(matches);
        }
        check(!selected.isEmpty(), label + " selects an existing native method");
        Object rawAt = value(wrap, "at");
        List<?> sites = rawAt instanceof List<?> list ? list : List.of(rawAt);
        check(sites.size() == 1 && sites.getFirst() instanceof AnnotationNode, label + " has one invocation anchor");
        AnnotationNode at = (AnnotationNode) sites.getFirst();
        check("INVOKE".equals(value(at, "value")), label + " anchors a native invocation");
        String target = (String) value(at, "target");
        int calls = 0;
        for (MethodNode method : selected) for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call
                    && ("L" + call.owner + ";" + call.name + call.desc).equals(target)) calls++;
        check(calls == 1, label + " must match exactly one native invocation across selected bodies, got " + calls);
    }

    private static AnnotationNode annotation(List<AnnotationNode> visible, List<AnnotationNode> invisible, String descriptor) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (visible != null) annotations.addAll(visible);
        if (invisible != null) annotations.addAll(invisible);
        List<AnnotationNode> matches = annotations.stream().filter(annotation -> annotation.desc.equals(descriptor)).toList();
        check(matches.size() == 1, "Expected one compiled " + descriptor);
        return matches.getFirst();
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2)
            if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        throw new AssertionError("Missing compiled annotation property " + key);
    }

    private static ClassNode read(InputStream input) throws Exception {
        ClassNode result = new ClassNode();
        try (input) { new ClassReader(input).accept(result, ClassReader.SKIP_FRAMES); }
        return result;
    }

    private static void check(boolean pass, String message) {
        checks++;
        if (!pass) throw new AssertionError(message);
    }
}
