package com.mistaboom.essence_ascendance.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Native attribute, Jump Boost and block jump-factor baseline; no copied vanilla balancing constants. */
@Mixin(LivingEntity.class)
public interface MovementAbilityNativeAccess {
    @Invoker("getJumpPower") float essenceAscendance$jumpPower();
}
