package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.movement.TraversalService;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
//? if >=26.1.2
/*import net.minecraft.client.renderer.MultiBufferSource;*/
//? if >=26.1.2
/*import net.minecraft.client.renderer.texture.TextureAtlasSprite;*/
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
abstract class TraversalScreenEffectsMixin {
    @Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
    private static void essenceAscendance$clearWaterOverlay(Minecraft minecraft, PoseStack pose,
            //? if >=26.1.2
            /*MultiBufferSource buffer,*/
            CallbackInfo ci) {
        if (TraversalService.clearWaterVision(minecraft.player)) ci.cancel();
    }
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void essenceAscendance$clearImmersionFire(
            //? if <26.1.2
            Minecraft minecraft,
            PoseStack pose,
            //? if >=26.1.2
            /*MultiBufferSource buffer, TextureAtlasSprite sprite,*/
            CallbackInfo ci) {
        //? if >=26.1.2
        /*if (TraversalService.hideImmersionFire(Minecraft.getInstance().player)) ci.cancel();*/
        //? if <26.1.2
        if (TraversalService.hideImmersionFire(minecraft.player)) ci.cancel();
    }
}
