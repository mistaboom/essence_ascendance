package com.mistaboom.essence_ascendance.crucible;

import java.util.Locale;

/*
 * Persistent access policy for an Essence Crucible.
 *
 * PRIVATE is the only active policy in this tranche. TEAM and PUBLIC are
 * persisted now so changing policy later does not require another NBT shape.
 */
public enum EssenceCrucibleAccessMode {
    PRIVATE,
    TEAM,
    PUBLIC;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static EssenceCrucibleAccessMode fromSerializedName(String value) {
        if (value == null || value.isBlank()) {
            return PRIVATE;
        }

        for (EssenceCrucibleAccessMode mode : values()) {
            if (mode.serializedName().equalsIgnoreCase(value)) {
                return mode;
            }
        }

        /* Unknown/future values fail closed. */
        return PRIVATE;
    }
}
