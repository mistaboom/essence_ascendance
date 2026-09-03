package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Stored progression tier for one evolving Ascendance equipment ItemStack. */
public enum EquipmentTier {
    LATENT("latent", "Latent", -1, null),
    DORMANT("dormant", "Dormant", 0, AscendanceTiers.DORMANT),
    AWAKENED("awakened", "Awakened", 1, AscendanceTiers.AWAKENED),
    RESONANT("resonant", "Resonant", 2, AscendanceTiers.RESONANT),
    ASCENDANT("ascendant", "Ascendant", 3, AscendanceTiers.ASCENDANT),
    TRANSCENDENT("transcendent", "Transcendent", 4, AscendanceTiers.TRANSCENDENT);

    private final String serializedName;
    private final String displayName;
    private final int order;
    private final AscendanceTierDefinition ascendanceTier;

    EquipmentTier(String serializedName, String displayName, int order, @Nullable AscendanceTierDefinition ascendanceTier) {
        this.serializedName = serializedName;
        this.displayName = displayName;
        this.order = order;
        this.ascendanceTier = ascendanceTier;
    }

    public String serializedName() { return serializedName; }
    public String displayName() { return displayName; }
    public int order() { return order; }
    @Nullable public AscendanceTierDefinition ascendanceTier() { return ascendanceTier; }

    @Nullable
    public EquipmentTier next() {
        int index = ordinal() + 1;
        return index >= values().length ? null : values()[index];
    }

    public static EquipmentTier fromSerializedName(String value) {
        if (value == null || value.isBlank()) return LATENT;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (EquipmentTier tier : values()) {
            if (tier.serializedName.equals(normalized)) return tier;
        }
        return LATENT;
    }

    public static EquipmentTier fromAscendanceTier(AscendanceTierDefinition tier) {
        if (tier == null) return DORMANT;
        for (EquipmentTier equipmentTier : values()) {
            if (equipmentTier.ascendanceTier != null && equipmentTier.ascendanceTier.id().equals(tier.id())) {
                return equipmentTier;
            }
        }
        return DORMANT;
    }
}
