package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.client.SkillEffectHudClientState;
import net.minecraft.client.player.LocalPlayer;
//? if <26.1.2
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native pick still clips terrain and applies its entity hitbox/range rules. */
//? if >=26.1.2
/*@Mixin(LocalPlayer.class)
*///? if <26.1.2
@Mixin(GameRenderer.class)
public abstract class RiposteReachRendererMixin {
    //? if >=26.1.2
    /*@WrapOperation(method = "raycastHitResult", at = @At(value = "INVOKE",
    *///? if <26.1.2
    @WrapOperation(method = "pick(F)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;entityInteractionRange()D"), require = 1)
    private double essenceAscendance$counterattackReach(LocalPlayer player, Operation<Double> original) {
        return original.call(player) + SkillEffectHudClientState.primaryMeleeBonusReach();
    }
}
