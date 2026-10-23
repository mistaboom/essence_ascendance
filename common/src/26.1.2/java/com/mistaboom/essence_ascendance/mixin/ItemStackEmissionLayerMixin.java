package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.InventoryItemEmissionRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Submit the same resolved model's authored accent quads in every item display context. */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public abstract class ItemStackEmissionLayerMixin {
    @Shadow @Final private List<BakedQuad> quads;
    @Shadow @Final private ItemStackRenderState this$0;

    @Inject(method = "submit", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void essenceAscendance$submitAccent(PoseStack pose, SubmitNodeCollector collector,
            int light, int overlay, int outlineColor, CallbackInfo ci) {
        InventoryItemEmissionRenderer.submit(this$0, quads, pose, collector, overlay);
    }
}
