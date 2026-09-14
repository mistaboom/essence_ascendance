package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.skill.effect.GuardCounterattackService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

/** Exact native explosion hit/impulse pair; ordinary velocity writes and explosion damage remain native. */
@Mixin(Explosion.class)
public abstract class ExplosionGuardKnockbackMixin {
    @Shadow @Final private DamageSource damageSource;

    @WrapOperation(method = "explode", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), require = 1, expect = 1, allow = 1)
    private boolean essenceAscendance$explosionGuardHit(Entity entity, DamageSource source, float amount,
                                                       Operation<Boolean> original) {
        return EquipmentDamageService.withExplosionHit(entity, source, () -> original.call(entity, source, amount));
    }

    // Both native loaders expose these normalized direction locals before resistance modifies them.
    // Share is invocation-local, so nested explosions cannot overwrite another explosion's capture.
    @WrapOperation(method = "explode", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/ExplosionDamageCalculator;getKnockbackMultiplier(Lnet/minecraft/world/entity/Entity;)F"), require = 1, expect = 1, allow = 1)
    private float essenceAscendance$explosionGuardDirection(ExplosionDamageCalculator calculator, Entity entity,
                                                            Operation<Float> original,
                                                            @Local(index = 16) double x,
                                                            @Local(index = 18) double y,
                                                            @Local(index = 20) double z,
                                                            @Share("guardExplosionDirection") LocalRef<Vec3> direction) {
        direction.set(new Vec3(x, y, z));
        return original.call(calculator, entity);
    }

    // aa (slot 24) is exposure * distance falloff * calculator multiplier BEFORE native resistance.
    // The proposed vector here includes native resistance and NeoForge's explosion-knockback event.
    @WrapOperation(method = "explode", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"), require = 1, expect = 1, allow = 1)
    private void essenceAscendance$explosionGuardImpulse(Entity entity, Vec3 proposed, Operation<Void> original,
                                                        @Local(index = 24) double rawMagnitude,
                                                        @Share("guardExplosionDirection") LocalRef<Vec3> direction,
                                                        @Share("guardExplosionProtected") LocalBooleanRef protectedImpulse) {
        protectedImpulse.set(EquipmentDamageService.suppressExplosionDisplacement(entity, damageSource));
        Vec3 raw = direction.get() == null ? Vec3.ZERO : direction.get().scale(rawMagnitude);
        EquipmentDamageService.explosionKnockback(entity, damageSource, raw, proposed,
                () -> original.call(entity, proposed));
    }

    @WrapOperation(method = "explode", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", remap = false), require = 1, expect = 1, allow = 1)
    private Object essenceAscendance$explosionGuardPacket(Map<Object, Object> players, Object player, Object impulse,
                                                        Operation<Object> original,
                                                        @Share("guardExplosionProtected") LocalBooleanRef protectedImpulse) {
        // ServerLevel later builds each player's explosion packet from this map. Preserve presentation,
        // while sending the same zero impulse used by the resolving server-authoritative Riposte.
        return original.call(players, player, protectedImpulse.get() ? Vec3.ZERO : impulse);
    }
}
