package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.GlaiveClientState2612;
import com.mistaboom.essence_ascendance.client.GlaivePlayerAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Poses the shoulder after vanilla animation has set the arm and before its held layer reads it. */
@Mixin(LivingEntityRenderer.class)
public abstract class GlaivePlayerModelMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V",
                    shift = At.Shift.AFTER))
    private void essenceAscendance$poseGlaive(LivingEntityRenderState state, PoseStack pose,
            SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState avatar)) return;
        var player = GlaiveClientState2612.player(avatar);
        if (player != null && GlaivePlayerAnimation.active(player)
                && ((LivingEntityRenderer<?, ?, ?>)(Object)this).getModel() instanceof PlayerModel model) {
            float partial = net.minecraft.client.Minecraft.getInstance().getDeltaTracker()
                    .getGameTimeDeltaPartialTick(true);
            GlaivePlayerAnimation.model(player, model, pose, partial);
            if (GlaivePlayerAnimation.attack(player, partial) != null)
                GlaiveClientState2612.captureArm(avatar, model, player.getMainArm());
        }
    }
}
