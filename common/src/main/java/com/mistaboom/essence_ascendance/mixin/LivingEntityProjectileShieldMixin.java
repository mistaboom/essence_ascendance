package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.projectile.ProjectileDefenseService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shared Fabric/NeoForge bridge for vanilla shields and shields using vanilla's blocking contract. */
@Mixin(LivingEntity.class)
abstract class LivingEntityProjectileShieldMixin {
    //? if >=26.1.2
    /*@Inject(method = "applyItemBlocking", at = @At("HEAD"), cancellable = true)*/
    //? if <26.1.2
    @Inject(method = "isDamageSourceBlocked", at = @At("HEAD"), cancellable = true)
    //? if >=26.1.2
    /*private void essenceAscendance$pierceShield(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {*/
    //? if <26.1.2
    private void essenceAscendance$pierceShield(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (ProjectileDefenseService.bypasses(ProjectileDefenseService.VANILLA_SHIELD,
                //? if >=26.1.2
                /*(LivingEntity) (Object) this, source)) cir.setReturnValue(0.0F);*/
                //? if <26.1.2
                (LivingEntity) (Object) this, source)) cir.setReturnValue(false);
    }
}
