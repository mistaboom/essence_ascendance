package com.mistaboom.essence_ascendance.movement;

/** Pure shared launch math. Generated parameters supply strength; native attributes supply the baseline. */
public final class MovementImpulseMath {
    /** Vanilla entity-motion packet clips each component to this transport limit. Not balance tuning. */
    public static final double MAX_PACKET_COMPONENT = 3.9;
    private MovementImpulseMath() { }
    public record Velocity(double x, double y, double z) {
        public Velocity {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Non-finite movement velocity");
        }
        public double horizontalSpeed() { return Math.hypot(x, z); }
    }
    public static Velocity directionalJump(Velocity nativeJump, double directionX, double directionZ,
                                           double nativeSpeed, double heightBonus, double steeringBonus, double charge) {
        double fraction = Math.clamp(charge, 0, 1);
        double vertical = nativeJump.y() * Math.sqrt(1 + Math.max(0, heightBonus) * fraction);
        double length = Math.hypot(directionX, directionZ);
        if (length == 0) return bounded(nativeJump.x(), vertical, nativeJump.z());
        double speed = Math.max(nativeJump.horizontalSpeed(), Math.max(0, nativeSpeed) * (1 + Math.max(0, steeringBonus) * fraction));
        return bounded(directionX / length * speed, vertical, directionZ / length * speed);
    }
    public static Velocity vectorJump(Velocity before, Velocity look, double nativeJumpPower,
                                      double impulseBonus, double brakeFraction) {
        double power = Math.max(0, nativeJumpPower) * Math.sqrt(1 + Math.max(0, impulseBonus));
        double downward = Math.clamp(-look.y(), 0, 1);
        double retention = 1 - downward * Math.clamp(brakeFraction, 0, 1);
        // Looking down is a brake, never a downward kick that makes a fall more dangerous.
        double y = downward > 0 ? before.y() * retention : Math.max(before.y(), look.y() * power);
        return bounded(before.x() * retention + look.x() * power, y,
                before.z() * retention + look.z() * power);
    }
    /** Arresting descent starts a new fall. A partial brake removes only the corresponding kinetic burden. */
    public static float fallDistance(float current, double beforeY, double afterY) {
        if (!Float.isFinite(current) || current <= 0 || afterY >= 0) return 0;
        if (beforeY >= 0 || afterY <= beforeY) return current;
        double ratio = afterY / beforeY;
        return (float) (current * ratio * ratio);
    }
    public static float impactDamage(float amount, double reduction, double nativeFallMultiplier) {
        if (!Float.isFinite(amount) || amount <= 0) return amount;
        return (float) (amount * (1 - Math.clamp(reduction, 0, 1)) * Math.clamp(nativeFallMultiplier, 0, 1));
    }
    private static Velocity bounded(double x, double y, double z) {
        return new Velocity(Math.clamp(x, -MAX_PACKET_COMPONENT, MAX_PACKET_COMPONENT),
                Math.clamp(y, -MAX_PACKET_COMPONENT, MAX_PACKET_COMPONENT),
                Math.clamp(z, -MAX_PACKET_COMPONENT, MAX_PACKET_COMPONENT));
    }
}
