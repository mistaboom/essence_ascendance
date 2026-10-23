package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/** Submits the authored Crucible mesh and its persistent world field. */
public final class EssenceCrucibleRenderer implements BlockEntityRenderer<EssenceCrucibleBlockEntity,
        MachineBlockEntityRenderState<EssenceCrucibleBlockEntity>> {
    public EssenceCrucibleRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public MachineBlockEntityRenderState<EssenceCrucibleBlockEntity> createRenderState() {
        return new MachineBlockEntityRenderState<>();
    }

    @Override public void extractRenderState(EssenceCrucibleBlockEntity crucible,
            MachineBlockEntityRenderState<EssenceCrucibleBlockEntity> state, float partialTick,
            Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderState.extractBase(crucible, state, breaking);
        state.source = crucible;
        state.partialTick = partialTick;
    }

    @Override public void submit(MachineBlockEntityRenderState<EssenceCrucibleBlockEntity> state,
            PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        EssenceMachineMeshes.CRUCIBLE.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY);
        if (state.source != null && state.source.getLevel() != null)
            MachineWorldVisualRenderer.enqueue(state.source, state.partialTick);
    }

    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public int getViewDistance() { return CrucibleVisuals.VIEW_DISTANCE; }
}
