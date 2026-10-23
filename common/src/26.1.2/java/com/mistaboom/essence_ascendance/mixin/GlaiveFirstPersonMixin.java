package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.GlaiveFirstPersonBody;
import com.mistaboom.essence_ascendance.client.GlaivePlayerAnimation;
import com.mistaboom.essence_ascendance.client.GlaiveRenderer;
import com.mistaboom.essence_ascendance.network.TwoHandedPolicyPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps first-person glaive placement and the two-handed policy in the native hand submit pass. */
@Mixin(ItemInHandRenderer.class)
public abstract class GlaiveFirstPersonMixin {
    @Invoker("renderPlayerArm")
    protected abstract void essenceAscendance$arm(PoseStack pose, SubmitNodeCollector collector,
            int light, float equip, float swing, HumanoidArm arm);

    @Invoker("applyItemArmTransform")
    protected abstract void essenceAscendance$itemTransform(PoseStack pose, HumanoidArm arm, float equip);

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$glaive(AbstractClientPlayer player, float partial, float pitch,
            InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack pose,
            SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (hand == InteractionHand.OFF_HAND
                && !TwoHandedPolicyPayload.clientDecision(player).offhandVisible()) {
            if (!GlaiveRenderer.held(player) && !player.isInvisible())
                essenceAscendance$arm(pose, collector, light, equip, 0,
                        player.getMainArm().getOpposite());
            ci.cancel();
        }
        if (hand == InteractionHand.MAIN_HAND && GlaivePlayerAnimation.active(player)) {
            if (!player.isScoping()) {
                GlaiveFirstPersonBody.render(partial);
                pose.pushPose();
                try {
                    essenceAscendance$itemTransform(pose, player.getMainArm(), 0);
                    GlaiveRenderer.submitFirstPerson(player, pose, collector, light);
                } finally { pose.popPose(); }
            }
            ci.cancel();
        }
    }
}
