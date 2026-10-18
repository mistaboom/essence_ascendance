package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.world.item.ItemStack;

@FunctionalInterface
public interface EquipmentStatProvider {

    EquipmentStatProfile evaluate(ItemStack stack);

    default EquipmentWeaponCapabilities weaponCapabilities(ItemStack stack) {
        return EquipmentWeaponCapabilities.DEFAULT;
    }

    /** Explicit passive approval never overrides recognized combat/active-equipment roles. */
    default boolean passiveOffhand(ItemStack stack) { return false; }

    /** Declare component dependencies for component-sensitive roles/capabilities/passive approval. */
    default java.util.Set<net.minecraft.core.component.DataComponentType<?>> equipmentPolicyComponents() {
        return java.util.Set.of();
    }

    /** Immutable semantic projection, e.g. only the relevant fields of CUSTOM_DATA, excluding tint/lore. */
    default Object equipmentPolicyKey(ItemStack stack) { return null; }
}
