package com.mistaboom.essence_ascendance.pylon;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;

/** Stable operational identities; generated runtime data owns every numeric contribution. */
public enum EssenceFocusTier {
    DORMANT("dormant", "Dormant"), AWAKENED("awakened", "Awakened"),
    RESONANT("resonant", "Resonant"), ASCENDANT("ascendant", "Ascendant"),
    TRANSCENDENT("transcendent", "Transcendent");
    private final String serializedName;
    private final String displayName;
    EssenceFocusTier(String serializedName, String displayName) { this.serializedName=serializedName;this.displayName=displayName; }
    public String serializedName() { return serializedName; }
    public String displayName() { return displayName; }
    public EssencePylonContribution contribution() { return EssenceConfigManager.runtime().pylon(serializedName); }
    public static EssencePylonContribution emptyContribution() { return EssenceConfigManager.runtime().pylon("empty"); }
}
