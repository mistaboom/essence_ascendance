package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.skill.effect.VitalitySustenanceEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Awake sustained owners leave the denominator. Voluntary sleepers still count and retain native beds/spawn points. */
@Mixin(SleepStatus.class)
public abstract class SustenanceSleepMixin {
    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;isSpectator()Z"))
    private boolean essenceAscendance$optionalSleeper(ServerPlayer player, Operation<Boolean> original) {
        return original.call(player) || !player.isSleeping() && VitalitySustenanceEffects.fullySustained(player);
    }
}
