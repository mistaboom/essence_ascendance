package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.movement.TraversalService;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Use normal scene fog/color, preserving render distance, weather, Blindness and Darkness.
 * Actual Camera fluid state is not changed for any other renderer or gameplay caller. */
@Mixin(FogRenderer.class)
abstract class TraversalFogMixin {
    @WrapOperation(method = {"setupFog", "setupColor"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getFluidInCamera()Lnet/minecraft/world/level/material/FogType;"))
    private static FogType essenceAscendance$clearImmersion(Camera camera, Operation<FogType> original) {
        FogType fluid = original.call(camera);
        if (fluid == FogType.WATER && TraversalService.clearWaterVision(camera.getEntity())
                || fluid == FogType.LAVA && TraversalService.clearLavaVision(camera.getEntity())) return FogType.NONE;
        return fluid;
    }
}
