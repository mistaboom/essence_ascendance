package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.skill.effect.VitalitySustenanceEffects;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public abstract class SustenanceMovementExertionMixin {
    @WrapOperation(method = "checkMovementStatistics", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V"))
    private void essenceAscendance$passiveExhaustion(ServerPlayer player, float amount, Operation<Void> original) {
        original.call(player, VitalitySustenanceEffects.passiveExhaustion(player, amount));
    }
}
