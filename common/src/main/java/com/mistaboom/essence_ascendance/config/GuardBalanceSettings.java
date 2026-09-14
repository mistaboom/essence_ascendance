package com.mistaboom.essence_ascendance.config;

import java.util.Objects;

/** Validated guard effects in the existing generated skill-effect profile. All scales are ratios. */
public record GuardBalanceSettings(Mobility mobility, Ram ram, Ward ward, StoredForce storedForce,
                                  Amplifier amplifier, PerfectGuard perfectGuard, Reprisal reprisal,
                                  Riposte riposte) {
    public GuardBalanceSettings {
        Objects.requireNonNull(mobility); Objects.requireNonNull(ram); Objects.requireNonNull(ward);
        Objects.requireNonNull(storedForce); Objects.requireNonNull(amplifier); Objects.requireNonNull(perfectGuard);
        Objects.requireNonNull(reprisal); Objects.requireNonNull(riposte);
    }

    public record Mobility(double slowdownRemoval, double stepHeight) {
        public Mobility {
            range("mobility.slowdownRemoval", slowdownRemoval, 0, 1);
            range("mobility.stepHeight", stepHeight, .6, 1.1);
        }
    }
    public record Ram(double minimumSpeed, double maximumSweep, int staggerTicks,
                      double staggerMovementMultiplier, double knockback, int contactLimit,
                      int repeatCooldownTicks) {
        public Ram {
            range("ram.minimumSpeed", minimumSpeed, .1, 1);
            range("ram.maximumSweep", maximumSweep, 1, 3);
            integer("ram.staggerTicks", staggerTicks, 1, 40);
            range("ram.staggerMovementMultiplier", staggerMovementMultiplier, .1, 1);
            range("ram.knockback", knockback, 0, 2);
            integer("ram.contactLimit", contactLimit, 1, 8);
            integer("ram.repeatCooldownTicks", repeatCooldownTicks, staggerTicks, 200);
        }
    }
    public record Ward(double preventedReflectionScale, double knockbackEchoScale, double knockbackEchoCap) {
        public Ward {
            range("ward.preventedReflectionScale", preventedReflectionScale, 0, 1);
            range("ward.knockbackEchoScale", knockbackEchoScale, 0, 2);
            range("ward.knockbackEchoCap", knockbackEchoCap, 0, 2);
        }
    }
    /** Positive blocks add converted force up to capacity and refresh the idle expiry. */
    public record StoredForce(double conversion, double capacity, int durationTicks,
                              double damageScale, double knockbackScale) {
        public StoredForce {
            range("storedForce.conversion", conversion, 0, 1);
            range("storedForce.capacity", capacity, 0, 1_024);
            integer("storedForce.durationTicks", durationTicks, 1, 1_200);
            range("storedForce.damageScale", damageScale, 0, 4);
            range("storedForce.knockbackScale", knockbackScale, 0, 1);
        }
    }
    /** Growth is added to a multiplier starting at one; perfect blocks promote to the maximum. */
    public record Amplifier(double perBlockGrowth, double maximumMultiplier, int durationTicks) {
        public Amplifier {
            range("amplifier.maximumMultiplier", maximumMultiplier, 1, 4);
            range("amplifier.perBlockGrowth", perBlockGrowth, 0, maximumMultiplier - 1);
            integer("amplifier.durationTicks", durationTicks, 1, 1_200);
        }
    }
    public record PerfectGuard(int windowTicks) {
        public PerfectGuard { integer("perfectGuard.windowTicks", windowTicks, 1, 10); }
    }
    public record Reprisal(double radius, double damageScale, int maximumTargets, int particleCount) {
        public Reprisal {
            range("reprisal.radius", radius, 0, 16);
            range("reprisal.damageScale", damageScale, 0, 1);
            integer("reprisal.maximumTargets", maximumTargets, 0, 16);
            integer("reprisal.particleCount", particleCount, 0, 32);
        }
    }
    /** Damage scale is added primary-melee damage; protection closes when that native attack returns. */
    public record Riposte(int durationTicks, double bonusReach, double damageScale, int protectionTicks) {
        public Riposte {
            integer("riposte.durationTicks", durationTicks, 1, 1_200);
            range("riposte.bonusReach", bonusReach, 0, 2);
            range("riposte.damageScale", damageScale, 0, 4);
            integer("riposte.protectionTicks", protectionTicks, 1, 2);
        }
    }

    public static GuardBalanceSettings defaults() {
        // Shared encounter/cadence seeds; generation resolves damage and all ranks procedurally.
        var projectiles = ProjectileBalanceSettings.defaults();
        double retained = projectiles.ricochetDamageMultiplier();
        int shortWindow = projectiles.caster().lifetimeTicks();
        double area = projectiles.ricochetRadius() / 2;
        return new GuardBalanceSettings(new Mobility(.85, 1), new Ram(.2, 2, 10, .3, .6, 3, 20),
                new Ward(1, .5, 1), new StoredForce(1 - retained, area * 2, shortWindow * 2, 1, .1),
                new Amplifier(1 - retained, 2, shortWindow * 3 / 2), new PerfectGuard(5),
                new Reprisal(area, 1 - retained, 6, projectiles.payloadParticleCount()),
                new Riposte(shortWindow * 5 / 4, retained, .35, 1));
    }

    public void validate() {
        // Records reject invalid values during construction and Gson record deserialization.
        new GuardBalanceSettings(mobility, ram, ward, storedForce, amplifier, perfectGuard, reprisal, riposte);
    }
    private static void integer(String field, int value, int min, int max) {
        if (value < min || value > max)
            throw new IllegalArgumentException("effects.guard." + field + " must be between " + min + " and " + max);
    }
    private static void range(String field, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException("effects.guard." + field + " must be finite and between " + min + " and " + max);
    }
}
