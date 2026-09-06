package com.mistaboom.essence_ascendance.equipment;

/*
 * Describes HOW an ItemStack is active, not WHAT broad kind of item it is.
 *
 * This deliberately replaces broad conduit categories with the minimum
 * context information needed by the resolver.
 */
public enum EquipmentActivationType {
    HELD,
    WORN,
    /** An actively used, functional defensive conduit; never a generic held passive. */
    GUARDING
}
