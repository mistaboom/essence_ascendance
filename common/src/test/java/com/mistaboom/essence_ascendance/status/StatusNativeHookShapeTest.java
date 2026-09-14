package com.mistaboom.essence_ascendance.status;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

/** Exact protected native status entry/merge/instant shapes in both mapped loaders. */
public final class StatusNativeHookShapeTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        inspect(name -> StatusNativeHookShapeTest.class.getClassLoader().getResourceAsStream(name + ".class"), false);
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve(".gradle/loom-cache/minecraftMaven/net/minecraft"))) root = root.getParent();
        if (root == null) throw new AssertionError("Mapped NeoForge cache required");
        List<Path> jars;
        try (var files = Files.walk(root.resolve(".gradle/loom-cache/minecraftMaven/net/minecraft"))) {
            jars = files.filter(p -> p.getFileName().toString().startsWith("neoforge-") && p.toString().endsWith(".jar")
                    && !p.toString().endsWith("-sources.jar")).toList();
        }
        check(!jars.isEmpty(), "both loader artifacts available");
        for (var path : jars) try (var jar = new JarFile(path.toFile())) {
            inspect(name -> jar.getInputStream(jar.getJarEntry(name + ".class")), true);
        }
        System.out.println("Status native hook shapes passed: " + checks + " (both mapped loaders, no world)");
    }
    private interface Source { InputStream read(String name) throws Exception; }
    private static ClassNode read(Source source, String name) throws Exception {
        try (var input = source.read("net/minecraft/" + name)) { var node = new ClassNode(); new ClassReader(input).accept(node, 0); return node; }
    }
    private static void inspect(Source source, boolean neo) throws Exception {
        var living = read(source, "world/entity/LivingEntity");
        var add = method(living, "addEffect", "(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z");
        check(count(add, "canBeAffected") == (neo ? 0 : 1)
                && count(add, "canMobEffectBeApplied") == (neo ? 1 : 0), "single loader-native acceptance precheck");
        if (neo) check(count(add, "post") == 1, "NeoForge posts exactly one native Added event after acceptance");
        check(count(add, "update") == 1, "single merge handles stronger/weaker/hidden refresh");
        check(count(add, "onEffectAdded") == 2 && count(add, "onEffectStarted") == 1, "native callbacks remain in one application");
        var force = method(living, "forceAddEffect", "(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)V");
        check(count(force, "canBeAffected") == (neo ? 0 : 1)
                && count(force, "canMobEffectBeApplied") == (neo ? 1 : 0)
                && count(force, "put") == 1, "forced effect retains loader-native acceptance and replacement");
        var remove = method(living, "removeEffect", "(Lnet/minecraft/core/Holder;)Z");
        check(count(remove, "onEffectRemoved") == (neo ? 2 : 1) && count(remove, "removeEffectNoUpdate") == 1,
                "native removal keeps NeoForge cancellation before the sole map removal and completion callback");
        var instance = read(source, "world/effect/MobEffectInstance");
        var tick = method(instance, "tick", "(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/Runnable;)Z");
        check(count(tick, "applyEffectTick") == 1 && count(tick, "setDetailsFrom") == 1,
                "one derived native effect tick and one hidden promotion, no persisted source guessing");
        var copyConstructor = method(instance, "<init>", "(Lnet/minecraft/world/effect/MobEffectInstance;)V");
        var details = method(instance, "setDetailsFrom", "(Lnet/minecraft/world/effect/MobEffectInstance;)V");
        check(count(copyConstructor, "setDetailsFrom") == 1 && count(details, "clear") == (neo ? 1 : 0)
                && count(details, "addAll") == (neo ? 1 : 0), "native restoration copy preserves NeoForge instance cure sets");
        check(instance.fields.stream().filter(f -> f.name.equals("hiddenEffect") && f.desc.equals("Lnet/minecraft/world/effect/MobEffectInstance;")).count() == 1,
                "one exact hidden-chain accessor field");
        check(count(method(read(source, "world/entity/projectile/ThrownPotion"), "applySplash", null), "applyInstantenousEffect") == 1,
                "one generic splash instant dispatch protects custom overrides");
        check(count(method(read(source, "world/entity/AreaEffectCloud"), "tick", "()V"), "applyInstantenousEffect") == 1,
                "one generic cloud instant dispatch protects custom overrides");
        check(count(method(read(source, "world/entity/projectile/ThrownPotion"), "applySplash", null), "addEffect") == 1,
                "one splash timed source annotation");
        check(count(method(read(source, "world/entity/AreaEffectCloud"), "tick", "()V"), "addEffect") == 1,
                "one cloud timed source annotation");
        check(count(method(read(source, "world/entity/projectile/Arrow"), "doPostHurtEffects", null), "addEffect") == 2,
                "exactly two tipped-arrow branches retain direct identity and secondary state");
        String instant = "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;ID)V";
        check(method(read(source, "world/effect/MobEffect"), "applyInstantenousEffect", instant) != null, "base instant fallback exists");
        check(method(read(source, "world/effect/HealOrHarmMobEffect"), "applyInstantenousEffect", instant) != null, "native overriding instant fallback exists");
    }
    private static MethodNode method(ClassNode type, String name, String desc) {
        var found = type.methods.stream().filter(m -> m.name.equals(name) && (desc == null || m.desc.equals(desc))).toList();
        if (found.size() != 1) throw new AssertionError("Expected one exact method " + type.name + "." + name);
        return found.getFirst();
    }
    private static long count(MethodNode method, String name) {
        long count = 0; for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.name.equals(name)) count++;
        return count;
    }
    private static void check(boolean okay, String message) { checks++; if (!okay) throw new AssertionError(message); }
}
