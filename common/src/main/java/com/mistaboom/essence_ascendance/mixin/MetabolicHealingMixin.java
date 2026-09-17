package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Uses the accepted native health write, not a pre-cancellation requested healing value. */
@Mixin(LivingEntity.class)
public abstract class MetabolicHealingMixin {
    @WrapOperation(method = "heal", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"),
            require = 1, expect = 1, allow = 1)
    private void essenceAscendance$acceptedHealing(LivingEntity target, float proposed, Operation<Void> original) {
        if (!(target instanceof ServerPlayer player)) { original.call(target, proposed); return; }
        double before = player.getHealth();
        original.call(target, proposed);
        com.mistaboom.essence_ascendance.vitality.HealingRecoveryService.acceptedHeal(player, before, proposed);
    }
}
