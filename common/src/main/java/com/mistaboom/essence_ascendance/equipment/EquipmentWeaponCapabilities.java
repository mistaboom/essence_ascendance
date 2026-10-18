package com.mistaboom.essence_ascendance.equipment;

/** Independent hand occupancy and melee execution declarations; no inventory restrictions. */
public record EquipmentWeaponCapabilities(HandRequirement hands, MeleeExecution melee) {
    public enum HandRequirement { UNRESTRICTED, MAIN_HAND_TWO_HANDED }
    public enum MeleeExecution { NATIVE, ACTION }

    public static final EquipmentWeaponCapabilities DEFAULT = new EquipmentWeaponCapabilities(
            HandRequirement.UNRESTRICTED, MeleeExecution.NATIVE);

    public EquipmentWeaponCapabilities {
        java.util.Objects.requireNonNull(hands);
        java.util.Objects.requireNonNull(melee);
    }
}
