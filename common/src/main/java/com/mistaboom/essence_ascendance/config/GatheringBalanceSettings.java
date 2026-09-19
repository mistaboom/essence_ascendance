package com.mistaboom.essence_ascendance.config;

import java.util.Objects;

/** Generated parameters for Gathering gameplay. Fractions use 1 for 100%. */
public record GatheringBalanceSettings(
        ToolInstinct toolInstinct,
        MiningMomentum miningMomentum,
        NaturesBoon naturesBoon,
        Survey oreSight,
        Survey treasureSense,
        HuntersStudy huntersStudy,
        EssenceBloom essenceBloom
) {
    public GatheringBalanceSettings {
        Objects.requireNonNull(toolInstinct, "Missing Tool Instinct balance; rebuild generated balance");
        Objects.requireNonNull(miningMomentum, "Missing Mining Momentum balance; rebuild generated balance");
        Objects.requireNonNull(naturesBoon, "Missing Nature's Boon balance; rebuild generated balance");
        Objects.requireNonNull(oreSight, "Missing Ore Sight balance; rebuild generated balance");
        Objects.requireNonNull(treasureSense, "Missing Treasure Sense balance; rebuild generated balance");
        Objects.requireNonNull(huntersStudy, "Missing Hunter's Study balance; rebuild generated balance");
        Objects.requireNonNull(essenceBloom, "Missing Essence Bloom balance; rebuild generated balance");
    }

    /** Maximum target-dependent speed bonus when the Ascendance tool fully outclasses the material. */
    public record ToolInstinct(double maximumLowerTierSpeedBonus) {
        public ToolInstinct { number("toolInstinct.maximumLowerTierSpeedBonus", maximumLowerTierSpeedBonus, 0, 16); }
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

    /** Server-authoritative survey radius. Scan cadence is an implementation/performance concern, not skill power. */
    public record Survey(double rangeBlocks) {
        public Survey { number("survey.rangeBlocks", rangeBlocks, 0, 128); }
    }

    /**
     * Repeated kills of the same entity type build toward an ordinary-loot virtual Looting bonus.
     * The generated maximum is an expected level; gameplay realizes its fractional part per native loot event.
     */
    public record HuntersStudy(int killsToFullStudy, double maximumVirtualLootingLevels) {
        public HuntersStudy {
            integer("huntersStudy.killsToFullStudy", killsToFullStudy, 1, 1_024);
            number("huntersStudy.maximumVirtualLootingLevels", maximumVirtualLootingLevels, 0, 255);
        }
    }

    /** A bloom converts the victim's native XP reward into Gathering Essence and additional XP. */
    public record EssenceBloom(double triggerChance, double essencePerExperiencePoint,
                               double bonusExperienceFraction) {
        public EssenceBloom {
            number("essenceBloom.triggerChance", triggerChance, 0, 1);
            number("essenceBloom.essencePerExperiencePoint", essencePerExperiencePoint, 0, 1_024);
            number("essenceBloom.bonusExperienceFraction", bonusExperienceFraction, 0, 16);
        }
    }

    /** Neutral schema fixture only. Gameplay requires the generated profile. */
    public static GatheringBalanceSettings defaults() {
        return new GatheringBalanceSettings(new ToolInstinct(0),
                new MiningMomentum(0, 1, 1, 1), new NaturesBoon(0),
                new Survey(0), new Survey(0), new HuntersStudy(1, 0),
                new EssenceBloom(0, 0, 0));
    }

    public void validate() {
        new ToolInstinct(toolInstinct.maximumLowerTierSpeedBonus());
        new MiningMomentum(miningMomentum.maximumSpeedBonus(), miningMomentum.buildBreaks(),
                miningMomentum.chainTimeoutTicks(), miningMomentum.crossMaterialBuildFraction());
        new NaturesBoon(naturesBoon.dropChance());
        new Survey(oreSight.rangeBlocks());
        new Survey(treasureSense.rangeBlocks());
        new HuntersStudy(huntersStudy.killsToFullStudy(), huntersStudy.maximumVirtualLootingLevels());
        new EssenceBloom(essenceBloom.triggerChance(), essenceBloom.essencePerExperiencePoint(),
                essenceBloom.bonusExperienceFraction());
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
