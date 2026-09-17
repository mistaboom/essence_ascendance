package com.mistaboom.essence_ascendance.config;

import java.util.Objects;

/** Generated, rank-resolved ward parameters. Health is in native HP; fractions use 1 for 100%. */
public record VitalityWardBalanceSettings(SoulWard soulWard, DeepWard deepWard, ShatteringWard shatteringWard) {
    public VitalityWardBalanceSettings {
        Objects.requireNonNull(soulWard, "Missing Soul Ward balance; rebuild generated balance");
        Objects.requireNonNull(deepWard, "Missing Deep Ward balance; rebuild generated balance");
        Objects.requireNonNull(shatteringWard, "Missing Shattering Ward balance; rebuild generated balance");
    }
    public record SoulWard(double victimHealthFraction, double capacityHealthFraction, int durationTicks) {
        public SoulWard {
            number("soulWard.victimHealthFraction", victimHealthFraction, 1);
            number("soulWard.capacityHealthFraction", capacityHealthFraction, 1024);
            ticks("soulWard.durationTicks", durationTicks);
        }
    }
    public record DeepWard(double capacityBonusFraction, int combatTimeoutTicks, int decayTicks) {
        public DeepWard {
            number("deepWard.capacityBonusFraction", capacityBonusFraction, 1024);
            ticks("deepWard.combatTimeoutTicks", combatTimeoutTicks);
            ticks("deepWard.decayTicks", decayTicks);
        }
    }
    public record ShatteringWard(double radius, int maximumTargets, double knockback,
                                 double healingFractionPerSecond, int regenerationTicks) {
        public ShatteringWard {
            number("shatteringWard.radius", radius, 64);
            if (maximumTargets < 1 || maximumTargets > 64)
                throw new IllegalArgumentException("effects.vitality.wards.shatteringWard.maximumTargets must be between 1 and 64");
            number("shatteringWard.knockback", knockback, 1024);
            number("shatteringWard.healingFractionPerSecond", healingFractionPerSecond, 1024);
            ticks("shatteringWard.regenerationTicks", regenerationTicks);
        }
    }
    /** Neutral schema fixtures only; live values always come from the pack generator, never these placeholders. */
    public static VitalityWardBalanceSettings defaults() {
        return new VitalityWardBalanceSettings(new SoulWard(0, 0, 1), new DeepWard(0, 1, 1),
                new ShatteringWard(0, 1, 0, 0, 1));
    }
    public void validate() {
        new SoulWard(soulWard.victimHealthFraction(), soulWard.capacityHealthFraction(), soulWard.durationTicks());
        new DeepWard(deepWard.capacityBonusFraction(), deepWard.combatTimeoutTicks(), deepWard.decayTicks());
        new ShatteringWard(shatteringWard.radius(), shatteringWard.maximumTargets(), shatteringWard.knockback(),
                shatteringWard.healingFractionPerSecond(), shatteringWard.regenerationTicks());
    }
    private static void number(String key, double value, double maximum) {
        if (!Double.isFinite(value) || value < 0 || value > maximum)
            throw new IllegalArgumentException("effects.vitality.wards." + key + " must be finite and between 0 and " + maximum);
    }
    private static void ticks(String key, int value) {
        if (value < 1 || value > 72_000)
            throw new IllegalArgumentException("effects.vitality.wards." + key + " must be between 1 and 72000 ticks");
    }
}
