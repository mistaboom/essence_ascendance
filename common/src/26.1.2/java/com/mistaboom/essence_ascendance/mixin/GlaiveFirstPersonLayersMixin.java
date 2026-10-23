package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mistaboom.essence_ascendance.client.GlaiveFirstPersonBody;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.List;

/** The attachment probe submits only the held layer; the real world pass still draws the player. */
@Mixin(LivingEntityRenderer.class)
public abstract class GlaiveFirstPersonLayersMixin {
    @ModifyReturnValue(method = "getRenderType", at = @At("RETURN"))
    private RenderType essenceAscendance$probeHasNoBody(RenderType original) {
        return GlaiveFirstPersonBody.rendering() ? null : original;
    }

    @ModifyExpressionValue(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;layers:Ljava/util/List;"))
    private List<?> essenceAscendance$onlyHeldLayer(List<?> layers) {
        return GlaiveFirstPersonBody.rendering()
                ? layers.stream().filter(layer -> layer instanceof ItemInHandLayer<?, ?>).toList()
                : layers;
    }
}
