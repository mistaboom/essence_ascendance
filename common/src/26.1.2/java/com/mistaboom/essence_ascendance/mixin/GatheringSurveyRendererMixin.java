package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.GatheringSurveyRenderer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class GatheringSurveyRendererMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void essenceAscendance$gatheringSurvey(GraphicsResourceAllocator allocator,
            DeltaTracker deltaTracker, boolean renderBlockOutline, CameraRenderState cameraState,
            Matrix4fc positionMatrix, GpuBufferSlice projectionBuffer, Vector4f fogColor,
            boolean renderSky, ChunkSectionsToRender sections, CallbackInfo ci) {
        GatheringSurveyRenderer.render(Minecraft.getInstance().gameRenderer.getMainCamera(),
                new Matrix4f(positionMatrix));
    }
}
