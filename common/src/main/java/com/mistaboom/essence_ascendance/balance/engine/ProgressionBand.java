package com.mistaboom.essence_ascendance.balance.engine;

/** External acquisition bands; ordinal order deliberately matches the five Ascendance tiers. */
public enum ProgressionBand {
    ENTRY, EARLY, MID, LATE, APEX;

    public static ProgressionBand at(int index) {
        return values()[Math.max(0, Math.min(values().length - 1, index))];
    }
}
