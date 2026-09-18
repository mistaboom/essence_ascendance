package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mistaboom.essence_ascendance.movement.TraversalService;
import com.mistaboom.essence_ascendance.client.TraversalFluidRenderState;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scope prediction to swimming input/sprint decisions. Do not lie about the player's actual
 * water state to breathing, fire, fog, equipment, progression or other mods. */
@Mixin(LocalPlayer.class)
abstract class TraversalLocalPlayerMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void essenceAscendance$fluidRenderPermissions(CallbackInfo ci) {
        TraversalFluidRenderState.refresh();
    }
    // canStartSprinting delegates to the impulse helper; it has no direct underwater query.
    @ModifyExpressionValue(method = {"aiStep", "hasEnoughImpulseToStartSprinting"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUnderWater()Z"))
    private boolean essenceAscendance$nativeSwimmingInput(boolean original) {
        return TraversalService.swimmingUnderwater((LocalPlayer)(Object)this, original);
    }
    @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isInWater()Z"))
    private boolean essenceAscendance$nativeFluidInput(boolean original) {
        return TraversalService.swimmingWater((LocalPlayer)(Object)this, original);
    }
}
