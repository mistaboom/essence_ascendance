package com.mistaboom.essence_ascendance.config;

import java.util.Objects;

/** Generated final-health routing parameters. Health and food are native points, not hearts/icons. */
public record VitalityDamageBalanceSettings(HungerWard hungerWard, StaggeredPain staggeredPain,
        DamageCeiling damageCeiling, MetabolicConversion metabolicConversion, PainPurge painPurge,
        Adrenaline adrenaline) {
    public VitalityDamageBalanceSettings {
        Objects.requireNonNull(hungerWard, "Missing Hunger Ward balance; rebuild generated balance");
        Objects.requireNonNull(staggeredPain, "Missing Staggered Pain balance; rebuild generated balance");
        Objects.requireNonNull(damageCeiling, "Missing Damage Ceiling balance; rebuild generated balance");
        Objects.requireNonNull(metabolicConversion, "Missing Metabolic Conversion balance; rebuild generated balance");
        Objects.requireNonNull(painPurge, "Missing Pain Purge balance; rebuild generated balance");
        Objects.requireNonNull(adrenaline, "Missing Adrenaline balance; rebuild generated balance");
        // A food -> healing -> food cycle must always lose resources, even under exact overrides.
        if (metabolicConversion.foodPointsPerOverflowHealth() * metabolicConversion.healthPerNutrition() >= 1)
            throw new IllegalArgumentException("Metabolic Conversion must have a lossy resource round trip");
    }
    public record HungerWard(double healthPerFoodPoint, double damageShare) {
        public HungerWard {
            number("hungerWard.healthPerFoodPoint", healthPerFoodPoint, 0, 1024);
            // Zero is a valid disabled/calibration endpoint; a full redirect is not this skill's contract.
            number("hungerWard.damageShare", damageShare, 0, Math.nextDown(1.0));
        }
    }
    public record StaggeredPain(int paymentTicks) {
        public StaggeredPain { integer("staggeredPain.paymentTicks", paymentTicks, 1, 72_000); }
    }
    /** One authoritative fraction: incoming HP taken AND prevented DMG charged as lost max HP. */
    public record DamageCeiling(double damageTakenFraction, int combatTimeoutTicks) {
        public DamageCeiling {
            number("damageCeiling.damageTakenFraction", damageTakenFraction, .01, 1);
            integer("damageCeiling.combatTimeoutTicks", combatTimeoutTicks, 1, 72_000);
        }
    }
    public record MetabolicConversion(double foodPointsPerOverflowHealth, double healthPerNutrition) {
        public MetabolicConversion {
            number("metabolicConversion.foodPointsPerOverflowHealth", foodPointsPerOverflowHealth, 0, 1024);
            number("metabolicConversion.healthPerNutrition", healthPerNutrition, 0, 1024);
        }
    }
    public record PainPurge(double queuePerHealing) {
        public PainPurge { number("painPurge.queuePerHealing", queuePerHealing, 0, 1); }
    }
    public record Adrenaline(double triggerHealthLossFraction, int durationTicks,
                             double movementSpeedBonus, double attackSpeedBonus, double knockbackResistance) {
        public Adrenaline {
            number("adrenaline.triggerHealthLossFraction", triggerHealthLossFraction, 0, 1);
            integer("adrenaline.durationTicks", durationTicks, 1, 72_000);
            number("adrenaline.movementSpeedBonus", movementSpeedBonus, 0, 10);
            number("adrenaline.attackSpeedBonus", attackSpeedBonus, 0, 10);
            number("adrenaline.knockbackResistance", knockbackResistance, 0, 1);
        }
    }
    /** Reference shape for diagnostics/tests; gameplay always uses the generated, validated server profile. */
    public static VitalityDamageBalanceSettings defaults() {
        return new VitalityDamageBalanceSettings(new HungerWard(1, .3), new StaggeredPain(200),
                new DamageCeiling(.75, 200), new MetabolicConversion(.5, .5),
                new PainPurge(1), new Adrenaline(.25, 100, .15, .2, .35));
    }
    public void validate() {
        new HungerWard(hungerWard.healthPerFoodPoint(), hungerWard.damageShare());
        new StaggeredPain(staggeredPain.paymentTicks());
        new DamageCeiling(damageCeiling.damageTakenFraction(), damageCeiling.combatTimeoutTicks());
        new MetabolicConversion(metabolicConversion.foodPointsPerOverflowHealth(), metabolicConversion.healthPerNutrition());
        new PainPurge(painPurge.queuePerHealing());
        new Adrenaline(adrenaline.triggerHealthLossFraction(), adrenaline.durationTicks(),
                adrenaline.movementSpeedBonus(), adrenaline.attackSpeedBonus(), adrenaline.knockbackResistance());
        new VitalityDamageBalanceSettings(hungerWard, staggeredPain, damageCeiling, metabolicConversion, painPurge, adrenaline);
    }
    private static void number(String field, double value, double low, double high) {
        if (!Double.isFinite(value) || value < low || value > high)
            throw new IllegalArgumentException("effects.vitality.damage." + field + " must be finite in [" + low + ", " + high + "]");
    }
    private static void integer(String field, int value, int low, int high) { number(field, value, low, high); }
}
