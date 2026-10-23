package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.armor.ArmorPresentation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the generated emissive accent after native armor, trim, and foil submission. */
@Mixin(HumanoidArmorLayer.class)
public abstract class ArmorPresentationMixin {
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("TAIL"))
    private void essenceAscendance$present(PoseStack pose, SubmitNodeCollector collector, int light,
            HumanoidRenderState state, float limbSwing, float limbSwingAmount, CallbackInfo ci) {
        HumanoidModel<?> parent = (HumanoidModel<?>) ((HumanoidArmorLayer<?, ?, ?>) (Object) this).getParentModel();
        ArmorPresentation.submit(parent, state, pose, collector);
    }
}
