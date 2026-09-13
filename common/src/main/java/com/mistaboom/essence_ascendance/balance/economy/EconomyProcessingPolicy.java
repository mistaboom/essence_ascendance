package com.mistaboom.essence_ascendance.balance.economy;

import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;

/** Separate semantic losses; carrier fabrication remains grade-specific. */
public record EconomyProcessingPolicy(int conversionEfficiencyBasisPoints,
                                      int carrierExtractionEfficiencyBasisPoints) {
    public EconomyProcessingPolicy {
        if (conversionEfficiencyBasisPoints < 1 || conversionEfficiencyBasisPoints > 10_000
                || carrierExtractionEfficiencyBasisPoints < 1 || carrierExtractionEfficiencyBasisPoints > 10_000)
            throw new IllegalArgumentException("Processing efficiencies must be in [1,10000]");
    }
    public static EconomyProcessingPolicy derive(BalanceSettings settings) {
        return new EconomyProcessingPolicy(Math.max(1, (int) Math.floor(10_000 * (1 - settings.conversionLossPressure()))),
                Math.max(1, (int) Math.floor(10_000 * (1 - .2 * settings.conversionLossPressure()))));
    }
    public static EconomyProcessingPolicy defaults() { return derive(BalanceSettings.defaults()); }
}
