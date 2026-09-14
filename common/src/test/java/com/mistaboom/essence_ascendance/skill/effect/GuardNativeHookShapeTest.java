package com.mistaboom.essence_ascendance.skill.effect;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.jar.JarFile;

/** Protect exact native bytecode sites in BOTH mapped loaders, including NeoForge's extra sprint-key use test. */
public final class GuardNativeHookShapeTest {
    private static int checks;
    private static final String LOCAL = "net/minecraft/client/player/LocalPlayer";
    public static void main(String[] args) throws Exception {
        inspect(name -> GuardNativeHookShapeTest.class.getClassLoader().getResourceAsStream(name + ".class"), false);
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve(".gradle/loom-cache/minecraftMaven/net/minecraft"))) root = root.getParent();
        if (root == null) throw new AssertionError("Mapped NeoForge build cache is required for loader equivalence verification");
        List<Path> jars;
        try (var stream = Files.walk(root.resolve(".gradle/loom-cache/minecraftMaven/net/minecraft"))) {
            jars = stream.filter(path -> path.getFileName().toString().startsWith("neoforge-")
                    && path.toString().endsWith(".jar") && !path.toString().endsWith("-sources.jar")).toList();
        }
        check(!jars.isEmpty(), "Current NeoForge mapped native artifact exists");
        for (Path path : jars) try (JarFile jar = new JarFile(path.toFile())) {
            inspect(name -> jar.getInputStream(jar.getJarEntry(name + ".class")), true);
        }
        System.out.println("Guard native hook bytecode shapes passed: " + checks + " (Fabric/common and " + jars.size()
                + " NeoForge mapped artifacts; exact injections/native jump/step/server movement; not a live NeoForge world)");
    }
    private interface Source { InputStream read(String name) throws Exception; }
    private static void inspect(Source source, boolean neo) throws Exception {
        String loader = neo ? "NeoForge" : "Fabric";
        ClassNode local = read(source, LOCAL), living = read(source, "net/minecraft/world/entity/LivingEntity"),
                entity = read(source, "net/minecraft/world/entity/Entity"), connection = read(source, "net/minecraft/server/network/ServerGamePacketListenerImpl");
        MethodNode ai = method(local, "aiStep", "()V"), sprint = method(local, "canStartSprinting", "()Z"),
                intent = method(local, "hasEnoughImpulseToStartSprinting", "()Z");
        check(count(ai, invoke("onInput")) == 1, loader + " unique slowdown slice begins after Tutorial.onInput");
        boolean after = false; int slowdown = 0;
        for (AbstractInsnNode instruction : ai.instructions) {
            if (invoke("onInput").test(instruction)) after = true;
            if (after && instruction instanceof LdcInsnNode ldc && Float.valueOf(.2F).equals(ldc.cst)) slowdown++;
        }
        check(slowdown == 2, loader + " exact two 0.2 item-use multipliers after input processing");
        check(count(ai, invoke("isUsingItem")) == (neo ? 2 : 1), loader + " explicit loader sprint-key use-test shape");
        check(count(sprint, invoke("isUsingItem")) == 1, loader + " unique guard sprint permission hook");
        check(count(sprint, invoke("hasEnoughImpulseToStartSprinting")) == 1, loader + " native movement intent still required");
        check(count(sprint, invoke("hasEnoughFoodToStartSprinting")) == 1, loader + " native hunger still required");
        check(count(sprint, invoke("isFallFlying")) == 1 && count(sprint, invoke("isPassenger")) == 1,
                loader + " native riding/fall-flight eligibility retained");
        check(count(intent, instruction -> instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                && field.owner.equals("net/minecraft/client/player/Input") && field.name.equals("forwardImpulse")) == 1,
                loader + " exact one sprint-intent input read");
        check(count(method(living, "maxUpStep", "()F"), instruction -> instruction.getOpcode() == Opcodes.FRETURN) == 1,
                loader + " unique active-only step-height return hook");
        check(count(method(entity, "collide", "(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"), invoke("maxUpStep")) == 3,
                loader + " native terrain algorithm queries step-height permission at all three stages");
        MethodNode jump = method(living, "jumpFromGround", "()V");
        check(count(jump, invoke("isSprinting")) == 1 && count(jump, invoke("addDeltaMovement")) == 1,
                loader + " native guarded jump retains ordinary sprint impulse");
        check(count(jump, invoke("stopUsingItem")) == 0 && count(jump, invoke("isUsingItem")) == 0,
                loader + " native jump has no shield-cancel or item-use prohibition");
        MethodNode movePacket = method(connection, "handleMovePlayer", "(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V");
        check(count(movePacket, instruction -> instruction instanceof FieldInsnNode field && field.owner.equals("net/minecraft/world/entity/MoverType")
                && field.name.equals("PLAYER")) == 1, loader + " actual server movement packet uses one intentional PLAYER movement boundary");
        check(count(movePacket, instruction -> instruction instanceof MethodInsnNode call && call.name.equals("move")
                && call.desc.equals("(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V")) == 1,
                loader + " server packet performs one native terrain-resolved translation");
        check(method(entity, "move", "(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V") != null,
                loader + " shared contact wrapper target exists");
        MethodNode explosion = method(read(source, "net/minecraft/world/level/Explosion"), "explode", "()V");
        check(count(explosion, instruction -> instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/world/entity/Entity")
                && call.name.equals("hurt") && call.desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;F)Z")) == 1,
                loader + " one native explosion damage call establishes the retaliation/protection source");
        check(count(explosion, instruction -> instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/world/entity/Entity")
                && call.name.equals("setDeltaMovement") && call.desc.equals("(Lnet/minecraft/world/phys/Vec3;)V")) == 1,
                loader + " exact one native explosion displacement hook");
        check(count(explosion, instruction -> instruction instanceof MethodInsnNode call && call.owner.equals("java/util/Map") && call.name.equals("put")) == 1,
                loader + " exact one explosion hitPlayers packet impulse hook");
        check(count(explosion, instruction -> instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/world/level/ExplosionDamageCalculator")
                && call.name.equals("getKnockbackMultiplier") && call.desc.equals("(Lnet/minecraft/world/entity/Entity;)F")) == 1,
                loader + " exact one pre-resistance knockback multiplier hook");
        for (int slot : new int[]{16, 18, 20}) check(liveDouble(explosion, "getKnockbackMultiplier", slot),
                loader + " native normalized direction local slot " + slot + " remains a live double");
        check(liveDouble(explosion, "setDeltaMovement", 24), loader + " raw pre-resistance impulse magnitude local remains slot 24");
    }
    private static ClassNode read(Source source, String name) throws Exception {
        try (InputStream input = source.read(name)) {
            if (input == null) throw new AssertionError("Missing native class " + name);
            var result = new ClassNode(); new ClassReader(input).accept(result, 0); return result;
        }
    }
    private static MethodNode method(ClassNode type, String name, String descriptor) {
        return type.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst().orElseThrow();
    }
    private static Predicate<AbstractInsnNode> invoke(String name) { return instruction -> instruction instanceof MethodInsnNode method && method.name.equals(name); }
    private static boolean liveDouble(MethodNode method, String call, int slot) {
        for (AbstractInsnNode instruction : method.instructions) if (invoke(call).test(instruction)) {
            int position = method.instructions.indexOf(instruction);
            return method.localVariables.stream().anyMatch(local -> local.index == slot && local.desc.equals("D")
                    && method.instructions.indexOf(local.start) <= position && method.instructions.indexOf(local.end) > position);
        }
        return false;
    }
    private static long count(MethodNode method, Predicate<AbstractInsnNode> predicate) {
        long count = 0; for (AbstractInsnNode instruction : method.instructions) if (predicate.test(instruction)) count++; return count;
    }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
}
