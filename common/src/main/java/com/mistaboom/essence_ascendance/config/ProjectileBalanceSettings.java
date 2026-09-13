package com.mistaboom.essence_ascendance.config;

/** Validated launch snapshots and projectile control, owned by the generated runtime profile. */
public record ProjectileBalanceSettings(
        Profile arrow, Profile caster, int ricochets, double ricochetRadius,
        double ricochetDamageMultiplier, int penetrations, double piercingDamageMultiplier,
        double piercingShieldDamageMultiplier,
        int maximumImpacts, double maximumSpeed, Payload payload, Control control
) {
    public record Payload(int triggerBudget, double explosiveRadius, double explosiveDamageScale,
                          int rootDurationTicks, int rootMaxDurationTicks, double rootMovementTolerance,
                          int particleCount) {
        public Payload {
            integer("payload.triggerBudget", triggerBudget, 0, 32);
            range("payload.explosiveRadius", explosiveRadius, 0, 32);
            range("payload.explosiveDamageScale", explosiveDamageScale, 0, 4);
            integer("payload.rootDurationTicks", rootDurationTicks, 1, 200);
            integer("payload.rootMaxDurationTicks", rootMaxDurationTicks, rootDurationTicks, 400);
            range("payload.rootMovementTolerance", rootMovementTolerance, 0.001, 1);
            integer("payload.particleCount", particleCount, 0, 64);
        }
    }
    public record Control(double outerRadius, double innerRadius, double minimumSpeedFactor,
                          double responseExponent, int scanCadenceTicks, double swingRange,
                          double swingRadius, double swingHalfAngleDegrees, double readinessThreshold,
                          int swingBudget, double theftSpeedMultiplier, double theftTargetRange,
                          double theftAimConeDegrees, double theftTurnDegreesPerTick, int redirectBudget) {
        public Control {
            range("control.outerRadius", outerRadius, 0.5, 32);
            range("control.innerRadius", innerRadius, 0, outerRadius);
            if (innerRadius >= outerRadius) throw new IllegalArgumentException("Projectile control inner radius must be less than outer radius");
            range("control.minimumSpeedFactor", minimumSpeedFactor, 0.05, 1);
            range("control.responseExponent", responseExponent, 0.25, 8);
            integer("control.scanCadenceTicks", scanCadenceTicks, 1, 20);
            range("control.swingRange", swingRange, 0.5, 8);
            range("control.swingRadius", swingRadius, 0.01, 2);
            range("control.swingHalfAngleDegrees", swingHalfAngleDegrees, 1, 80);
            range("control.readinessThreshold", readinessThreshold, 0, 1);
            integer("control.swingBudget", swingBudget, 1, 64);
            range("control.theftSpeedMultiplier", theftSpeedMultiplier, 0.1, 2);
            range("control.theftTargetRange", theftTargetRange, 1, 128);
            range("control.theftAimConeDegrees", theftAimConeDegrees, 0, 60);
            range("control.theftTurnDegreesPerTick", theftTurnDegreesPerTick, 0, 45);
            // One ownership transfer is the explicit anti-ping-pong policy; zero disables theft.
            integer("control.redirectBudget", redirectBudget, 0, 1);
        }
    }
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
        java.util.Objects.requireNonNull(payload, "Missing projectile payload profile; rebuild generated balance");
        java.util.Objects.requireNonNull(control, "Missing projectile control profile; rebuild generated balance");
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
        double ricochetRadius = 8;
        double retention = 0.75;
        Profile caster = new Profile(20, 3, 80, 20, 10, 12);
        // Ratios share established path range, lifetime and retention references. They are
        // generator seeds, never tier/rank arrays or constants hidden in gameplay handlers.
        Payload payload = new Payload(1 + 2, ricochetRadius / 2, 1 - retention,
                caster.lifetimeTicks() / 2, caster.lifetimeTicks(), 1.0 / 16, 8);
        Control control = new Control(ricochetRadius * 1.5, caster.speed(), (1 - retention) / 2, retention,
                2, caster.speed(), 0.5, 35, 0.9, 16, 2 - retention,
                caster.acquisitionRange(), 35 + caster.acquisitionConeDegrees(), caster.turnDegreesPerTick() * 1.5, 1);
        return new ProjectileBalanceSettings(new Profile(160, 3, 200, 64, 10, 2),
                caster, 1, ricochetRadius, retention, 2, 0.80, 0.80, 64, 16, payload, control);
    }
    public int payloadTriggerBudget() { return payload.triggerBudget(); }
    public double explosiveRadius() { return payload.explosiveRadius(); }
    public double explosiveDamageScale() { return payload.explosiveDamageScale(); }
    public int rootDurationTicks() { return payload.rootDurationTicks(); }
    public int rootMaxDurationTicks() { return payload.rootMaxDurationTicks(); }
    public double rootMovementTolerance() { return payload.rootMovementTolerance(); }
    public int payloadParticleCount() { return payload.particleCount(); }
    public void validate() {
        new Payload(payload.triggerBudget(), payload.explosiveRadius(), payload.explosiveDamageScale(),
                payload.rootDurationTicks(), payload.rootMaxDurationTicks(), payload.rootMovementTolerance(), payload.particleCount());
        new Control(control.outerRadius(), control.innerRadius(), control.minimumSpeedFactor(),
                control.responseExponent(), control.scanCadenceTicks(), control.swingRange(), control.swingRadius(),
                control.swingHalfAngleDegrees(), control.readinessThreshold(), control.swingBudget(),
                control.theftSpeedMultiplier(), control.theftTargetRange(), control.theftAimConeDegrees(),
                control.theftTurnDegreesPerTick(), control.redirectBudget());
    }
    private static void integer(String field, int value, int min, int max) {
        if (value < min || value > max) throw new IllegalArgumentException("effects.projectiles." + field + " must be between " + min + " and " + max);
    }
    private static void range(String field, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException("effects.projectiles." + field + " must be finite and between " + min + " and " + max);
    }
    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Projectile settings must be finite");
        return Math.clamp(value, min, max);
    }
}
