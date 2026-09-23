package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.pylon.EssencePylonBlockEntity;
import com.mistaboom.essence_ascendance.pylon.EssencePylonBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Renders the modeled Pylon plus the installed floating Essence Focus. */
public final class EssencePylonRenderer
        implements BlockEntityRenderer<EssencePylonBlockEntity> {

    public EssencePylonRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            EssencePylonBlockEntity pylon,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        poseStack.pushPose();
        PylonRenderTransform.applyAroundBlockCenter(
                poseStack, pylon.getBlockState().getValue(EssencePylonBlock.FACING));
        EssenceMachineMeshes.PYLON.render(
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay
        );
        poseStack.popPose();

        if (pylon.getLevel() != null) {
            MachineWorldVisualRenderer.enqueue(pylon, partialTick, packedOverlay);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(EssencePylonBlockEntity pylon) {
        // The resonance field intentionally extends beyond the one-block machine bounds.
        return true;
    }

    @Override
    public int getViewDistance() {
        return PylonVisuals.VIEW_DISTANCE;
    }
}
