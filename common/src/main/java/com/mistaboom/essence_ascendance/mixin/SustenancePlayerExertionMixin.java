package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.skill.effect.VitalitySustenanceEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native exertion only: healing, damage penalties and explicit exhaustion costs remain untouched. */
@Mixin(Player.class)
public abstract class SustenancePlayerExertionMixin {
    @WrapOperation(method = {"jumpFromGround", "attack"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private void essenceAscendance$passiveExhaustion(Player player, float amount, Operation<Void> original) {
        original.call(player, player instanceof ServerPlayer server
                ? VitalitySustenanceEffects.passiveExhaustion(server, amount) : amount);
    }
}
