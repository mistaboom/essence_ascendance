package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.FlightVisualLayer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Installs the native player layer and snapshots observer flight effects before submission. */
@Mixin(AvatarRenderer.class)
public abstract class FlightVisualRendererMixin
        extends LivingEntityRenderer<AbstractClientPlayer, AvatarRenderState, PlayerModel> {
    protected FlightVisualRendererMixin(EntityRendererProvider.Context context,
            PlayerModel model, float shadowRadius) { super(context, model, shadowRadius); }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void essenceAscendance$addFlightVisualLayer(EntityRendererProvider.Context context,
            boolean slim, CallbackInfo ci) {
        addLayer(new FlightVisualLayer(this));
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void essenceAscendance$captureFlight(Avatar entity, AvatarRenderState state,
            float partialTick, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer player) FlightVisualLayer.capture(player, state, partialTick);
    }
}
