package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.GlaiveClientState2612;
import com.mistaboom.essence_ascendance.client.GlaiveRenderer;
import com.mistaboom.essence_ascendance.network.TwoHandedPolicyPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the native arm bone and applies the authoritative two-handed offhand decision. */
@Mixin(ItemInHandLayer.class)
public abstract class GlaiveHeldLayerMixin {
    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$heldModel(ArmedEntityRenderState state,
            ItemStackRenderState itemState, ItemStack stack, HumanoidArm arm,
            PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState avatar)) return;
        var player = GlaiveClientState2612.player(avatar);
        if (player == null) return;
        boolean main = arm == state.mainArm;
        if (main && GlaiveRenderer.held(player)) {
            pose.pushPose();
            try {
                var model = ((RenderLayer<?, ?>)(Object)this).getParentModel();
                if (model instanceof PlayerModel playerModel)
                    (arm == HumanoidArm.RIGHT ? playerModel.rightArm : playerModel.leftArm).translateAndRotate(pose);
                GlaiveRenderer.captureHand(player, pose);
            } finally { pose.popPose(); }
            ci.cancel();
        } else if (!main && !TwoHandedPolicyPayload.clientDecision(player).offhandVisible()) ci.cancel();
    }
}
