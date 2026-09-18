package com.mistaboom.essence_ascendance.movement;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.jar.JarFile;

/** Opt-in verification of real mapped native call sites on BOTH loaders, not a substitute for playtesting. */
public final class TraversalNativeHookShapeTest {
    private static int checks;
    private static final String ENTITY = "net/minecraft/world/entity/Entity";
    private static final String LIVING = "net/minecraft/world/entity/LivingEntity";
    private static final String PLAYER = "net/minecraft/world/entity/player/Player";
    private static final String LOCAL = "net/minecraft/client/player/LocalPlayer";
    private static final String FLUID = "net/minecraft/world/level/material/FluidState";
    private static final String VEC = "(Lnet/minecraft/world/phys/Vec3;)V";
    private interface Source { InputStream read(String name) throws Exception; }

    public static void main(String[] args) throws Exception {
        try (var input = TraversalNativeHookShapeTest.class.getResourceAsStream("/essence_ascendance.mixins.json")) {
            check(input != null, "The actual common Mixin configuration must be present");
            String config = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            check(!config.contains("\"TraversalPlayerMixin\""),
                    "The obsolete Player travel injector must remain unregistered, not made optional");
        }
        inspect(name -> TraversalNativeHookShapeTest.class.getClassLoader().getResourceAsStream(name + ".class"), false);
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve(".gradle/loom-cache/minecraftMaven/net/minecraft"))) root = root.getParent();
        if (root == null) throw new AssertionError("Build the mapped NeoForge artifact before loader parity verification");
        List<Path> jars;
        try (var paths = Files.walk(root.resolve(".gradle/loom-cache/minecraftMaven/net/minecraft"))) {
            jars = paths.filter(path -> path.getFileName().toString().startsWith("neoforge-")
                    && path.toString().endsWith(".jar") && !path.toString().endsWith("-sources.jar")).toList();
        }
        check(!jars.isEmpty(), "Mapped NeoForge artifact must exist; missing dependencies cannot count as a pass");
        for (Path path : jars) try (JarFile jar = new JarFile(path.toFile())) {
            inspect(name -> {
                var entry = jar.getJarEntry(name + ".class");
                return entry == null ? null : jar.getInputStream(entry);
            }, true);
        }
        System.out.println("TraversalNativeHookShapeTest: " + checks + " checks passed against common and " + jars.size() + " NeoForge artifacts");
    }
    private static void inspect(Source source, boolean neo) throws Exception {
        String loader = neo ? "NeoForge" : "Fabric/common";
        ClassNode entity = read(source, ENTITY), living = read(source, LIVING), player = read(source, PLAYER), local = read(source, LOCAL);
        method(entity, "getBlockSpeedFactor", "()F"); method(entity, "getBlockJumpFactor", "()F");
        method(entity, "makeStuckInBlock", "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)V");
        method(living, "canFreeze", "()Z"); // The LivingEntity override, not merely the Entity base.
        method(living, "canStandOnFluid", "(Lnet/minecraft/world/level/material/FluidState;)Z");
        method(living, "hurt", "(Lnet/minecraft/world/damagesource/DamageSource;F)Z");
        check(count(method(entity, "updateSwimming", "()V"), invoke(ENTITY, "setSwimming", "(Z)V")) == 2,
                loader + " two native pose commits survive NeoForge's rewritten swimming conditions");
        check(count(method(entity, "isVisuallyCrawling", "()Z"), invoke(ENTITY, "isInWater", "()Z")) == 1,
                loader + " lava swimming is not classified as dry crawling");
        MethodNode travel = method(living, "travel", VEC);
        check(count(travel, invoke(LIVING, "isInWater", "()Z")) == (neo ? 2 : 1), loader + " all native/FluidType water branches participate");
        // Movement-efficiency internals are deliberately not hooked: the native Swim Speed
        // calculation, including off-ground scaling, belongs to bonuses/equipment, not these skills.
        check(count(method(entity, "lavaHurt", "()V"), invoke(ENTITY, "igniteForSeconds", "(F)V")) == 1,
                loader + " exactly one source-scoped lava ignition call");
        method(local, "tick", "()V");
        method(read(source, "net/minecraft/client/renderer/block/LiquidBlockRenderer"), "tesselate",
                "(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V");
        String blockState = "Lnet/minecraft/world/level/block/state/BlockState;";
        MethodNode mine = method(player, neo ? "getDigSpeed" : "getDestroySpeed",
                "(" + blockState + (neo ? "Lnet/minecraft/core/BlockPos;" : "") + ")F");
        check(count(mine, invoke(PLAYER, "onGround", "()Z")) == 1, loader + " correct native mining implementation, not NeoForge's delegating wrapper");
        // Regression: Player.travel has no FluidState.is(TagKey) call to wrap.
        // Its native steering already accepts lava through the nonempty-fluid test;
        // the shared pose and LivingEntity travel hooks provide the actual ability.
        MethodNode playerTravel = method(player, "travel", VEC);
        check(count(playerTravel, invoke(PLAYER, "isSwimming", "()Z")) == 1,
                loader + " native pitch steering consumes the shared swimming flag");
        check(count(playerTravel, invoke(FLUID, "isEmpty", "()Z")) == 1,
                loader + " native pitch steering already accepts any nonempty fluid");
        check(count(playerTravel, invoke(FLUID, "is", "(Lnet/minecraft/tags/TagKey;)Z")) == 0,
                loader + " no nonexistent fluid-tag instruction may be used as a steering hook");
        MethodNode input = method(local, "aiStep", "()V");
        check(count(input, invoke(LOCAL, "isInWater", "()Z")) >= 1, loader + " swimming input fluid conditions exist");
        check(count(input, invoke(LOCAL, "isUnderWater", "()Z")) >= 1,
                loader + " input target contains a direct underwater query");
        check(count(method(local, "hasEnoughImpulseToStartSprinting", "()Z"), invoke(LOCAL, "isUnderWater", "()Z")) == 1,
                loader + " sprint-intent target contains its direct underwater query");
        check(count(method(local, "canStartSprinting", "()Z"), invoke(LOCAL, "hasEnoughImpulseToStartSprinting", "()Z")) == 1,
                loader + " sprint eligibility delegates to the hooked impulse helper");
        var state = read(source, "net/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase");
        method(state, "getCollisionShape", "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;");
        method(state, "entityInside", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V");
        MethodNode contextEntity = method(read(source, "net/minecraft/world/phys/shapes/EntityCollisionContext"), "getEntity", "()Lnet/minecraft/world/entity/Entity;");
        check((contextEntity.access & Opcodes.ACC_PUBLIC) != 0, loader + " context entity is public, no access-widening guess");
        var fog = read(source, "net/minecraft/client/renderer/FogRenderer");
        for (String name : List.of("setupFog", "setupColor")) {
            var target = fog.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
            check(count(target, invoke("net/minecraft/client/Camera", "getFluidInCamera", "()Lnet/minecraft/world/level/material/FogType;")) >= 1,
                    loader + " scoped camera-fluid query in " + name);
        }
        var screen = read(source, "net/minecraft/client/renderer/ScreenEffectRenderer");
        for (String name : List.of("renderWater", "renderFire"))
            method(screen, name, "(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/vertex/PoseStack;)V");
    }
    private static ClassNode read(Source source, String name) throws Exception {
        try (var input = source.read(name)) {
            if (input == null) throw new AssertionError("Missing actual mapped class " + name);
            var result = new ClassNode(); new ClassReader(input).accept(result, 0); return result;
        }
    }
    private static MethodNode method(ClassNode type, String name, String descriptor) {
        checks++;
        return type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing native method " + type.name + "." + name + descriptor));
    }
    private static Predicate<AbstractInsnNode> invoke(String owner, String name, String descriptor) {
        return instruction -> instruction instanceof MethodInsnNode call && call.owner.equals(owner)
                && call.name.equals(name) && call.desc.equals(descriptor);
    }
    private static long count(MethodNode method, Predicate<AbstractInsnNode> test) {
        long result = 0; for (var instruction : method.instructions) if (test.test(instruction)) result++; return result;
    }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
}
