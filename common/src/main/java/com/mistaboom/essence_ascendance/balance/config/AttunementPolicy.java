package com.mistaboom.essence_ascendance.balance.config;

/** High-level policy in the existing commented balance TOML. Resolved profiles own runtime values. */
public record AttunementPolicy(double pace, double maximumAcceleration, double repetitionFloor,
                              double varietyStrength, int historyWindow, double earlyEffortFraction,
                              double onboardingEffortFraction, double breadthExponent) {
    public AttunementPolicy {
        range("pace", pace, .1, 10);
        range("maximum_acceleration", maximumAcceleration, 0, 4);
        range("repetition_floor", repetitionFloor, .01, 1);
        range("variety_strength", varietyStrength, 0, 1);
        range("history_window", historyWindow, 8, 256);
        range("early_effort_fraction", earlyEffortFraction, .1, 1);
        range("onboarding_effort_fraction", onboardingEffortFraction, .1, 1);
        range("breadth_exponent", breadthExponent, .25, 4);
    }
    public static AttunementPolicy defaults() { return new AttunementPolicy(1, 1, .15, .25, 64, .75, .5, 1); }
    private static void range(String key, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException("attunement." + key + " must be between " + min + " and " + max);
    }
}
