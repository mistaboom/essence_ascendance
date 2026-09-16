package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.skill.effect.VitalitySustenanceEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Block.class)
public abstract class SustenanceMiningExertionMixin {
    @WrapOperation(method = "playerDestroy", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private void essenceAscendance$passiveExhaustion(Player player, float amount, Operation<Void> original) {
        original.call(player, player instanceof ServerPlayer server
                ? VitalitySustenanceEffects.passiveExhaustion(server, amount) : amount);
    }
}
