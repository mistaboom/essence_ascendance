package com.mistaboom.essence_ascendance.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.skill.effect.AbsorptionPoolService;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Observes native absorption writes and isolates effect-owned hearts without replacing potion semantics. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityAbsorptionPoolMixin {
    // In 1.21.1 the public setter is final and declared on LivingEntity. Player only overrides
    // the internal storage method, so a WrapMethod on Player cannot find this inherited method.
    @WrapMethod(method = "setAbsorptionAmount(F)V")
    private void essenceAscendance$ownedAbsorption(float amount, Operation<Void> original) {
        AbsorptionPoolService.observeWrite((LivingEntity) (Object) this, () -> original.call(amount));
    }

    // Enclose the whole application: onEffectStarted runs outside onEffectAdded and can refill absorption.
    @WrapMethod(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z")
    private boolean essenceAscendance$effectAdded(MobEffectInstance effect, Entity source, Operation<Boolean> original) {
        return AbsorptionPoolService.externalEffectResult((LivingEntity) (Object) this, effect, () -> original.call(effect, source));
    }
    @WrapMethod(method = "forceAddEffect")
    private void essenceAscendance$effectForced(MobEffectInstance effect, Entity source, Operation<Void> original) {
        AbsorptionPoolService.externalEffect((LivingEntity) (Object) this, effect, () -> original.call(effect, source));
    }
    @WrapMethod(method = "onEffectUpdated")
    private void essenceAscendance$effectUpdated(MobEffectInstance effect, boolean forced, Entity source,
                                                 Operation<Void> original) {
        AbsorptionPoolService.externalEffect((LivingEntity) (Object) this, effect, () -> original.call(effect, forced, source));
    }
    @WrapMethod(method = "onEffectRemoved")
    private void essenceAscendance$effectRemoved(MobEffectInstance effect, Operation<Void> original) {
        AbsorptionPoolService.externalEffect((LivingEntity) (Object) this, effect, () -> original.call(effect));
    }
}
