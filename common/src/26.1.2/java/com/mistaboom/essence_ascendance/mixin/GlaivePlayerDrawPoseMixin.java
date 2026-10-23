package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.GlaiveClientState2612;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restore the arm after deferred setup; its native child sleeve follows automatically. */
@Mixin(PlayerModel.class)
public abstract class GlaivePlayerDrawPoseMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("TAIL"), require = 1)
    private void essenceAscendance$restoreSubmittedArm(AvatarRenderState state, CallbackInfo ci) {
        GlaiveClientState2612.restoreArm(state, (PlayerModel)(Object)this);
    }
}
