package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mistaboom.essence_ascendance.client.AscensionAnimation;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Keep the native collision rays while easing the temporary rear-view distance. */
@Mixin(Camera.class)
abstract class AscendanceCameraMixin {
    @ModifyArg(method = "alignWithEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"), index = 0, require = 1)
    private float essenceAscendance$ceremonyDistance(float distance,
            @Local(argsOnly = true) float partialTick) {
        return AscensionAnimation.cameraDistance(distance, partialTick);
    }
}
