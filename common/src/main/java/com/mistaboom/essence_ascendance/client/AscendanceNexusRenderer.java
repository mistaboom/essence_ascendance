package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.nexus.AscendanceNexusBlockEntity;
import com.mistaboom.essence_ascendance.machine.HorizontalMachineBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Renders the restrained Nexus lectern and its persistent floating codex. */
public final class AscendanceNexusRenderer
        implements BlockEntityRenderer<AscendanceNexusBlockEntity> {

    public AscendanceNexusRenderer(
            BlockEntityRendererProvider.Context context
    ) {
    }

    @Override
    public void render(
            AscendanceNexusBlockEntity nexus,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        poseStack.pushPose();
        PylonRenderTransform.applyAroundBlockCenter(poseStack,
                HorizontalMachineBlock.frame(nexus.getBlockState().getValue(HorizontalMachineBlock.FACING)));
        EssenceMachineMeshes.NEXUS.render(
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay
        );
        poseStack.popPose();

        if (nexus.getLevel() != null) {
            MachineWorldVisualRenderer.enqueue(nexus, partialTick);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(AscendanceNexusBlockEntity nexus) {
        // The codex and its orbit intentionally rise beyond the one-block lectern bounds.
        return true;
    }

    @Override
    public int getViewDistance() {
        return NexusVisuals.VIEW_DISTANCE;
    }
}
