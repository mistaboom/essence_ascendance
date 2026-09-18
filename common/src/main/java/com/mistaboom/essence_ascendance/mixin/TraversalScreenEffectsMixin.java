package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.movement.TraversalService;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
abstract class TraversalScreenEffectsMixin {
    @Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
    private static void essenceAscendance$clearWaterOverlay(Minecraft minecraft, PoseStack pose, CallbackInfo ci) {
        if (TraversalService.clearWaterVision(minecraft.player)) ci.cancel();
    }
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void essenceAscendance$clearImmersionFire(Minecraft minecraft, PoseStack pose, CallbackInfo ci) {
        if (TraversalService.hideImmersionFire(minecraft.player)) ci.cancel();
    }
}
