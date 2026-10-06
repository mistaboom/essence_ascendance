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
        EssenceBloom essenceBloom,
        VerdantStride verdantStride,
        Herdkeeper herdkeeper,
        AnimalGift animalGift,
        FishingInstinct fishingInstinct,
        FishersCall fishersCall,
        SalvagersCraft salvagersCraft,
        PocketNets pocketNets
) {
    public GatheringBalanceSettings {
        Objects.requireNonNull(toolInstinct, "Missing Tool Instinct balance; rebuild generated balance");
        Objects.requireNonNull(miningMomentum, "Missing Mining Momentum balance; rebuild generated balance");
        Objects.requireNonNull(naturesBoon, "Missing Nature's Boon balance; rebuild generated balance");
        Objects.requireNonNull(oreSight, "Missing Ore Sight balance; rebuild generated balance");
        Objects.requireNonNull(treasureSense, "Missing Treasure Sense balance; rebuild generated balance");
        Objects.requireNonNull(huntersStudy, "Missing Hunter's Study balance; rebuild generated balance");
        Objects.requireNonNull(essenceBloom, "Missing Essence Bloom balance; rebuild generated balance");
        Objects.requireNonNull(verdantStride, "Missing Verdant Stride balance; rebuild generated balance");
        Objects.requireNonNull(herdkeeper, "Missing Herdkeeper balance; rebuild generated balance");
        Objects.requireNonNull(animalGift, "Missing Animal Gift balance; rebuild generated balance");
        Objects.requireNonNull(fishingInstinct, "Missing Fishing Instinct balance; rebuild generated balance");
        Objects.requireNonNull(fishersCall, "Missing Fisher's Call balance; rebuild generated balance");
        Objects.requireNonNull(salvagersCraft, "Missing Salvager's Craft balance; rebuild generated balance");
        Objects.requireNonNull(pocketNets, "Missing Pocket Nets balance; rebuild generated balance");
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

    /** Null growth mode preserves the exact saved shape and random-tick behavior of older profiles. */
    public record VerdantStride(double radiusBlocks, int growthPulseTicks, double growthChance, Boolean boneMealGrowth) {
        public VerdantStride(double radiusBlocks, int growthPulseTicks, double growthChance) {
            this(radiusBlocks, growthPulseTicks, growthChance, null);
        }

        public boolean usesBoneMealGrowth() { return Boolean.TRUE.equals(boneMealGrowth); }

        public VerdantStride {
            number("verdantStride.radiusBlocks", radiusBlocks, 0, 128);
            integer("verdantStride.growthPulseTicks", growthPulseTicks, 1, 72_000);
            number("verdantStride.growthChance", growthChance, 0, 1);
        }
    }

    /** Native animal navigation gathers the herd; this multiplier accelerates only positive breeding cooldown. */
    public record Herdkeeper(double radiusBlocks, double breedingRecoveryMultiplier) {
        public Herdkeeper {
            number("herdkeeper.radiusBlocks", radiusBlocks, 0, 128);
            number("herdkeeper.breedingRecoveryMultiplier", breedingRecoveryMultiplier, 1, 128);
        }
    }

    /** Well-fed adults periodically roll once for a factual, renewable biological product. */
    public record AnimalGift(double radiusBlocks, int giftPulseTicks, double giftChance) {
        public AnimalGift {
            number("animalGift.radiusBlocks", radiusBlocks, 0, 128);
            integer("animalGift.giftPulseTicks", giftPulseTicks, 1, 72_000);
            number("animalGift.giftChance", giftChance, 0, 1);
        }
    }

    /** Fishing timing scales native hook countdowns/windows; virtual Luck is realized stochastically per retrieval. */
    public record FishingInstinct(double biteSpeedMultiplier, double reelWindowMultiplier,
                                  double virtualLuckLevels) {
        public FishingInstinct {
            number("fishingInstinct.biteSpeedMultiplier", biteSpeedMultiplier, 1, 128);
            number("fishingInstinct.reelWindowMultiplier", reelWindowMultiplier, 1, 128);
            number("fishingInstinct.virtualLuckLevels", virtualLuckLevels, 0, 255);
        }
    }

    /** Consecutive catches build one shared-deadline shoal; generated values bound both speed and extra loot. */
    public record FishersCall(int catchesToFullShoal, int chainTimeoutTicks,
                              double maximumBiteSpeedMultiplier, double maximumExtraCatchChance) {
        public FishersCall {
            integer("fishersCall.catchesToFullShoal", catchesToFullShoal, 1, 1_024);
            integer("fishersCall.chainTimeoutTicks", chainTimeoutTicks, 1, 72_000);
            number("fishersCall.maximumBiteSpeedMultiplier", maximumBiteSpeedMultiplier, 1, 128);
            number("fishersCall.maximumExtraCatchChance", maximumExtraCatchChance, 0, 1);
        }
    }

    /** Grindstone salvage destroys the chosen equipment and realizes fractional material/experience recovery. */
    public record SalvagersCraft(double materialRecoveryFraction, double bonusExperienceFraction) {
        public SalvagersCraft {
            number("salvagersCraft.materialRecoveryFraction", materialRecoveryFraction, 0, 1);
            number("salvagersCraft.bonusExperienceFraction", bonusExperienceFraction, 0, 1);
        }
    }

    /** Passive swimming checks are deliberately pulsed; each success rolls the loaded fishing loot table. */
    public record PocketNets(int pulseTicks, double dropChance) {
        public PocketNets {
            integer("pocketNets.pulseTicks", pulseTicks, 1, 72_000);
            number("pocketNets.dropChance", dropChance, 0, 1);
        }
    }

    /** Neutral schema fixture only. Gameplay requires the generated profile. */
    public static GatheringBalanceSettings defaults() {
        return new GatheringBalanceSettings(new ToolInstinct(0),
                new MiningMomentum(0, 1, 1, 1), new NaturesBoon(0),
                new Survey(0), new Survey(0), new HuntersStudy(1, 0),
                new EssenceBloom(0, 0, 0),
                new VerdantStride(0, 1, 0), new Herdkeeper(0, 1),
                new AnimalGift(0, 1, 0), new FishingInstinct(1, 1, 0),
                new FishersCall(1, 1, 1, 0), new SalvagersCraft(0, 0),
                new PocketNets(1, 0));
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
        new VerdantStride(verdantStride.radiusBlocks(), verdantStride.growthPulseTicks(),
                verdantStride.growthChance(), verdantStride.boneMealGrowth());
        new Herdkeeper(herdkeeper.radiusBlocks(), herdkeeper.breedingRecoveryMultiplier());
        new AnimalGift(animalGift.radiusBlocks(), animalGift.giftPulseTicks(), animalGift.giftChance());
        new FishingInstinct(fishingInstinct.biteSpeedMultiplier(), fishingInstinct.reelWindowMultiplier(),
                fishingInstinct.virtualLuckLevels());
        new FishersCall(fishersCall.catchesToFullShoal(), fishersCall.chainTimeoutTicks(),
                fishersCall.maximumBiteSpeedMultiplier(), fishersCall.maximumExtraCatchChance());
        new SalvagersCraft(salvagersCraft.materialRecoveryFraction(), salvagersCraft.bonusExperienceFraction());
        new PocketNets(pocketNets.pulseTicks(), pocketNets.dropChance());
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
