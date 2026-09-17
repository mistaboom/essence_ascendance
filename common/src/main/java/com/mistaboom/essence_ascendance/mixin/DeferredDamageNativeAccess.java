package com.mistaboom.essence_ascendance.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Preserve the pre-existing native hurt window around a delayed, already-mitigated payment. */
@Mixin(LivingEntity.class)
public interface DeferredDamageNativeAccess {
    @Accessor("lastHurt") float essenceAscendance$lastHurt();
    @Accessor("lastHurt") void essenceAscendance$lastHurt(float amount);
}
