package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Renders the Blockbench Crucible mesh and its persistent dissolution field. */
public final class EssenceCrucibleRenderer
        implements BlockEntityRenderer<EssenceCrucibleBlockEntity> {

    public EssenceCrucibleRenderer(
            BlockEntityRendererProvider.Context context
    ) {
    }

    @Override
    public void render(
            EssenceCrucibleBlockEntity crucible,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        EssenceMachineMeshes.CRUCIBLE.render(
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay
        );

        if (crucible.getLevel() != null) {
            CrucibleVisuals.render(crucible, partialTick, poseStack, bufferSource);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(EssenceCrucibleBlockEntity crucible) {
        // The vertical release field intentionally extends above the one-block mesh.
        return true;
    }

    @Override
    public int getViewDistance() {
        return CrucibleVisuals.VIEW_DISTANCE;
    }
}
