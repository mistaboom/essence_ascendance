package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Periodic harmful effects are derived actions, including restored effects with no trustworthy original source.
 * Preserve native ticking/health and scope only proc/adaptation/retaliation eligibility. No saved provenance. */
@Mixin(MobEffectInstance.class)
public abstract class StatusPeriodicEffectMixin {
    @WrapMethod(method = "tick")
    private boolean essenceAscendance$derivedTick(LivingEntity entity, Runnable changed, Operation<Boolean> original) {
        if (entity.level().isClientSide || ((MobEffectInstance)(Object)this).getEffect().value().getCategory()
                != net.minecraft.world.effect.MobEffectCategory.HARMFUL) return original.call(entity, changed);
        boolean[] result = {false};
        EquipmentDamageService.withSecondarySkillDamage(() -> result[0] = original.call(entity, changed));
        return result[0];
    }
}
