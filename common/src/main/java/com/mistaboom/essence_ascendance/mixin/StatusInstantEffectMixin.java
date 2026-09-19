package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.status.StatusInterceptionService;
import com.mistaboom.essence_ascendance.utility.UtilityPotionService;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Base dispatch fallback for direct native callers. Generic potion/cloud bridges also cover custom overrides. */
@Mixin(MobEffect.class)
public abstract class StatusInstantEffectMixin {
    @WrapMethod(method = "applyInstantenousEffect")
    private void essenceAscendance$instant(Entity direct, Entity owner, LivingEntity target, int amplifier,
                                           double potency, Operation<Void> original) {
        MobEffect effect = (MobEffect)(Object)this;
        double resolvedPotency = UtilityPotionService.consumedInstantPotency(effect, target, amplifier, potency);
        StatusInterceptionService.instant(effect, direct, owner, target, amplifier, resolvedPotency,
                () -> original.call(direct, owner, target, amplifier, resolvedPotency));
    }
}
