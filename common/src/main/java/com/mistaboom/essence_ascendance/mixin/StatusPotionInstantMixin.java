package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.status.StatusInterceptionService;
import com.mistaboom.essence_ascendance.utility.UtilityPotionService;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ThrownPotion.class)
public abstract class StatusPotionInstantMixin {
    @WrapOperation(method = "applySplash", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"), require = 1, expect = 1)
    private boolean essenceAscendance$source(LivingEntity target, net.minecraft.world.effect.MobEffectInstance effect,
                                             Entity source, Operation<Boolean> original) {
        Entity potion = (Entity)(Object)this;
        return UtilityPotionService.applyExternalPotion(target, effect, source,
                (resolvedEffect, resolvedSource) -> StatusInterceptionService.withSource(
                        target, resolvedEffect, potion, resolvedSource,
                        () -> original.call(target, resolvedEffect, resolvedSource)));
    }

    @WrapOperation(method = "applySplash", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/effect/MobEffect;applyInstantenousEffect(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;ID)V"), require = 1)
    private void essenceAscendance$instant(MobEffect effect, Entity direct, Entity owner, LivingEntity target,
                                           int amplifier, double potency, Operation<Void> original) {
        double resolvedPotency = UtilityPotionService.externalInstantPotency(effect, target, potency);
        StatusInterceptionService.instant(effect, direct, owner, target, amplifier, resolvedPotency,
                () -> original.call(effect, direct, owner, target, amplifier, resolvedPotency));
    }
}
