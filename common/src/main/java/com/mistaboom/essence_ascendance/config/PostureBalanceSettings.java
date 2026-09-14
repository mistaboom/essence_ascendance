package com.mistaboom.essence_ascendance.config;

import java.util.Objects;

/** Ratios, ticks, blocks and degrees for the one shared server posture meter. */
public record PostureBalanceSettings(Movement movement, Evasive evasive, Bulwark bulwark, Adaptive adaptive) {
    public PostureBalanceSettings {
        Objects.requireNonNull(movement); Objects.requireNonNull(evasive);
        Objects.requireNonNull(bulwark); Objects.requireNonNull(adaptive);
    }
    public record Movement(double minimumDisplacement, double stillEnterDisplacement, double stillExitDisplacement,
                           double maximumDisplacement, double turnEnterDegrees, double turnExitDegrees,
                           int stableTicks, int intentTimeoutTicks, int forcedMotionQuietTicks) {
        public Movement {
            range("minimumDisplacement", minimumDisplacement, .001, .1);
            range("stillEnterDisplacement", stillEnterDisplacement, .0001, minimumDisplacement);
            range("stillExitDisplacement", stillExitDisplacement, stillEnterDisplacement, .2);
            range("maximumDisplacement", maximumDisplacement, 1, 8);
            range("turnEnterDegrees", turnEnterDegrees, .1, 5);
            range("turnExitDegrees", turnExitDegrees, turnEnterDegrees, 20);
            integer("stableTicks", stableTicks, 1, 20);
            integer("intentTimeoutTicks", intentTimeoutTicks, 5, 40);
            integer("forcedMotionQuietTicks", forcedMotionQuietTicks, 1, 40);
        }
    }
    public record Evasive(int buildTicks, int drainTicks, double maximumDodgeChance,
                          double successDrainFraction, double hitDrainFraction) {
        public Evasive {
            integer("evasive.buildTicks", buildTicks, 20, 1200); integer("evasive.drainTicks", drainTicks, 1, 200);
            range("evasive.maximumDodgeChance", maximumDodgeChance, 0, .75);
            range("evasive.successDrainFraction", successDrainFraction, .1, 1);
            range("evasive.hitDrainFraction", hitDrainFraction, .1, 1);
        }
    }
    public record Bulwark(int buildTicks, int drainTicks, double threatRange, double facingDegrees,
                          double maximumResistance, double knockbackThreshold, int maximumThreats) {
        public Bulwark {
            integer("bulwark.buildTicks", buildTicks, 20, 1200); integer("bulwark.drainTicks", drainTicks, 1, 200);
            range("bulwark.threatRange", threatRange, 1, 32); range("bulwark.facingDegrees", facingDegrees, 10, 85);
            range("bulwark.maximumResistance", maximumResistance, 0, .75);
            range("bulwark.knockbackThreshold", knockbackThreshold, .5, 1);
            integer("bulwark.maximumThreats", maximumThreats, 1, 64);
        }
    }
    public record Adaptive(int windowTicks, int maximumStacks, int minimumHits, double resistancePerStack) {
        public Adaptive {
            integer("adaptive.windowTicks", windowTicks, 20, 1200); integer("adaptive.maximumStacks", maximumStacks, 2, 16);
            integer("adaptive.minimumHits", minimumHits, 2, maximumStacks);
            range("adaptive.resistancePerStack", resistancePerStack, 0, .75 / (maximumStacks - minimumHits + 1));
        }
    }
    public static PostureBalanceSettings defaults() {
        return new PostureBalanceSettings(new Movement(.01, .003, .015, 3, 1, 3, 3, 10, 10),
                new Evasive(80, 20, .2, .5, .125), new Bulwark(80, 20, 12, 60, .2, 1, 32),
                new Adaptive(120, 5, 2, .05));
    }
    public void validate() { new PostureBalanceSettings(movement, evasive, bulwark, adaptive); }
    private static void integer(String key, int value, int min, int max) {
        if (value < min || value > max) throw new IllegalArgumentException("effects.posture." + key + " out of bounds");
    }
    private static void range(String key, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException("effects.posture." + key + " must be finite and bounded");
    }
}
