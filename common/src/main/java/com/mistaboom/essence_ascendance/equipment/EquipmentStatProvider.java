package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.world.item.ItemStack;

@FunctionalInterface
public interface EquipmentStatProvider {

    EquipmentStatProfile evaluate(ItemStack stack);
}
