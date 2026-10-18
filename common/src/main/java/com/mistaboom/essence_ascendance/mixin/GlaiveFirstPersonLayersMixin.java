package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mistaboom.essence_ascendance.client.GlaiveFirstPersonBody;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.List;

@Mixin(LivingEntityRenderer.class)
public abstract class GlaiveFirstPersonLayersMixin {
    @ModifyExpressionValue(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at=@At(value="FIELD",target="Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;layers:Ljava/util/List;"))
    private List<?> essenceAscendance$glaiveFirstPersonLayers(List<?> layers) {
        return GlaiveFirstPersonBody.rendering() ? layers.stream().filter(layer -> layer instanceof ItemInHandLayer<?,?>).toList():layers;
    }
}
