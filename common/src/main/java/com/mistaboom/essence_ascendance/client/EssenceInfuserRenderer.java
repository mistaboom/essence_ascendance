package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Placeholder renderer: the installed shared Focus floats above the Infuser. */
public final class EssenceInfuserRenderer
        implements BlockEntityRenderer<EssenceInfuserBlockEntity> {

    private final ItemRenderer itemRenderer;

    public EssenceInfuserRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
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
        ItemStack focus = infuser.getItem(EssenceInfuserBlockEntity.FOCUS_SLOT);
        if (focus.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5D, 1.30D, 0.5D);
        long gameTime = infuser.getLevel() == null
                ? 0L
                : infuser.getLevel().getGameTime();
        poseStack.mulPose(Axis.YP.rotationDegrees((gameTime + partialTick) * 2.0F));
        poseStack.scale(0.70F, 0.70F, 0.70F);
        int focusLight = infuser.getLevel() == null
                ? packedLight
                : LevelRenderer.getLightColor(
                        infuser.getLevel(),
                        infuser.getBlockPos().above()
                );

        itemRenderer.renderStatic(
                focus,
                ItemDisplayContext.FIXED,
                focusLight,
                packedOverlay,
                poseStack,
                bufferSource,
                infuser.getLevel(),
                (int) infuser.getBlockPos().asLong()
        );
        poseStack.popPose();
    }
}
