package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;

/** Renders the modeled Infuser, its containment field, workpiece, and shared Focus. */
public final class EssenceInfuserRenderer
        implements BlockEntityRenderer<EssenceInfuserBlockEntity> {
    private final ItemRenderer itemRenderer;

    public EssenceInfuserRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            EssenceInfuserBlockEntity infuser,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        EssenceMachineMeshes.INFUSER.render(
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay
        );

        if (infuser.getLevel() != null) {
            MachineWorldVisualRenderer.enqueue(infuser, itemRenderer,
                    partialTick, packedOverlay);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(EssenceInfuserBlockEntity infuser) {
        // The containment field intentionally extends beyond the one-block mesh.
        return true;
    }

    @Override
    public int getViewDistance() {
        return InfuserVisuals.VIEW_DISTANCE;
    }
}
