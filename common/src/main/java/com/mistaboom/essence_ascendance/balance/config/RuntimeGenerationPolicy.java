package com.mistaboom.essence_ascendance.balance.config;

/** Explicit generation assumptions, never a table of item or tier stat values. */
public record RuntimeGenerationPolicy(double routineEncounterSeconds, double bossEncounterSeconds,
                                      double survivalWindowSeconds, double entryResourceEffort,
                                      double effortGrowth) {
    public RuntimeGenerationPolicy {
        require("routine_seconds", routineEncounterSeconds, 0.1, 300);
        require("boss_seconds", bossEncounterSeconds, 0.1, 3600);
        require("survival_seconds", survivalWindowSeconds, 0.1, 300);
        require("entry_resource_effort", entryResourceEffort, 1, 100000);
        require("effort_growth", effortGrowth, 1, 20);
    }
    public static RuntimeGenerationPolicy defaults() { return new RuntimeGenerationPolicy(4, 40, 10, 80, 3.5); }
    private static void require(String key, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException("generation." + key + " must be between " + min + " and " + max);
    }
}
