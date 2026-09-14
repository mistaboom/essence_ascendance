package com.mistaboom.essence_ascendance.config;

/** Real transfer limits; Pure State is deliberately binary and has no invented rank magnitude. */
public record StatusBalanceSettings(int mirrorCooldownTicks, int mirrorMaximumDurationTicks,
                                    int mirrorMaximumAmplifier) {
    public StatusBalanceSettings {
        integer("mirrorCooldownTicks", mirrorCooldownTicks, 20, 72_000);
        integer("mirrorMaximumDurationTicks", mirrorMaximumDurationTicks, 1, 72_000);
        integer("mirrorMaximumAmplifier", mirrorMaximumAmplifier, 0, 10);
    }
    public static StatusBalanceSettings defaults() { return new StatusBalanceSettings(600, 1200, 4); }
    public void validate() { new StatusBalanceSettings(mirrorCooldownTicks, mirrorMaximumDurationTicks, mirrorMaximumAmplifier); }
    private static void integer(String field, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum)
            throw new IllegalArgumentException("effects.status." + field + " must be between " + minimum + " and " + maximum);
    }
}
