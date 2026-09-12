package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.item.AscendanceCasterItem;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Reuses vanilla sprites, placement, target checks and the player's attack-indicator option. */
@Mixin(Gui.class)
abstract class CasterAttackIndicatorMixin {
    @WrapOperation(method = {"renderCrosshair", "renderItemHotbar"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getAttackStrengthScale(F)F"))
    private float essenceAscendance$castReadiness(LocalPlayer player, float partialTick, Operation<Float> original) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) stack = player.getOffhandItem();
        return stack.getItem() instanceof AscendanceCasterItem
                ? 1.0F - player.getCooldowns().getCooldownPercent(stack.getItem(), partialTick)
                : original.call(player, partialTick);
    }
}
