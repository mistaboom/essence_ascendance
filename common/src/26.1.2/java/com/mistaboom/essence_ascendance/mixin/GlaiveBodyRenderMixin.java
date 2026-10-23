package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.GlaiveClientState2612;
import com.mistaboom.essence_ascendance.client.GlaivePlayerAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reapply the shoulder solve after 26.1's deferred model setup resets the visible arm. */
@Mixin(ModelFeatureRenderer.class)
public abstract class GlaiveBodyRenderMixin {
    @Shadow @Final private PoseStack poseStack;

    @Inject(method = "renderModel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
            shift = At.Shift.AFTER), require = 1)
    private <S> void essenceAscendance$poseVisibleShoulder(SubmitNodeStorage.ModelSubmit<S> submit,
            RenderType renderType, VertexConsumer vertexConsumer, OutlineBufferSource outline,
            MultiBufferSource.BufferSource buffers, CallbackInfo ci) {
        if (!(submit.state() instanceof AvatarRenderState avatar)
                || !(submit.model() instanceof HumanoidModel<?> model)
                || model instanceof net.minecraft.client.model.player.PlayerModel) return;
        var player = GlaiveClientState2612.player(avatar);
        if (player == null || !GlaivePlayerAnimation.active(player)) return;
        float partial = net.minecraft.client.Minecraft.getInstance().getDeltaTracker()
                .getGameTimeDeltaPartialTick(true);
        GlaivePlayerAnimation.armorModel(player, model, poseStack, partial);
    }
}
