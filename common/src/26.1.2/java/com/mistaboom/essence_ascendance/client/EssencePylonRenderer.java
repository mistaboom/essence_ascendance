package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.pylon.EssencePylonBlockEntity;
import com.mistaboom.essence_ascendance.pylon.PylonLocalFrame;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/** Submits the authored Pylon in its exact floor, wall, or ceiling orientation. */
public final class EssencePylonRenderer implements BlockEntityRenderer<EssencePylonBlockEntity,
        MachineBlockEntityRenderState<EssencePylonBlockEntity>> {
    public EssencePylonRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public MachineBlockEntityRenderState<EssencePylonBlockEntity> createRenderState() {
        return new MachineBlockEntityRenderState<>();
    }

    @Override public void extractRenderState(EssencePylonBlockEntity pylon,
            MachineBlockEntityRenderState<EssencePylonBlockEntity> state, float partialTick,
            Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderState.extractBase(pylon, state, breaking);
        state.source = pylon;
        state.partialTick = partialTick;
        state.frame = PylonLocalFrame.of(pylon.getBlockState());
    }

    @Override public void submit(MachineBlockEntityRenderState<EssencePylonBlockEntity> state,
            PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        PylonRenderTransform.applyAroundBlockCenter(pose, state.frame);
        EssenceMachineMeshes.PYLON.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        if (state.source != null && state.source.getLevel() != null)
            MachineWorldVisualRenderer.enqueue(state.source, state.partialTick, OverlayTexture.NO_OVERLAY);
    }

    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public int getViewDistance() { return PylonVisuals.VIEW_DISTANCE; }
}
