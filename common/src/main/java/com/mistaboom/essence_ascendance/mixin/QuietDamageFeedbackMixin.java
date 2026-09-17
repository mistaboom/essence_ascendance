package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mistaboom.essence_ascendance.damage.DamageFeedbackAccess;
import com.mistaboom.essence_ascendance.damage.DamageFeedbackPolicy;
import com.mistaboom.essence_ascendance.damage.DamageFeedbackState;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the native damage packet as a quiet-health marker, but not as a repeated hit animation. */
@Mixin(LivingEntity.class)
public abstract class QuietDamageFeedbackMixin implements DamageFeedbackAccess {
    @Unique private final DamageFeedbackState essenceAscendance$feedback = new DamageFeedbackState();
    @Override public DamageFeedbackState essenceAscendance$damageFeedback() { return essenceAscendance$feedback; }

    @Inject(method = "handleDamageEvent", at = @At("HEAD"), cancellable = true, require = 1)
    private void essenceAscendance$quietClientImpact(DamageSource source, CallbackInfo ci) {
        boolean quiet = DamageFeedbackPolicy.quiet(source);
        essenceAscendance$feedback.observe(quiet);
        if (quiet) ci.cancel();
    }

    // ServerPlayer overrides indicateDamage to send a separate directional-hurt packet. Gate the
    // virtual CALL in hurt, not only the base method, so the override cannot repeatedly tilt the view.
    @WrapOperation(method = "hurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;indicateDamage(DD)V"), require = 1)
    private void essenceAscendance$quietDirection(LivingEntity entity, double x, double z,
                                                  Operation<Void> original, @Local(argsOnly = true) DamageSource source) {
        if (!DamageFeedbackPolicy.quiet(source)) original.call(entity, x, z);
    }

    @WrapOperation(method = "hurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"), require = 1)
    private void essenceAscendance$quietKnockback(LivingEntity entity, double strength, double x, double z,
                                                  Operation<Void> original, @Local(argsOnly = true) DamageSource source) {
        if (!DamageFeedbackPolicy.quiet(source)) original.call(entity, strength, x, z);
    }

    @Inject(method = "playHurtSound", at = @At("HEAD"), cancellable = true, require = 1)
    private void essenceAscendance$quietHurtSound(DamageSource source, CallbackInfo ci) {
        if (DamageFeedbackPolicy.quiet(source)) ci.cancel();
    }
}
