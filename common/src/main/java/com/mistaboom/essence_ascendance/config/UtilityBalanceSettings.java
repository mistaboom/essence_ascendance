package com.mistaboom.essence_ascendance.config;

import java.util.Objects;

/** Generated parameters for Utility gameplay. */
public record UtilityBalanceSettings(
        ThreatSense threatSense,
        HuntersLedger huntersLedger,
        Waylight waylight,
        RestfulMending restfulMending,
        MetabolicMending metabolicMending,
        MasterworkTempering masterworkTempering,
        VillagePatron villagePatron,
        BondedCompanion bondedCompanion,
        AlchemicalAmplification alchemicalAmplification,
        PotionRelay potionRelay
) {
    public UtilityBalanceSettings {
        Objects.requireNonNull(threatSense, "Missing Threat Sense balance; rebuild generated balance");
        Objects.requireNonNull(huntersLedger, "Missing Hunter's Ledger balance; rebuild generated balance");
        Objects.requireNonNull(waylight, "Missing Waylight balance; rebuild generated balance");
        Objects.requireNonNull(restfulMending, "Missing Restful Mending balance; rebuild generated balance");
        Objects.requireNonNull(metabolicMending, "Missing Metabolic Mending balance; rebuild generated balance");
        Objects.requireNonNull(masterworkTempering, "Missing Masterwork Tempering balance; rebuild generated balance");
        Objects.requireNonNull(villagePatron, "Missing Village Patron balance; rebuild generated balance");
        Objects.requireNonNull(bondedCompanion, "Missing Bonded Companion balance; rebuild generated balance");
        Objects.requireNonNull(alchemicalAmplification, "Missing Alchemical Amplification balance; rebuild generated balance");
        Objects.requireNonNull(potionRelay, "Missing Potion Relay balance; rebuild generated balance");
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

    /** Idle delay plus item-local repair throughput as a fraction of native maximum durability per second. */
    public record RestfulMending(int idleDelayTicks, double repairFractionPerSecond) {
        public RestfulMending {
            integer("restfulMending.idleDelayTicks", idleDelayTicks, 1, 72_000);
            number("restfulMending.repairFractionPerSecond", repairFractionPerSecond, 0, 1);
        }
    }

    /** Fraction of one item's native durability restored per intrinsic food point consumed. */
    public record MetabolicMending(double repairFractionPerFoodPoint) {
        public MetabolicMending {
            number("metabolicMending.repairFractionPerFoodPoint", repairFractionPerFoodPoint, 0, 1);
        }
    }

    /** Shared material durability budget, maximum extra durability, and maximum role-performance bonus while reinforced. */
    public record MasterworkTempering(double reinforcementFractionPerMaterialUnit,
                                      double maximumOverdurabilityFraction,
                                      double maximumPerformanceBonus) {
        public MasterworkTempering {
            number("masterworkTempering.reinforcementFractionPerMaterialUnit", reinforcementFractionPerMaterialUnit, 0, 1);
            number("masterworkTempering.maximumOverdurabilityFraction", maximumOverdurabilityFraction, 0, 1);
            number("masterworkTempering.maximumPerformanceBonus", maximumPerformanceBonus, 0, 1);
        }
    }

    /** Villager pricing and local exhausted-trade recovery cadence. */
    public record VillagePatron(double discountFraction, double restockRadiusBlocks, int restockIntervalTicks) {
        public VillagePatron {
            number("villagePatron.discountFraction", discountFraction, 0, 1);
            number("villagePatron.restockRadiusBlocks", restockRadiusBlocks, 0, 128);
            integer("villagePatron.restockIntervalTicks", restockIntervalTicks, 1, 72_000);
        }
    }

    /** Shared bonded-creature stat strength and distance that permits safe catch-up. */
    public record BondedCompanion(double statBonusFraction, double catchupDistanceBlocks) {
        public BondedCompanion {
            number("bondedCompanion.statBonusFraction", statBonusFraction, 0, 1);
            number("bondedCompanion.catchupDistanceBlocks", catchupDistanceBlocks, 0, 128);
        }
    }

    /** Maximum beneficial potion amplification plus the generated long-duration diminishing window for timed effects. */
    public record AlchemicalAmplification(double maximumBonusFraction, int diminishingWindowTicks) {
        public AlchemicalAmplification {
            number("alchemicalAmplification.maximumBonusFraction", maximumBonusFraction, 0, 1);
            integer("alchemicalAmplification.diminishingWindowTicks", diminishingWindowTicks, 1, 72_000);
        }
    }

    /** Ally relay radius, retained duration fraction, and deterministic target budget. */
    public record PotionRelay(double radiusBlocks, double durationFraction, int maximumTargets) {
        public PotionRelay {
            number("potionRelay.radiusBlocks", radiusBlocks, 0, 128);
            number("potionRelay.durationFraction", durationFraction, 0, 1);
            integer("potionRelay.maximumTargets", maximumTargets, 1, 64);
        }
    }

    /** Neutral schema fixture only. Gameplay requires the generated profile. */
    public static UtilityBalanceSettings defaults() {
        return new UtilityBalanceSettings(
                new ThreatSense(0),
                new HuntersLedger(1),
                new Waylight(0),
                new RestfulMending(1, 0),
                new MetabolicMending(0),
                new MasterworkTempering(0, 0, 0),
                new VillagePatron(0, 0, 1),
                new BondedCompanion(0, 0),
                new AlchemicalAmplification(0, 1),
                new PotionRelay(0, 0, 1));
    }

    public void validate() {
        new ThreatSense(threatSense.rangeBlocks());
        new HuntersLedger(huntersLedger.memoryTicks());
        new Waylight(waylight.searchRadiusBlocks());
        new RestfulMending(restfulMending.idleDelayTicks(), restfulMending.repairFractionPerSecond());
        new MetabolicMending(metabolicMending.repairFractionPerFoodPoint());
        new MasterworkTempering(masterworkTempering.reinforcementFractionPerMaterialUnit(),
                masterworkTempering.maximumOverdurabilityFraction(), masterworkTempering.maximumPerformanceBonus());
        new VillagePatron(villagePatron.discountFraction(), villagePatron.restockRadiusBlocks(), villagePatron.restockIntervalTicks());
        new BondedCompanion(bondedCompanion.statBonusFraction(), bondedCompanion.catchupDistanceBlocks());
        new AlchemicalAmplification(alchemicalAmplification.maximumBonusFraction(), alchemicalAmplification.diminishingWindowTicks());
        new PotionRelay(potionRelay.radiusBlocks(), potionRelay.durationFraction(), potionRelay.maximumTargets());
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
