package com.mistaboom.essence_ascendance.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Executes the transformed native explosion impulse and its client-packet map on memory-only geometry. */
public final class NativeGuardExplosionTest {
    private static int assertions;
    private NativeGuardExplosionTest() { }

    static void verify(ProjectileNativeInterceptionTest.Fixture fixture, Entity responsible,
                       DamageSource source, boolean protectedStrike) {
        if (!Boolean.getBoolean("essence.projectile.nativeHookTest")) throw new IllegalStateException("Explicit native fixture required");
        var player = fixture.player;
        Vec3 before = new Vec3(.1, -.05, .02);
        player.setDeltaMovement(before);
        float health = player.getHealth();
        ExplosionDamageCalculator calculator = new ExplosionDamageCalculator() {
            @Override public boolean shouldDamageEntity(Explosion explosion, Entity target) { return false; }
            @Override public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter level,
                    BlockPos pos, BlockState block, FluidState fluid) { return Optional.of(100.0F); }
        };
        Explosion explosion = new Explosion(fixture.level, responsible, source, calculator,
                0, 0, -1, 2, false, Explosion.BlockInteraction.KEEP,
                ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, SoundEvents.GENERIC_EXPLODE);
        // explode() is actual native force calculation. finalizeExplosion is intentionally not called;
        // the fixture never constructs/opens a world or alters blocks.
        explosion.explode();
        Vec3 packet = explosion.getHitPlayers().get(player);
        check(packet != null, "Native explosion registers the player's packet impulse");
        if (protectedStrike) {
            check(player.getDeltaMovement().distanceToSqr(before) < 1e-12, "Resolving Riposte suppresses native explosion setDeltaMovement");
            check(packet.lengthSqr() == 0, "Protected explosion packet cannot reintroduce client knockback");
        } else {
            check(player.getDeltaMovement().distanceToSqr(before) > 0, "Explosion impulse returns immediately outside attack resolution");
            check(packet.distanceToSqr(player.getDeltaMovement().subtract(before)) < 1e-12,
                    "Unprotected native explosion and packet retain identical velocity delta");
        }
        check(player.getHealth() == health, "Knockback hooks do not manufacture explosion damage");
        Vec3 direct = new Vec3(.4, .2, -.1);
        player.setDeltaMovement(direct);
        check(player.getDeltaMovement().equals(direct), "Ordinary direct velocity writes remain outside the narrow explosion hook");
        player.setDeltaMovement(Vec3.ZERO);
        System.out.println("Native explosion guard checks passed: " + assertions + " (actual Explosion.explode and packet impulse; no world)");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
