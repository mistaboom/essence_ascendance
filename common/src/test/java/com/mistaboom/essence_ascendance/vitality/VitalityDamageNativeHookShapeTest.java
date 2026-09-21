package com.mistaboom.essence_ascendance.vitality;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Opt-in fail-fast checks of actual mapped hook targets and live float ordinals on BOTH loaders. */
public final class VitalityDamageNativeHookShapeTest {
    private static int checks;
    private static final String PLAYER = "net/minecraft/world/entity/player/Player";
    private static final String LIVING = "net/minecraft/world/entity/LivingEntity";
    private static final String FOOD = "net/minecraft/world/food/FoodData";
    private static final String REGEN = "net/minecraft/world/effect/RegenerationMobEffect";
    private static final String LOCAL = "net/minecraft/client/player/LocalPlayer";
    private static final String GUI = "net/minecraft/client/gui/Gui";
    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve(".gradle/loom-cache/minecraftMaven/net/minecraft"))) root = root.getParent();
        if (root == null) throw new AssertionError("Mapped NeoForge cache required; compile :neoforge:compileJava first");
        inspect(VitalityDamageNativeHookShapeTest.class.getResourceAsStream("/" + PLAYER + ".class"),
                declaredFloatOrdinal(root, "fabric"), "Fabric");
        inspectFeedback(VitalityDamageNativeHookShapeTest.class.getResourceAsStream("/" + LIVING + ".class"),
                VitalityDamageNativeHookShapeTest.class.getResourceAsStream("/" + LOCAL + ".class"), "Fabric");
        inspectHealing(VitalityDamageNativeHookShapeTest.class.getResourceAsStream("/" + LIVING + ".class"),
                VitalityDamageNativeHookShapeTest.class.getResourceAsStream("/" + FOOD + ".class"),
                VitalityDamageNativeHookShapeTest.class.getResourceAsStream("/" + REGEN + ".class"), "Fabric");
        inspectHud(VitalityDamageNativeHookShapeTest.class.getResourceAsStream("/" + GUI + ".class"), "Fabric");
        int neoOrdinal = declaredFloatOrdinal(root, "neoforge");
        int found = 0;
        try (var paths = Files.walk(root.resolve(".gradle/loom-cache/minecraftMaven/net/minecraft"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().startsWith("neoforge-")
                    && p.toString().endsWith(".jar") && !p.toString().endsWith("-sources.jar")).toList()) {
                try (var jar = new JarFile(path.toFile())) {
                    var entry = jar.getJarEntry(PLAYER + ".class");
                    if (entry == null) continue;
                    String loader = "NeoForge " + path.getFileName();
                    inspect(jar.getInputStream(entry), neoOrdinal, loader);
                    var living = jar.getJarEntry(LIVING + ".class");
                    var local = jar.getJarEntry(LOCAL + ".class");
                    check(living != null && local != null, loader + " merged client/server classes are required");
                    inspectFeedback(jar.getInputStream(living), jar.getInputStream(local), loader);
                    var food = jar.getJarEntry(FOOD + ".class");
                    var regen = jar.getJarEntry(REGEN + ".class");
                    check(food != null && regen != null, loader + " native regeneration classes exist");
                    inspectHealing(jar.getInputStream(living), jar.getInputStream(food), jar.getInputStream(regen), loader);
                    var gui = jar.getJarEntry(GUI + ".class");
                    check(gui != null, loader + " native health HUD class exists");
                    inspectHud(jar.getInputStream(gui), loader);
                    found++;
                }
            }
        }
        check(found > 0, "NeoForge was actually inspected, not silently skipped");
        System.out.println("VitalityDamageNativeHookShapeTest: " + checks + " checks passed (both mapped loaders)");
    }
    /** Inspect the actual compiled annotation so a changed binding cannot silently diverge from this test. */
    private static int declaredFloatOrdinal(Path root, String loader) throws Exception {
        Path mixin = root.resolve(loader + "/build/classes/java/main/com/mistaboom/essence_ascendance/"
                + loader + "/mixin/VitalityPlayerDamageMixin.class");
        check(Files.isRegularFile(mixin), loader + " compiled routing mixin must exist");
        var type = read(Files.newInputStream(mixin));
        var route = type.methods.stream().filter(m -> m.name.equals("essenceAscendance$routeHealth")).findFirst().orElseThrow();
        var annotations = new ArrayList<AnnotationNode>();
        if (route.visibleParameterAnnotations != null) for (var parameter : route.visibleParameterAnnotations)
            if (parameter != null) annotations.addAll(parameter);
        if (route.invisibleParameterAnnotations != null) for (var parameter : route.invisibleParameterAnnotations)
            if (parameter != null) annotations.addAll(parameter);
        var locals = annotations.stream().filter(a -> a.desc.equals("Lcom/llamalad7/mixinextras/sugar/Local;")).toList();
        check(locals.size() == 1, loader + " one explicit health local binding is required");
        var values = locals.getFirst().values;
        for (int i = 0; i < values.size(); i += 2) if (values.get(i).equals("ordinal")) return (Integer) values.get(i + 1);
        throw new AssertionError(loader + " health local requires an explicit ordinal");
    }
    private static void inspect(InputStream input, int expectedFloatOrdinal, String loader) throws Exception {
        check(input != null, loader + " mapped Player class available");
        var type = new ClassNode(); try (input) { new ClassReader(input).accept(type, 0); }
        var method = type.methods.stream().filter(m -> m.name.equals("actuallyHurt")
                && m.desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;F)V")).findFirst().orElseThrow();
        int records = 0, reads = 0, writes = 0, healthWrites = 0;
        int recordIndex = -1, healthIndex = -1;
        for (AbstractInsnNode node : method.instructions) if (node instanceof MethodInsnNode call) {
            if (call.owner.equals("net/minecraft/world/damagesource/CombatTracker") && call.name.equals("recordDamage")) {
                records++;
                recordIndex = method.instructions.indexOf(call);
                AbstractInsnNode previous = call.getPrevious();
                while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
                check(previous instanceof VarInsnNode && previous.getOpcode() == Opcodes.FLOAD,
                        loader + " final recordDamage amount comes from a mutable float local");
                int index = method.instructions.indexOf(call);
                var locals = method.localVariables.stream().filter(v -> v.desc.equals("F")
                        && method.instructions.indexOf(v.start) <= index && method.instructions.indexOf(v.end) > index)
                        .sorted(Comparator.comparingInt(v -> v.index)).toList();
                check(locals.size() > expectedFloatOrdinal, loader + " expected live float ordinal exists");
                check(locals.get(expectedFloatOrdinal).index == ((VarInsnNode)previous).var,
                        loader + " @Local ordinal targets final post-absorption health, not raw input; ordinal="
                                + expectedFloatOrdinal + ", loaded slot=" + ((VarInsnNode)previous).var
                                + ", live floats=" + locals.stream().map(v -> v.name + "@" + v.index).toList());
            }
            if (call.name.equals("setHealth") && call.desc.equals("(F)V")) {
                check(call.owner.equals(PLAYER), loader + " common capacity-commit hook bytecode owner remains Player");
                healthWrites++;
                healthIndex = method.instructions.indexOf(call);
            }
            if (call.name.equals("getAbsorptionAmount") || call.name.equals("setAbsorptionAmount")) {
                check(call.owner.equals(PLAYER), loader + " scoped absorption hook bytecode owner remains Player");
                if (call.name.equals("getAbsorptionAmount")) reads++; else writes++;
            }
        }
        check(records == 1 && reads > 0 && writes > 0, loader + " exact required routing/absorption hooks exist");
        check(healthWrites == 1 && healthIndex > recordIndex,
                loader + " exactly one native health write follows routing, before maximum-HP cost commit");
    }
    private static void inspectFeedback(InputStream livingInput, InputStream localInput, String loader) throws Exception {
        check(livingInput != null && localInput != null, loader + " feedback hook classes available");
        var living = read(livingInput); var local = read(localInput);
        method(living, "handleDamageEvent", "(Lnet/minecraft/world/damagesource/DamageSource;)V");
        method(living, "playHurtSound", "(Lnet/minecraft/world/damagesource/DamageSource;)V");
        var hurt = method(living, "hurt", "(Lnet/minecraft/world/damagesource/DamageSource;F)Z");
        int direction = 0, knockback = 0;
        for (AbstractInsnNode node : hurt.instructions) if (node instanceof MethodInsnNode call) {
            if (call.owner.equals(LIVING) && call.name.equals("indicateDamage") && call.desc.equals("(DD)V")) direction++;
            if (call.owner.equals(LIVING) && call.name.equals("knockback") && call.desc.equals("(DDD)V")) knockback++;
        }
        check(direction > 0 && knockback > 0, loader + " exact virtual direction/knockback call sites exist");
        var update = method(local, "hurtTo", "(F)V");
        boolean hurtTimer = false;
        for (AbstractInsnNode node : update.instructions) if (node instanceof FieldInsnNode field
                && field.getOpcode() == Opcodes.PUTFIELD && field.name.equals("hurtTime")) hurtTimer = true;
        check(hurtTimer, loader + " health synchronization independently starts a hurt animation");
    }
    private static void inspectHealing(InputStream livingInput, InputStream foodInput, InputStream regenInput, String loader) throws Exception {
        check(livingInput != null && foodInput != null && regenInput != null, loader + " healing targets available");
        var living = read(livingInput); var food = read(foodInput); var regen = read(regenInput);
        var heal = method(living, "heal", "(F)V");
        check(calls(heal, LIVING, "setHealth", "(F)V") == 1, loader + " accepted native healing has exactly one health write");
        var tick = method(food, "tick", "(L" + PLAYER + ";)V");
        check(calls(tick, PLAYER, "isHurt", "()Z") == 2, loader + " both natural regeneration missing-health gates are intercepted");
        var effect = method(regen, "applyEffectTick", "(L" + LIVING + ";I)Z");
        check(calls(effect, LIVING, "getHealth", "()F") == 1, loader + " exact regeneration-effect eligibility expression");
        check(calls(effect, LIVING, "heal", "(F)V") == 1, loader + " effect retains the native heal call");
    }
    private static void inspectHud(InputStream input, String loader) throws Exception {
        check(input != null, loader + " native Gui class available");
        var gui = read(input);
        method(gui, "render", "(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V");
        check(gui.fields.stream().anyMatch(f -> f.name.equals("minecraft") && f.desc.equals("Lnet/minecraft/client/Minecraft;")),
                loader + " native camera context is available to the shared health-capacity renderer hook");
        for (String name : List.of("lastHealth", "displayHealth")) {
            check(gui.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals("I")),
                    loader + " native damage-flash HP history exists: " + name);
        }
        // NeoForge splits renderPlayerHealth into independent GUI layers. The shared outer render
        // hook must remain available; no assumption that renderPlayerHealth is still invoked.
    }
    private static int calls(MethodNode method, String owner, String name, String descriptor) {
        int count = 0;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor)) count++;
        return count;
    }
    private static ClassNode read(InputStream input) throws Exception {
        var node = new ClassNode();
        try (input) { new ClassReader(input).accept(node, 0); }
        return node;
    }
    private static MethodNode method(ClassNode type, String name, String descriptor) {
        var method = type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).findFirst();
        check(method.isPresent(), type.name + " exact hook target " + name + descriptor);
        return method.orElseThrow();
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
