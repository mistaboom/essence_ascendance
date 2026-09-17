package com.mistaboom.essence_ascendance.neoforge.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Stack;

/** Native loader container remains consistent with the changed final health local for post-damage observers. */
@Mixin(LivingEntity.class)
public interface NeoForgeDamageContainerAccess {
    @Accessor(value = "damageContainers", remap = false) Stack<DamageContainer> essenceAscendance$damageContainers();
}
