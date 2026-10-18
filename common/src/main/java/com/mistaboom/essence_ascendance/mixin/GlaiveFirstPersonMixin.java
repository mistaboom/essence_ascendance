package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.network.TwoHandedPolicyPayload;
import com.mistaboom.essence_ascendance.client.GlaivePlayerAnimation;
import com.mistaboom.essence_ascendance.client.GlaiveRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla-style weapon-only first person. A held trident does not render a separate player arm. */
@Mixin(ItemInHandRenderer.class)
public abstract class GlaiveFirstPersonMixin {
    @Invoker("renderPlayerArm")
    protected abstract void essenceAscendance$arm(PoseStack pose,MultiBufferSource buffers,int light,float equip,float swing,HumanoidArm arm);

    @Invoker("applyItemArmTransform")
    protected abstract void essenceAscendance$itemTransform(PoseStack pose,HumanoidArm arm,float equip);

    @Inject(method="renderArmWithItem",at=@At("HEAD"),cancellable=true)
    private void essenceAscendance$offhand(AbstractClientPlayer player,float partial,float pitch,InteractionHand hand,
                                           float swing,ItemStack stack,float equip,PoseStack pose,MultiBufferSource buffers,
                                           int light,CallbackInfo ci) {
        if (hand==InteractionHand.OFF_HAND && !TwoHandedPolicyPayload.clientDecision(player).offhandVisible()) {
            if (!GlaiveRenderer.held(player) && !player.isInvisible())
                essenceAscendance$arm(pose,buffers,light,equip,0,player.getMainArm().getOpposite());
            ci.cancel();
        }
        if (hand==InteractionHand.MAIN_HAND && GlaivePlayerAnimation.active(player)) {
            if (!player.isScoping() && GlaivePlayerAnimation.attack(player,partial)==null) {
                pose.pushPose();
                try {
                    essenceAscendance$itemTransform(pose,player.getMainArm(),equip);
                    GlaiveRenderer.renderFirstPersonRest(player,pose,buffers,light);
                } finally { pose.popPose(); }
            }
            ci.cancel();
        }
    }
}
