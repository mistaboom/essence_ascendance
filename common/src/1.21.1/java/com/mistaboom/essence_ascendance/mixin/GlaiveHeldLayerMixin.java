package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.GlaiveRenderer;
import com.mistaboom.essence_ascendance.network.TwoHandedPolicyPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class GlaiveHeldLayerMixin<T extends LivingEntity,M extends EntityModel<T> & ArmedModel> extends RenderLayer<T,M> {
    protected GlaiveHeldLayerMixin(RenderLayerParent<T,M> parent) { super(parent); }
    @Inject(method="renderArmWithItem",at=@At("HEAD"),cancellable=true)
    private void essenceAscendance$heldModel(LivingEntity entity,ItemStack stack,ItemDisplayContext display,HumanoidArm arm,
                                             PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        boolean main=arm==player.getMainArm();
        if (main && GlaiveRenderer.held(player)) {
            pose.pushPose();
            try {
                if(getParentModel() instanceof net.minecraft.client.model.PlayerModel<?> model)
                    (arm==HumanoidArm.RIGHT ? model.rightArm:model.leftArm).translateAndRotate(pose);
                else getParentModel().translateToHand(arm,pose);
                GlaiveRenderer.captureHand(player,pose);
            }
            finally { pose.popPose(); }
        }
        if (main && GlaiveRenderer.held(player)
                || !main && !TwoHandedPolicyPayload.clientDecision(player).offhandVisible()) ci.cancel();
    }
}
