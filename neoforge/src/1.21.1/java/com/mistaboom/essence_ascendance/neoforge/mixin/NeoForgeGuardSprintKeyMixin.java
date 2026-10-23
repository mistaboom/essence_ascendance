package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mistaboom.essence_ascendance.skill.effect.GuardMobilityController;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge repeats the item-use test after the shared native sprint decision helper. */
@Mixin(LocalPlayer.class)
public abstract class NeoForgeGuardSprintKeyMixin {
    @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z", ordinal = 1), require = 1, expect = 1)
    private boolean essenceAscendance$guardedSprintKey(boolean usingItem) {
        return usingItem && !GuardMobilityController.sprintAllowed((LocalPlayer) (Object) this);
    }
}
