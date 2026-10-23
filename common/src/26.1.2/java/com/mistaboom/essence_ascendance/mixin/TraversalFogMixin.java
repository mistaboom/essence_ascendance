package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.movement.TraversalService;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep normal distance and environmental fog while clearing only the immersion type. */
@Mixin(FogRenderer.class)
abstract class TraversalFogMixin {
    @WrapOperation(method = "getFogType", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;getFluidInCamera()Lnet/minecraft/world/level/material/FogType;"))
    private FogType essenceAscendance$clearImmersion(Camera camera, Operation<FogType> original) {
        FogType fluid = original.call(camera);
        if ((fluid == FogType.WATER && TraversalService.clearWaterVision(camera.entity()))
                || (fluid == FogType.LAVA && TraversalService.clearLavaVision(camera.entity()))) {
            return FogType.NONE;
        }
        return fluid;
    }
}
