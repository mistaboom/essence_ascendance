package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.resources.ResourceLocation;

/*
 * First-party declaration only.
 *
 * Gameplay code should resolve ItemStacks through EquipmentStatProviderRegistry
 * rather than depending on this interface directly. This leaves third-party
 * items, tags, enchantments, data components, and explicit integrations free
 * to participate through additional providers.
 */
public interface EquipmentProfileItem {

    ResourceLocation equipmentProfileId();
}
