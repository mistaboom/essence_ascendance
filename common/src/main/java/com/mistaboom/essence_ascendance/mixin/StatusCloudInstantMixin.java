package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.status.StatusInterceptionService;
import com.mistaboom.essence_ascendance.utility.UtilityPotionService;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AreaEffectCloud.class)
public abstract class StatusCloudInstantMixin {
    //? if >=26.1.2
    /*@WrapOperation(method = "serverTick", at = @At(value = "INVOKE", target =*/
    //? if <26.1.2
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"), require = 1, expect = 1)
    private boolean essenceAscendance$source(LivingEntity target, net.minecraft.world.effect.MobEffectInstance effect,
                                             Entity source, Operation<Boolean> original) {
        AreaEffectCloud cloud = (AreaEffectCloud)(Object)this;
        return UtilityPotionService.applyExternalPotion(target, effect, source,
                (resolvedEffect, resolvedSource) -> StatusInterceptionService.withSource(
                        target, resolvedEffect, cloud, cloud.getOwner(),
                        () -> original.call(target, resolvedEffect, resolvedSource)));
    }

    //? if >=26.1.2
    /*@WrapOperation(method = "serverTick", at = @At(value = "INVOKE", target =*/
    //? if <26.1.2
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
            //? if >=26.1.2
            /*"Lnet/minecraft/world/effect/MobEffect;applyInstantenousEffect(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;ID)V"), require = 1)*/
            //? if <26.1.2
            "Lnet/minecraft/world/effect/MobEffect;applyInstantenousEffect(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;ID)V"), require = 1)
    //? if >=26.1.2
    /*private void essenceAscendance$instant(MobEffect effect, net.minecraft.server.level.ServerLevel level, Entity direct, Entity owner, LivingEntity target,*/
    //? if <26.1.2
    private void essenceAscendance$instant(MobEffect effect, Entity direct, Entity owner, LivingEntity target,
                                           int amplifier, double potency, Operation<Void> original) {
        double resolvedPotency = UtilityPotionService.externalInstantPotency(effect, target, potency);
        StatusInterceptionService.instant(effect, direct, owner, target, amplifier, resolvedPotency,
                //? if >=26.1.2
                /*() -> original.call(effect, level, direct, owner, target, amplifier, resolvedPotency));*/
                //? if <26.1.2
                () -> original.call(effect, direct, owner, target, amplifier, resolvedPotency));
    }
}
