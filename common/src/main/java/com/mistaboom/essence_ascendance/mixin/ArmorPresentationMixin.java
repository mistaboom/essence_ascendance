package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.armor.ArmorPresentation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One common hook, after vanilla/loader armor and foil have been submitted. */
@Mixin(HumanoidArmorLayer.class)
public abstract class ArmorPresentationMixin<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    @Unique private final ArmorPresentation<T> essenceAscendance$presentation = new ArmorPresentation<>();

    protected ArmorPresentationMixin(RenderLayerParent<T, M> parent) { super(parent); }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("TAIL"))
    private void essenceAscendance$present(PoseStack pose, MultiBufferSource buffers, int light, T entity,
                                           float limbSwing, float limbSwingAmount, float partialTick,
                                           float age, float yaw, float pitch, CallbackInfo ci) {
        essenceAscendance$presentation.render(getParentModel(), entity, partialTick, pose, buffers);
    }
}
