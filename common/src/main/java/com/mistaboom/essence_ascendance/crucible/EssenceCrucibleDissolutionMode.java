package com.mistaboom.essence_ascendance.crucible;

import java.util.Locale;

/**
 * Player-selectable scheduler used when a Crucible has multiple active input
 * lanes. The setting is stored on the physical Crucible because it controls
 * that machine's own lane selection rather than the player's shared reservoir.
 */
public enum EssenceCrucibleDissolutionMode {
    SMART_ROUND_ROBIN("smart_round_robin", "Smart Round Robin"),
    STRICT_ROUND_ROBIN("strict_round_robin", "Strict Round Robin"),
    SKIP_ROUND_ROBIN("skip_round_robin", "Skip Round Robin"),
    LOWEST_STORED("lowest_stored", "Lowest Stored"),
    HIGHEST_STORED("highest_stored", "Highest Stored");

    private final String serializedName;
    private final String displayName;

    EssenceCrucibleDissolutionMode(String serializedName, String displayName) {
        this.serializedName = serializedName;
        this.displayName = displayName;
    }

    public String serializedName() {
        return serializedName;
    }

    public String displayName() {
        return displayName;
    }

    public EssenceCrucibleDissolutionMode next() {
        EssenceCrucibleDissolutionMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static EssenceCrucibleDissolutionMode fromSerializedName(String value) {
        if (value == null || value.isBlank()) {
            return SMART_ROUND_ROBIN;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (EssenceCrucibleDissolutionMode mode : values()) {
            if (mode.serializedName.equals(normalized)) {
                return mode;
            }
        }
        return SMART_ROUND_ROBIN;
    }
}
