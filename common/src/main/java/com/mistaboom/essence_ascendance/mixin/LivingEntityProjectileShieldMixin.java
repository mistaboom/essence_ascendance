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
    @Inject(method = "isDamageSourceBlocked", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$pierceShield(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (ProjectileDefenseService.bypasses(ProjectileDefenseService.VANILLA_SHIELD,
                (LivingEntity) (Object) this, source)) cir.setReturnValue(false);
    }
}
