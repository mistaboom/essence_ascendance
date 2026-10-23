package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.utility.FriendlyDamageService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shared native damage boundary for direct, projectile and player-attributed ability friendly fire. */
@Mixin(LivingEntity.class)
public abstract class FriendlyFireWardMixin {
    //? if >=26.1.2
    /*@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)*/
    //? if <26.1.2
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    //? if >=26.1.2
    /*private void essenceAscendance$friendlyFireWard(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount,*/
    //? if <26.1.2
    private void essenceAscendance$friendlyFireWard(DamageSource source, float amount,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (FriendlyDamageService.prevents((LivingEntity) (Object) this, source)) cir.setReturnValue(false);
    }
}
