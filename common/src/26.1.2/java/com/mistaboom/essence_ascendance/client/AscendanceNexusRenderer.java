package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.nexus.AscendanceNexusBlockEntity;
import com.mistaboom.essence_ascendance.machine.HorizontalMachineBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/** Submits the authored Nexus lectern and its floating codex field. */
public final class AscendanceNexusRenderer implements BlockEntityRenderer<AscendanceNexusBlockEntity,
        MachineBlockEntityRenderState<AscendanceNexusBlockEntity>> {
    public AscendanceNexusRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public MachineBlockEntityRenderState<AscendanceNexusBlockEntity> createRenderState() {
        return new MachineBlockEntityRenderState<>();
    }

    @Override public void extractRenderState(AscendanceNexusBlockEntity nexus,
            MachineBlockEntityRenderState<AscendanceNexusBlockEntity> state, float partialTick,
            Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderState.extractBase(nexus, state, breaking);
        state.source = nexus;
        state.partialTick = partialTick;
        state.frame = HorizontalMachineBlock.frame(
                nexus.getBlockState().getValue(HorizontalMachineBlock.FACING));
    }

    @Override public void submit(MachineBlockEntityRenderState<AscendanceNexusBlockEntity> state,
            PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        PylonRenderTransform.applyAroundBlockCenter(pose, state.frame);
        EssenceMachineMeshes.NEXUS.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        if (state.source != null && state.source.getLevel() != null)
            MachineWorldVisualRenderer.enqueue(state.source, state.partialTick);
    }

    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public int getViewDistance() { return NexusVisuals.VIEW_DISTANCE; }
}
