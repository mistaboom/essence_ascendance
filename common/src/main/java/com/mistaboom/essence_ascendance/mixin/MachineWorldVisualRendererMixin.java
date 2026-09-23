package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.MachineWorldVisualRenderer;
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
public abstract class MachineWorldVisualRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void essenceAscendance$beginMachineVisualFrame(
            DeltaTracker deltaTracker, boolean renderBlockOutline,
            Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
            Matrix4f positionMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        MachineWorldVisualRenderer.beginFrame();
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void essenceAscendance$renderMachineVisuals(
            DeltaTracker deltaTracker, boolean renderBlockOutline,
            Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
            Matrix4f positionMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        MachineWorldVisualRenderer.render(camera, positionMatrix);
        com.mistaboom.essence_ascendance.client.transientfx.TransientWorldVisuals.render(
                camera, positionMatrix, deltaTracker.getGameTimeDeltaPartialTick(true));
    }
}
