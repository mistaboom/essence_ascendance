package com.mistaboom.essence_ascendance.config;

import java.util.Objects;

/** Generated parameters for Utility information/sensing gameplay. */
public record UtilityBalanceSettings(
        ThreatSense threatSense,
        HuntersLedger huntersLedger,
        Waylight waylight
) {
    public UtilityBalanceSettings {
        Objects.requireNonNull(threatSense, "Missing Threat Sense balance; rebuild generated balance");
        Objects.requireNonNull(huntersLedger, "Missing Hunter's Ledger balance; rebuild generated balance");
        Objects.requireNonNull(waylight, "Missing Waylight balance; rebuild generated balance");
    }

    /** Server-authoritative radius for threat acquisition and danger preview. */
    public record ThreatSense(double rangeBlocks) {
        public ThreatSense { number("threatSense.rangeBlocks", rangeBlocks, 0, 128); }
    }

    /** How long a no-longer-active creature threat remains observed by Hunter's Ledger. */
    public record HuntersLedger(int memoryTicks) {
        public HuntersLedger { integer("huntersLedger.memoryTicks", memoryTicks, 1, 72_000); }
    }

    /** Radius searched for the locally riskiest viable spawn footing. */
    public record Waylight(double searchRadiusBlocks) {
        public Waylight { number("waylight.searchRadiusBlocks", searchRadiusBlocks, 0, 128); }
    }

    /** Neutral schema fixture only. Gameplay requires the generated profile. */
    public static UtilityBalanceSettings defaults() {
        return new UtilityBalanceSettings(new ThreatSense(0), new HuntersLedger(1), new Waylight(0));
    }

    public void validate() {
        new ThreatSense(threatSense.rangeBlocks());
        new HuntersLedger(huntersLedger.memoryTicks());
        new Waylight(waylight.searchRadiusBlocks());
    }

    private static void integer(String field, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum)
            throw new IllegalArgumentException("effects.utility." + field + " must be between " + minimum + " and " + maximum);
    }

    private static void number(String field, double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum)
            throw new IllegalArgumentException("effects.utility." + field + " must be finite and between " + minimum + " and " + maximum);
    }
}
