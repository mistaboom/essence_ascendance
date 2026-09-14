package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.status.StatusInterceptionService;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Tipped-arrow base-potion and custom-effect branches both retain the actual direct arrow. */
@Mixin(Arrow.class)
public abstract class StatusArrowSourceMixin {
    @WrapOperation(method = "doPostHurtEffects", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"), require = 2, expect = 2)
    private boolean essenceAscendance$source(LivingEntity target, MobEffectInstance effect, Entity source, Operation<Boolean> original) {
        return StatusInterceptionService.withSource(target, effect, (Entity)(Object)this, source,
                () -> original.call(target, effect, source));
    }
}
