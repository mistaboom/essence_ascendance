package com.mistaboom.essence_ascendance.config;

import java.util.Objects;

/** Generated parameters for the first Gathering mining branch. Fractions use 1 for 100%. */
public record GatheringBalanceSettings(
        ToolInstinct toolInstinct,
        MiningMomentum miningMomentum,
        NaturesBoon naturesBoon
) {
    public GatheringBalanceSettings {
        Objects.requireNonNull(toolInstinct, "Missing Tool Instinct balance; rebuild generated balance");
        Objects.requireNonNull(miningMomentum, "Missing Mining Momentum balance; rebuild generated balance");
        Objects.requireNonNull(naturesBoon, "Missing Nature's Boon balance; rebuild generated balance");
    }

    /** Maximum target-dependent speed bonus when the Ascendance tool fully outclasses the material. */
    public record ToolInstinct(double maximumLowerTierSpeedBonus) {
        public ToolInstinct {
            number("toolInstinct.maximumLowerTierSpeedBonus", maximumLowerTierSpeedBonus, 0, 16);
        }
    }

    /** Same-material breaks build the reservoir fastest; other uninterrupted materials use the generated fraction. */
    public record MiningMomentum(double maximumSpeedBonus, int buildBreaks,
                                 int chainTimeoutTicks, double crossMaterialBuildFraction) {
        public MiningMomentum {
            number("miningMomentum.maximumSpeedBonus", maximumSpeedBonus, 0, 16);
            integer("miningMomentum.buildBreaks", buildBreaks, 1, 1_024);
            integer("miningMomentum.chainTimeoutTicks", chainTimeoutTicks, 1, 72_000);
            number("miningMomentum.crossMaterialBuildFraction", crossMaterialBuildFraction, 0, 1);
        }
    }

    /** Chance per eligible natural substrate break; ore identity is selected from live pack evidence. */
    public record NaturesBoon(double dropChance) {
        public NaturesBoon { number("naturesBoon.dropChance", dropChance, 0, 1); }
    }

    /** Neutral schema fixture only. Gameplay requires the generated profile. */
    public static GatheringBalanceSettings defaults() {
        return new GatheringBalanceSettings(new ToolInstinct(0),
                new MiningMomentum(0, 1, 1, 1), new NaturesBoon(0));
    }

    public void validate() {
        new ToolInstinct(toolInstinct.maximumLowerTierSpeedBonus());
        new MiningMomentum(miningMomentum.maximumSpeedBonus(), miningMomentum.buildBreaks(),
                miningMomentum.chainTimeoutTicks(), miningMomentum.crossMaterialBuildFraction());
        new NaturesBoon(naturesBoon.dropChance());
    }

    private static void integer(String field, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum)
            throw new IllegalArgumentException("effects.gathering." + field + " must be between " + minimum + " and " + maximum);
    }

    private static void number(String field, double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum)
            throw new IllegalArgumentException("effects.gathering." + field + " must be finite and between " + minimum + " and " + maximum);
    }
}
