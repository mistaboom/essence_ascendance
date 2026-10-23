package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.UtilitySenseRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class UtilitySenseRendererMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void essenceAscendance$utilitySense(DeltaTracker deltaTracker, boolean renderBlockOutline,
                                                 Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                                                 Matrix4f positionMatrix, Matrix4f projectionMatrix,
                                                 CallbackInfo ci) {
        UtilitySenseRenderer.render(camera, positionMatrix);
    }
}
