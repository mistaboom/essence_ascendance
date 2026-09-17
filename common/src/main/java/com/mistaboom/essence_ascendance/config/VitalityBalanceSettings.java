package com.mistaboom.essence_ascendance.config;

import java.util.Objects;

/** Server-generated Vitality mechanics. Fractions use 1 for 100%; food uses native food points. */
public record VitalityBalanceSettings(RisingRecovery risingRecovery, LifeSteal lifeSteal,
                                     FeastReflex feastReflex, InnerSustenance innerSustenance,
                                     VitalityDamageBalanceSettings damage, VitalityWardBalanceSettings wards) {
    /** Reference-only convenience constructor retained for existing invariant fixtures. */
    public VitalityBalanceSettings(RisingRecovery recovery, LifeSteal steal, FeastReflex feast, InnerSustenance inner) {
        this(recovery, steal, feast, inner, VitalityDamageBalanceSettings.defaults());
    }
    /** Reference-only fixture constructor; generated JSON must contain both complete subtrees. */
    public VitalityBalanceSettings(RisingRecovery recovery, LifeSteal steal, FeastReflex feast, InnerSustenance inner,
                                  VitalityDamageBalanceSettings damage) {
        this(recovery, steal, feast, inner, damage, VitalityWardBalanceSettings.defaults());
    }
    public VitalityBalanceSettings {
        Objects.requireNonNull(wards, "Missing Vitality ward balance; rebuild generated balance");
        Objects.requireNonNull(damage, "Missing Vitality damage routing balance; rebuild generated balance");
        Objects.requireNonNull(risingRecovery, "Missing Rising Recovery balance; rebuild generated balance");
        Objects.requireNonNull(lifeSteal, "Missing Life Steal balance; rebuild generated balance");
        Objects.requireNonNull(feastReflex, "Missing Feast Reflex balance; rebuild generated balance");
        Objects.requireNonNull(innerSustenance, "Missing Inner Sustenance balance; rebuild generated balance");
    }
    public record RisingRecovery(double maxSpeedBonus, double recoveryCurveExponent) {
        public RisingRecovery {
            number("risingRecovery.maxSpeedBonus", maxSpeedBonus, 0, 9);
            number("risingRecovery.recoveryCurveExponent", recoveryCurveExponent, 1, 8);
        }
    }
    public record LifeSteal(double baseHealingFraction, double perHitHealingFraction,
                            int maxChainHits, int chainTimeoutTicks) {
        public LifeSteal {
            number("lifeSteal.baseHealingFraction", baseHealingFraction, 0, 1);
            number("lifeSteal.perHitHealingFraction", perHitHealingFraction, 0, 1);
            integer("lifeSteal.maxChainHits", maxChainHits, 2, 100);
            integer("lifeSteal.chainTimeoutTicks", chainTimeoutTicks, 1, 72_000);
            number("lifeSteal.maximumHealingFraction", baseHealingFraction + (maxChainHits - 1) * perHitHealingFraction, 0, 1);
        }
    }
    public record FeastReflex(double useDurationMultiplier) {
        public FeastReflex { number("feastReflex.useDurationMultiplier", useDurationMultiplier, .05, 1); }
    }
    public record InnerSustenance(int combatTimeoutTicks, int hungerRecoveryIntervalTicks,
                                  int hungerPerRecovery, double saturationPerRecovery) {
        public InnerSustenance {
            integer("innerSustenance.combatTimeoutTicks", combatTimeoutTicks, 1, 72_000);
            integer("innerSustenance.hungerRecoveryIntervalTicks", hungerRecoveryIntervalTicks, 20, 72_000);
            integer("innerSustenance.hungerPerRecovery", hungerPerRecovery, 1, 20);
            number("innerSustenance.saturationPerRecovery", saturationPerRecovery, .01, 20);
        }
    }
    public static VitalityBalanceSettings defaults() {
        return new VitalityBalanceSettings(new RisingRecovery(1.5, 2), new LifeSteal(.04, .02, 5, 60),
                new FeastReflex(.25), new InnerSustenance(200, 160, 1, .5));
    }
    public void validate() {
        damage.validate();
        wards.validate();
        new RisingRecovery(risingRecovery.maxSpeedBonus(), risingRecovery.recoveryCurveExponent());
        new LifeSteal(lifeSteal.baseHealingFraction(), lifeSteal.perHitHealingFraction(), lifeSteal.maxChainHits(), lifeSteal.chainTimeoutTicks());
        new FeastReflex(feastReflex.useDurationMultiplier());
        new InnerSustenance(innerSustenance.combatTimeoutTicks(), innerSustenance.hungerRecoveryIntervalTicks(),
                innerSustenance.hungerPerRecovery(), innerSustenance.saturationPerRecovery());
    }
    private static void integer(String field, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) throw new IllegalArgumentException("effects.vitality." + field + " must be between " + minimum + " and " + maximum);
    }
    private static void number(String field, double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum)
            throw new IllegalArgumentException("effects.vitality." + field + " must be finite and between " + minimum + " and " + maximum);
    }
}
