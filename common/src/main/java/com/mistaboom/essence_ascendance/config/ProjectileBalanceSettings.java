package com.mistaboom.essence_ascendance.config;

/** Launch profiles and bounded path defaults, owned by the existing server configuration. */
public record ProjectileBalanceSettings(
        Profile arrow, Profile caster, int ricochets, double ricochetRadius,
        double ricochetDamageMultiplier, int penetrations, double piercingDamageMultiplier,
        double piercingShieldDamageMultiplier,
        int maximumImpacts, double maximumSpeed
) {
    public record Profile(double range, double speed, int lifetimeTicks,
                          double acquisitionRange, double acquisitionConeDegrees, double turnDegreesPerTick) {
        public Profile {
            range = clamp(range, 1, 512); speed = clamp(speed, 0.1, 16);
            lifetimeTicks = Math.clamp(lifetimeTicks, 1, 2400);
            acquisitionRange = clamp(acquisitionRange, 0, range);
            acquisitionConeDegrees = clamp(acquisitionConeDegrees, 0, 30);
            turnDegreesPerTick = clamp(turnDegreesPerTick, 0, 45);
        }
    }
    public ProjectileBalanceSettings {
        java.util.Objects.requireNonNull(arrow); java.util.Objects.requireNonNull(caster);
        ricochets = Math.clamp(ricochets, 0, 16);
        ricochetRadius = clamp(ricochetRadius, 0, 32);
        ricochetDamageMultiplier = clamp(ricochetDamageMultiplier, 0, 1);
        penetrations = Math.clamp(penetrations, 0, 32);
        piercingDamageMultiplier = clamp(piercingDamageMultiplier, 0, 1);
        piercingShieldDamageMultiplier = clamp(piercingShieldDamageMultiplier, 0, 1);
        maximumImpacts = Math.clamp(maximumImpacts, 1, 256);
        maximumSpeed = clamp(maximumSpeed, 0.1, 16);
        caster = new Profile(caster.range(), Math.min(caster.speed(), maximumSpeed), caster.lifetimeTicks(),
                caster.acquisitionRange(), caster.acquisitionConeDegrees(), caster.turnDegreesPerTick());
    }
    public static ProjectileBalanceSettings defaults() {
        return new ProjectileBalanceSettings(new Profile(160, 3, 200, 64, 10, 2),
                new Profile(20, 3, 80, 20, 10, 12), 1, 8, 0.75, 2, 0.80, 0.80, 64, 16);
    }
    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Projectile settings must be finite");
        return Math.clamp(value, min, max);
    }
}
