package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.pylon.EssencePylonBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemStack;

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
        EssenceMachineMeshes.PYLON.render(
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay
        );

        ItemStack focus = pylon.getItem(EssencePylonBlockEntity.FOCUS_SLOT);
        if (focus.isEmpty()) {
            return;
        }

        if (pylon.getLevel() != null) {
            FocusVisuals.renderInstalled(FocusVisuals.Context.installed(pylon.visualState().focus()),
                    pylon.getLevel(), pylon.getBlockPos(), partialTick,
                    poseStack, bufferSource, packedOverlay);
        }
    }
}
