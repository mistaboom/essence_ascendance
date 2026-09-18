package com.mistaboom.essence_ascendance.config;

import java.util.Objects;

/** Generated land-movement parameters. Fractions, native blocks, degrees and server ticks. */
public record MobilityBalanceSettings(RunningMomentum runningMomentum, MomentumVault momentumVault, Rush rush,
                                      ImpactControl impactControl, ChargedJump chargedJump,
                                      AirJump doubleJump, VectorJump vectorJump) {
    public MobilityBalanceSettings {
        Objects.requireNonNull(runningMomentum, "Missing Running Momentum balance; rebuild generated balance");
        Objects.requireNonNull(momentumVault, "Missing Momentum Vault balance; rebuild generated balance");
        Objects.requireNonNull(rush, "Missing Rush balance; rebuild generated balance");
        Objects.requireNonNull(impactControl, "Missing Impact Control balance; rebuild generated balance");
        Objects.requireNonNull(chargedJump, "Missing Charged Jump balance; rebuild generated balance");
        Objects.requireNonNull(doubleJump, "Missing Double Jump balance; rebuild generated balance");
        Objects.requireNonNull(vectorJump, "Missing Vector Jump balance; rebuild generated balance");
    }
    public record RunningMomentum(double maximumSpeedBonus, int buildTicks, int drainTicks, double sharpTurnDegrees) {
        public RunningMomentum {
            number("runningMomentum.maximumSpeedBonus", maximumSpeedBonus, 0, 16);
            ticks("runningMomentum.buildTicks", buildTicks); ticks("runningMomentum.drainTicks", drainTicks);
            number("runningMomentum.sharpTurnDegrees", sharpTurnDegrees, 1, 180);
        }
    }
    public record MomentumVault(double stepHeight, double minimumMomentum) {
        public MomentumVault {
            number("momentumVault.stepHeight", stepHeight, 0, 2);
            number("momentumVault.minimumMomentum", minimumMomentum, 0, 1);
        }
    }
    public record Rush(int durationTicks) {
        public Rush { ticks("rush.durationTicks", durationTicks); }
    }
    public record ImpactControl(double damageReduction) {
        public ImpactControl { number("impactControl.damageReduction", damageReduction, 0, 1); }
    }
    /** Height is an impulse-squared budget relative to the actual native jump, not fixed blocks. */
    public record ChargedJump(int chargeTicks, double heightBonus, double steeringBonus) {
        public ChargedJump {
            ticks("chargedJump.chargeTicks", chargeTicks);
            number("chargedJump.heightBonus", heightBonus, 0, 64);
            number("chargedJump.steeringBonus", steeringBonus, 0, 64);
        }
    }
    public record AirJump(double heightBonus, double steeringBonus) {
        public AirJump {
            number("doubleJump.heightBonus", heightBonus, 0, 64);
            number("doubleJump.steeringBonus", steeringBonus, 0, 64);
        }
    }
    public record VectorJump(double impulseBonus, double brakeFraction) {
        public VectorJump {
            number("vectorJump.impulseBonus", impulseBonus, 0, 64);
            number("vectorJump.brakeFraction", brakeFraction, 0, 1);
        }
    }
    /** Neutral schema fixtures only. Gameplay requires a validated, generated profile. */
    public static MobilityBalanceSettings defaults() {
        return new MobilityBalanceSettings(new RunningMomentum(0, 1, 1, 180),
                new MomentumVault(0, 1), new Rush(1), new ImpactControl(0),
                new ChargedJump(1, 0, 0), new AirJump(0, 0), new VectorJump(0, 0));
    }
    public void validate() {
        new RunningMomentum(runningMomentum.maximumSpeedBonus(), runningMomentum.buildTicks(),
                runningMomentum.drainTicks(), runningMomentum.sharpTurnDegrees());
        new MomentumVault(momentumVault.stepHeight(), momentumVault.minimumMomentum());
        new Rush(rush.durationTicks());
        new ImpactControl(impactControl.damageReduction());
        new ChargedJump(chargedJump.chargeTicks(), chargedJump.heightBonus(), chargedJump.steeringBonus());
        new AirJump(doubleJump.heightBonus(), doubleJump.steeringBonus());
        new VectorJump(vectorJump.impulseBonus(), vectorJump.brakeFraction());
    }
    private static void ticks(String key, int value) {
        if (value < 1 || value > 72_000)
            throw new IllegalArgumentException("effects.mobility." + key + " must be between 1 and 72000 ticks");
    }
    private static void number(String key, double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum)
            throw new IllegalArgumentException("effects.mobility." + key + " must be finite and between " + minimum + " and " + maximum);
    }
}
