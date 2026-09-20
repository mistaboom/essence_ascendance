package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.pylon.EssencePylonBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Renders the modeled Pylon plus the installed floating Essence Focus. */
public final class EssencePylonRenderer
        implements BlockEntityRenderer<EssencePylonBlockEntity> {

    private final ItemRenderer itemRenderer;

    public EssencePylonRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
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

        poseStack.pushPose();
        poseStack.translate(0.5D, 1.35D, 0.5D);

        long gameTime = pylon.getLevel() == null
                ? 0L
                : pylon.getLevel().getGameTime();
        float rotation = (gameTime + partialTick) * 2.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        poseStack.scale(0.70F, 0.70F, 0.70F);

        itemRenderer.renderStatic(
                focus,
                ItemDisplayContext.FIXED,
                packedLight,
                packedOverlay,
                poseStack,
                bufferSource,
                pylon.getLevel(),
                (int) pylon.getBlockPos().asLong()
        );

        poseStack.popPose();
    }
}
