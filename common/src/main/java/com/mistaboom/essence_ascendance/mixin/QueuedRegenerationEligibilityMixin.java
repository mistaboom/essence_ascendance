package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mistaboom.essence_ascendance.vitality.HealingRecoveryService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Permit the native regeneration effect to heal debt at full HP; cadence, amount and events stay native. */
@Mixin(targets = "net.minecraft.world.effect.RegenerationMobEffect")
public abstract class QueuedRegenerationEligibilityMixin {
    @ModifyExpressionValue(method = "applyEffectTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getHealth()F"), require = 1, expect = 1, allow = 1)
    private float essenceAscendance$healingEligibility(float health, @Local(argsOnly = true) LivingEntity entity) {
        // Only this method's missing-health comparison sees the next lower representable float.
        // The entity's actual health, maximum health, and resulting heal amount are never changed here.
        return entity instanceof ServerPlayer player && health >= player.getMaxHealth()
                && HealingRecoveryService.hasRecoverableDebt(player) ? Math.nextDown(health) : health;
    }
}
