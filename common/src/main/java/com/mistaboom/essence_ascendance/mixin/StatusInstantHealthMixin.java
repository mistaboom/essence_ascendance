package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.status.StatusInterceptionService;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect")
public abstract class StatusInstantHealthMixin {
    @WrapMethod(method = "applyInstantenousEffect")
    private void essenceAscendance$instantHealth(Entity direct, Entity owner, LivingEntity target, int amplifier,
                                                 double potency, Operation<Void> original) {
        StatusInterceptionService.instant((MobEffect)(Object)this, direct, owner, target, amplifier, potency,
                () -> original.call(direct, owner, target, amplifier, potency));
    }
}
