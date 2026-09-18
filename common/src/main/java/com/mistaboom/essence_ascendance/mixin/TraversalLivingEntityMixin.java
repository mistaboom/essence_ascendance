package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mistaboom.essence_ascendance.movement.TraversalService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class TraversalLivingEntityMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$contactDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (TraversalService.protectsDamage((LivingEntity)(Object)this, source)) cir.setReturnValue(false);
    }
    // LivingEntity overrides Entity.canFreeze; hook the actual player-dispatched method.
    @ModifyReturnValue(method = "canFreeze", at = @At("RETURN"))
    private boolean essenceAscendance$terrainFreeze(boolean original) {
        return original && !TraversalService.preventsFreezing((LivingEntity)(Object)this);
    }
    @ModifyReturnValue(method = "canStandOnFluid", at = @At("RETURN"))
    private boolean essenceAscendance$surfacePermission(boolean original, FluidState fluid) {
        return original || TraversalService.canStandOnFluid((LivingEntity)(Object)this, fluid);
    }
    // NeoForge has an additional isInWater test inside its FluidType dispatch. Both calls must agree.
    @ModifyExpressionValue(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isInWater()Z"))
    private boolean essenceAscendance$nativeFluidTravel(boolean original) {
        return TraversalService.swimmingWater((LivingEntity)(Object)this, original);
    }
    // Keep the native water-efficiency calculation, including its off-ground scaling.
    // Aquatic Body and Lavaborn never supply a separate swimming-speed bonus.
}
