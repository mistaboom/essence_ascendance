package com.mistaboom.essence_ascendance.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MobEffectInstance.class)
public interface StatusEffectInstanceAccess {
    @Accessor("hiddenEffect") MobEffectInstance essenceAscendance$hiddenEffect();
    @Accessor("hiddenEffect") void essenceAscendance$hiddenEffect(MobEffectInstance hidden);
}
