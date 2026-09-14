package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.status.StatusInterceptionService;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AreaEffectCloud.class)
public abstract class StatusCloudInstantMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"), require = 1, expect = 1)
    private boolean essenceAscendance$source(LivingEntity target, net.minecraft.world.effect.MobEffectInstance effect,
                                             Entity source, Operation<Boolean> original) {
        return StatusInterceptionService.withSource(target, effect, (Entity)(Object)this,
                ((AreaEffectCloud)(Object)this).getOwner(), () -> original.call(target, effect, source));
    }
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/effect/MobEffect;applyInstantenousEffect(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;ID)V"), require = 1)
    private void essenceAscendance$instant(MobEffect effect, Entity direct, Entity owner, LivingEntity target,
                                           int amplifier, double potency, Operation<Void> original) {
        StatusInterceptionService.instant(effect, direct, owner, target, amplifier, potency,
                () -> original.call(effect, direct, owner, target, amplifier, potency));
    }
}
