package com.mistaboom.essence_ascendance.equipment;

/*
 * Native declaration used by first-party Essence Ascendance items.
 *
 * IMPORTANT:
 *
 * This interface is NOT the sole source of conduit information.
 *
 * EquipmentConduitRegistry resolves an ItemStack through registered
 * EquipmentConduitProvider instances.
 *
 * The built-in native provider recognizes this interface, while
 * future providers can grant conduits through:
 *
 * - item tags
 * - enchantments
 * - data components
 * - explicit mod integrations
 *
 * without requiring the target item's Java class to implement this
 * interface.
 */
public interface EquipmentConduitItem {

    EquipmentConduitType conduitType();
}