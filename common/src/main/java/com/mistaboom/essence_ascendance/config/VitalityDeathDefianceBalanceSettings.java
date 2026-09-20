package com.mistaboom.essence_ascendance.config;

import java.util.Objects;

/** Generated death-defiance tuning. Fractions use 1 for 100%; cooldown is shared by the exclusive choice group. */
public record VitalityDeathDefianceBalanceSettings(SecondWind secondWind, SpiritWalk spiritWalk) {
    public VitalityDeathDefianceBalanceSettings {
        Objects.requireNonNull(secondWind, "Missing Second Wind balance; rebuild generated balance");
        Objects.requireNonNull(spiritWalk, "Missing Spirit Walk balance; rebuild generated balance");
    }

    public record SecondWind(int cooldownTicks, int recoveryTicks,
                             double recoveryHealthFraction, double strengthDamageBonus) {
        public SecondWind {
            ticks("secondWind.cooldownTicks", cooldownTicks);
            ticks("secondWind.recoveryTicks", recoveryTicks);
            fraction("secondWind.recoveryHealthFraction", recoveryHealthFraction, 16);
            fraction("secondWind.strengthDamageBonus", strengthDamageBonus, 16);
        }
    }

    public record SpiritWalk(int cooldownTicks, int durationTicks, double reformHealthFraction) {
        public SpiritWalk {
            ticks("spiritWalk.cooldownTicks", cooldownTicks);
            ticks("spiritWalk.durationTicks", durationTicks);
            fraction("spiritWalk.reformHealthFraction", reformHealthFraction, Math.nextDown(1.0));
        }
    }

    /** Neutral schema fixtures only; live values always come from the runtime balance generator. */
    public static VitalityDeathDefianceBalanceSettings defaults() {
        return new VitalityDeathDefianceBalanceSettings(new SecondWind(1, 1, 0, 0),
                new SpiritWalk(1, 1, 0));
    }

    public void validate() {
        new SecondWind(secondWind.cooldownTicks(), secondWind.recoveryTicks(),
                secondWind.recoveryHealthFraction(), secondWind.strengthDamageBonus());
        new SpiritWalk(spiritWalk.cooldownTicks(), spiritWalk.durationTicks(), spiritWalk.reformHealthFraction());
    }

    private static void ticks(String key, int value) {
        if (value < 1 || value > 72_000)
            throw new IllegalArgumentException("effects.vitality.deathDefiance." + key + " must be between 1 and 72000 ticks");
    }

    private static void fraction(String key, double value, double maximum) {
        if (!Double.isFinite(value) || value < 0 || value > maximum)
            throw new IllegalArgumentException("effects.vitality.deathDefiance." + key
                    + " must be finite and between 0 and " + maximum);
    }
}
