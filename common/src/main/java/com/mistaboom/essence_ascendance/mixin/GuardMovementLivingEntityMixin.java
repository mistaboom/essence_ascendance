package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.skill.effect.GuardMobilityController;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Query-only step height; native Entity.collide performs all real ground/ceiling/terrain resolution. */
@Mixin(LivingEntity.class)
abstract class GuardMovementLivingEntityMixin {
    @Inject(method = "maxUpStep", at = @At("RETURN"), cancellable = true, require = 1, expect = 1)
    private void essenceAscendance$guardedStep(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(GuardMobilityController.stepHeight((LivingEntity) (Object) this, cir.getReturnValue()));
    }
}
