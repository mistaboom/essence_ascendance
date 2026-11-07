package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.skill.effect.GuardMobilityController;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Query-only step height; native Entity.collide performs all real ground/ceiling/terrain resolution. */
@Mixin(LivingEntity.class)
abstract class GuardMovementLivingEntityMixin {
    @WrapMethod(method = "jumpFromGround")
    private void essenceAscendance$nativeJumpObservation(Operation<Void> original) {
        if((Object)this instanceof net.minecraft.server.level.ServerPlayer player)
            com.mistaboom.essence_ascendance.movement.PlayerMotionTracker.nativeJump(player,()->original.call());
        else original.call();
    }
    @Inject(method = "maxUpStep", at = @At("RETURN"), cancellable = true, require = 1, expect = 1)
    private void essenceAscendance$guardedStep(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(GuardMobilityController.stepHeight((LivingEntity) (Object) this, cir.getReturnValue()));
    }
}
