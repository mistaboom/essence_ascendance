package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.mistaboom.essence_ascendance.client.InventoryItemEmissionRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the shared accent pass after vanilla has drawn the selected item/bow-state model. */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererEmissionMixin {
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderModelLists("
                    + "Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;II"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V",
            shift = At.Shift.AFTER))
    private void essenceAscendance$renderAccentEmission(ItemStack stack, ItemDisplayContext displayContext,
                                                         boolean leftHand, PoseStack pose,
                                                         MultiBufferSource buffers, int packedLight,
                                                         int packedOverlay, BakedModel model,
                                                         CallbackInfo ci) {
        InventoryItemEmissionRenderer.render(stack, model, pose, buffers);
    }
}
