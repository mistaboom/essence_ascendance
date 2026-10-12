package com.mistaboom.essence_ascendance.gathering;

import net.minecraft.world.phys.Vec3;

/** Bounds added launch force, never existing momentum. Predicts native air drag and a return to launch elevation. */
public final class MobLaunchEnvelope {
    public static final double DISTANCE_FRACTION = .9;
    private static final int FORECAST_TICKS = 320;
    private MobLaunchEnvelope() { }

    public static Vec3 limit(Vec3 offset, Vec3 velocity, Vec3 force, double gravity, boolean noGravity, double radius) {
        if (!Double.isFinite(radius) || radius <= 0 || !Double.isFinite(force.lengthSqr())) return Vec3.ZERO;
        double horizontalDrag = noGravity ? .98 : .91;
        gravity = noGravity ? 0 : Math.max(0, gravity);
        if (within(offset, velocity.add(force), gravity, horizontalDrag, radius)) return force;
        // A pre-existing launch outside the envelope is not erased or amplified further.
        if (!within(offset, velocity, gravity, horizontalDrag, radius)) return Vec3.ZERO;
        double low = 0, high = 1;
        for (int i = 0; i < 18; i++) {
            double mid = (low + high) * .5;
            if (within(offset, velocity.add(force.scale(mid)), gravity, horizontalDrag, radius)) low = mid;
            else high = mid;
        }
        return force.scale(low);
    }

    public static double maximumDistance(Vec3 offset, Vec3 velocity, double gravity, double horizontalDrag) {
        double x = offset.x, y = offset.y, z = offset.z;
        double vx = velocity.x, vy = velocity.y, vz = velocity.z;
        double floor = Math.min(0, offset.y), maximum = offset.lengthSqr();
        for (int i = 0; i < FORECAST_TICKS; i++) {
            x += vx; y += vy; z += vz;
            // Flight below the holder's launch elevation normally meets terrain. This is a
            // launch/apex budget, not an artificial midair leash or a guarantee over void terrain.
            if (y < floor && vy < 0) { y = floor; vy = 0; }
            maximum = Math.max(maximum, x * x + y * y + z * z);
            vx *= horizontalDrag; vz *= horizontalDrag; vy = (vy - gravity) * .98;
        }
        return Math.sqrt(maximum);
    }

    private static boolean within(Vec3 offset, Vec3 velocity, double gravity, double horizontalDrag, double radius) {
        return Double.isFinite(velocity.lengthSqr())
                && maximumDistance(offset, velocity, gravity, horizontalDrag) <= radius;
    }
}
