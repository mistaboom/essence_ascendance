package com.mistaboom.essence_ascendance.movement;

/** Pure shared launch math. Generated parameters supply strength; native attributes supply the baseline. */
public final class MovementImpulseMath {
    /** Vanilla entity-motion packet clips each component to this transport limit. Not balance tuning. */
    public static final double MAX_PACKET_COMPONENT = 3.9;
    /** Native Elytra horizontal retention and firework acceleration constants; these mirror vanilla mechanics, not mod tuning. */
    private static final double NATIVE_ELYTRA_HORIZONTAL_DRAG = 0.99;
    private static final double NATIVE_FIREWORK_LOOK_ACCELERATION = 0.1;
    private static final double NATIVE_FIREWORK_TARGET_SPEED = 1.5;
    private static final double NATIVE_FIREWORK_PULL = 0.5;
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
    /**
     * A Vector Boost is firework-class rather than jump-class. Vanilla firework acceleration converges on a
     * look-directed cruise velocity; one skill burst supplies the missing look-axis velocity immediately while
     * preserving perpendicular glide momentum. Generated power scales that native target, while rank scaling
     * is free to improve recharge instead of making each burst harder to control.
     */
    public static Velocity vectorBoost(Velocity before, Velocity look, double rocketSpeedBonus) {
        double length = Math.sqrt(look.x() * look.x() + look.y() * look.y() + look.z() * look.z());
        if (length == 0) return before;
        double lx = look.x() / length, ly = look.y() / length, lz = look.z() / length;
        double nativeCruise = NATIVE_FIREWORK_TARGET_SPEED + NATIVE_FIREWORK_LOOK_ACCELERATION / NATIVE_FIREWORK_PULL;
        double targetSpeed = nativeCruise * Math.sqrt(1 + Math.max(0, rocketSpeedBonus));
        double along = before.x() * lx + before.y() * ly + before.z() * lz;
        double missing = Math.max(0, targetSpeed - along);
        if (missing == 0) return before;
        return bounded(before.x() + lx * missing, before.y() + ly * missing, before.z() + lz * missing);
    }
    /** Restore only the generated fraction of vanilla's horizontal Elytra drag; pitch/lift and vertical physics stay native. */
    public static Velocity essenceWingsGlide(Velocity nativeVelocity, double dragCompensation) {
        double fraction = Math.clamp(dragCompensation, 0, 1);
        double desiredRetention = NATIVE_ELYTRA_HORIZONTAL_DRAG
                + (1 - NATIVE_ELYTRA_HORIZONTAL_DRAG) * fraction;
        double recovery = desiredRetention / NATIVE_ELYTRA_HORIZONTAL_DRAG;
        return bounded(nativeVelocity.x() * recovery, nativeVelocity.y(), nativeVelocity.z() * recovery);
    }
    /**
     * Jetpack thrust adds acceleration to the existing velocity; it never zeros descent first. Vertical power is
     * measured against live gravity. Horizontal steering reuses the player's resolved Abilities#flyingSpeed as
     * its native acceleration baseline, so the existing Flight Speed equipment bonus remains the one speed route.
     * Direction is normalized so diagonal input cannot exceed straight-line acceleration.
     */
    public static Velocity jetpackThrust(Velocity before, double nativeGravity, double nativeJumpPower,
                                         double thrustGravityMultiplier, double directionX, double directionZ,
                                         double resolvedFlightSpeed) {
        double gravity = Math.max(0, nativeGravity);
        double multiplier = Math.max(1, thrustGravityMultiplier);
        double acceleration = gravity * multiplier;
        double maximumAscent = Math.max(0, nativeJumpPower) * Math.sqrt(multiplier);
        double ceiling = Math.max(before.y(), maximumAscent);
        double y = Math.min(before.y() + acceleration, ceiling);

        double x = before.x();
        double z = before.z();
        double directionLength = Math.hypot(directionX, directionZ);
        double lateralAcceleration = Math.max(0, resolvedFlightSpeed);
        if (directionLength > 0 && lateralAcceleration > 0) {
            x += directionX / directionLength * lateralAcceleration;
            z += directionZ / directionLength * lateralAcceleration;
        }
        return bounded(x, y, z);
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
