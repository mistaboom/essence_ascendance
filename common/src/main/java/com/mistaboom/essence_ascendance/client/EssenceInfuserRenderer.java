package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemStack;

/** Renders the modeled Infuser plus the installed floating Essence Focus. */
public final class EssenceInfuserRenderer
        implements BlockEntityRenderer<EssenceInfuserBlockEntity> {

    public EssenceInfuserRenderer(BlockEntityRendererProvider.Context context) {
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

        ItemStack focus = infuser.getItem(EssenceInfuserBlockEntity.FOCUS_SLOT);
        if (focus.isEmpty()) {
            return;
        }

        if (infuser.getLevel() != null) {
            FocusVisuals.renderInstalled(FocusVisuals.Context.installed(infuser.visualState().focus()),
                    infuser.getLevel(), infuser.getBlockPos(), partialTick,
                    poseStack, bufferSource, packedOverlay);
        }
    }
}
