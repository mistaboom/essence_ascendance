package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.GlaiveRenderer;
import com.mistaboom.essence_ascendance.client.MachineWorldVisualRenderer;
import com.mistaboom.essence_ascendance.client.procedural.ProceduralWorldQueue;
import com.mistaboom.essence_ascendance.client.transientfx.TransientWorldVisuals;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
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

/** Integrates the existing depth-sorted visual queues with the 26.1 render-level boundary. */
@Mixin(LevelRenderer.class)
public abstract class MachineWorldVisualRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void essenceAscendance$beginMachineVisualFrame(GraphicsResourceAllocator allocator,
            DeltaTracker deltaTracker, boolean renderBlockOutline, CameraRenderState cameraState,
            Matrix4fc positionMatrix, GpuBufferSlice projectionBuffer, Vector4f fogColor,
            boolean renderSky, ChunkSectionsToRender sections, CallbackInfo ci) {
        MachineWorldVisualRenderer.beginFrame();
        GlaiveRenderer.beginFrame();
        ProceduralWorldQueue.beginFrame(new Matrix4f(positionMatrix));
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void essenceAscendance$renderMachineVisuals(GraphicsResourceAllocator allocator,
            DeltaTracker deltaTracker, boolean renderBlockOutline, CameraRenderState cameraState,
            Matrix4fc positionMatrix, GpuBufferSlice projectionBuffer, Vector4f fogColor,
            boolean renderSky, ChunkSectionsToRender sections, CallbackInfo ci) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Matrix4f view = new Matrix4f(positionMatrix);
        Matrix4f projection = new Matrix4f(camera.getViewRotationProjectionMatrix(new Matrix4f()))
                .mul(new Matrix4f(camera.getViewRotationMatrix(new Matrix4f())).invert());
        MachineWorldVisualRenderer.render(camera, view);
        GlaiveRenderer.renderWorld(camera, view, projection,
                deltaTracker.getGameTimeDeltaPartialTick(true));
        ProceduralWorldQueue.render();
        TransientWorldVisuals.render(camera, view,
                deltaTracker.getGameTimeDeltaPartialTick(true));
    }
}
