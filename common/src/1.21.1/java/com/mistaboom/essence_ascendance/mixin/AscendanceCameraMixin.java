package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.AscensionAnimation;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Retain vanilla's eight collision rays while easing the temporary rear-view distance. */
@Mixin(Camera.class)
abstract class AscendanceCameraMixin {
    @Shadow private float partialTickTime;

    @ModifyArg(method = "setup", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"), index = 0)
    private float essenceAscendance$ceremonyDistance(float distance) {
        return AscensionAnimation.cameraDistance(distance, partialTickTime);
    }
}
