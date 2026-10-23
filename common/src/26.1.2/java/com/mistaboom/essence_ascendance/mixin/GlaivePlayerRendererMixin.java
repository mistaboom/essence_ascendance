package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.GlaiveClientState2612;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Resolves the owning player when the native 26.1 render state reaches its model and held layers. */
@Mixin(AvatarRenderer.class)
public abstract class GlaivePlayerRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void essenceAscendance$capturePlayer(Avatar entity, AvatarRenderState state,
            float partialTick, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer player) GlaiveClientState2612.capture(player, state);
    }
}
