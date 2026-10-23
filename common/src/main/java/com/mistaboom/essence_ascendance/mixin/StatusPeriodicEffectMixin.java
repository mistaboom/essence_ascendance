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
    //? if >=26.1.2
    /*@WrapMethod(method = "tickServer")*/
    //? if <26.1.2
    @WrapMethod(method = "tick")
    //? if >=26.1.2
    /*private boolean essenceAscendance$derivedTick(net.minecraft.server.level.ServerLevel level, LivingEntity entity, Runnable changed, Operation<Boolean> original) {*/
    //? if <26.1.2
    private boolean essenceAscendance$derivedTick(LivingEntity entity, Runnable changed, Operation<Boolean> original) {
        if (entity.level().isClientSide || ((MobEffectInstance)(Object)this).getEffect().value().getCategory()
                //? if >=26.1.2
                /*!= net.minecraft.world.effect.MobEffectCategory.HARMFUL) return original.call(level, entity, changed);*/
                //? if <26.1.2
                != net.minecraft.world.effect.MobEffectCategory.HARMFUL) return original.call(entity, changed);
        boolean[] result = {false};
        //? if >=26.1.2
        /*EquipmentDamageService.withSecondarySkillDamage(() -> result[0] = original.call(level, entity, changed));*/
        //? if <26.1.2
        EquipmentDamageService.withSecondarySkillDamage(() -> result[0] = original.call(entity, changed));
        return result[0];
    }
}
