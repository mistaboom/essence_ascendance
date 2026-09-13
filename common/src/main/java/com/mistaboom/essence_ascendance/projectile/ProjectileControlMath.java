package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.config.ProjectileBalanceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Deterministic field response and a bounded swept weapon fan, shared by native execution and diagnostics. */
public final class ProjectileControlMath {
    private ProjectileControlMath() { }
    public static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    public static double factor(double distance, ProjectileBalanceSettings.Control tuning) {
        if (!Double.isFinite(distance) || distance >= tuning.outerRadius()) return 1;
        double proximity = Math.clamp((tuning.outerRadius() - distance) / (tuning.outerRadius() - tuning.innerRadius()), 0, 1);
        return 1 - (1 - tuning.minimumSpeedFactor()) * Math.pow(proximity, tuning.responseExponent());
    }
    /** Undo exactly the previous field factor before resolving this tick. Native inertia/gravity remain in the vector. */
    public static Vec3 velocity(Vec3 current, double previousFactor, double nextFactor, double maximumSpeed) {
        if (!finite(current) || !Double.isFinite(previousFactor) || previousFactor < 0.01 || previousFactor > 1
                || !Double.isFinite(nextFactor) || nextFactor < 0.01 || nextFactor > 1) return Vec3.ZERO;
        double speed = current.length();
        if (speed <= 0.000001) return current;
        double baseline = Math.min(maximumSpeed, speed / previousFactor);
        return current.scale(baseline * nextFactor / speed);
    }
    /** Advance native physics by the local time scale. Velocity scales with time; acceleration scales with time squared. */
    public static Vec3 afterPhysics(Vec3 input, Vec3 output, double inertia, double factor, double maximumSpeed) {
        if (!finite(input) || !finite(output) || !Double.isFinite(inertia) || inertia < 0 || inertia > 1
                || !Double.isFinite(factor) || factor < 0.01 || factor > 1) return Vec3.ZERO;
        if (factor == 1) return output;
        Vec3 slowedInertia = input.scale(inertia);
        Vec3 nativeAcceleration = output.subtract(slowedInertia);
        Vec3 reference = input.scale(Math.pow(inertia, factor) / factor).add(nativeAcceleration.scale(factor));
        double speed = reference.length();
        return speed > maximumSpeed && speed > 0.000001 ? reference.scale(maximumSpeed * factor / speed) : reference.scale(factor);
    }
    /** A successful melee return restores at least nominal launch speed, then applies its explicit bat impulse. */
    public static double redirectSpeed(Vec3 current, double dragFactor, double launchSpeed,
                                       double multiplier, double maximumSpeed) {
        if (!finite(current) || !Double.isFinite(dragFactor) || dragFactor < 0.01 || dragFactor > 1
                || !Double.isFinite(launchSpeed) || launchSpeed <= 0 || !Double.isFinite(multiplier) || multiplier <= 0
                || !Double.isFinite(maximumSpeed) || maximumSpeed <= 0) return 0;
        return Math.min(maximumSpeed, Math.max(current.length() / dragFactor, launchSpeed) * multiplier);
    }
    public static List<Vec3> fan(Vec3 look, double range, double radius, double halfAngle, int budget) {
        if (!finite(look) || look.lengthSqr() < 0.000001 || range <= 0 || radius <= 0 || budget < 1) return List.of();
        if (budget == 1) return List.of(look.normalize().scale(range));
        Vec3 forward = look.normalize(), right = forward.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 0.000001) right = new Vec3(1, 0, 0);
        right = right.normalize();
        double radians = Math.toRadians(halfAngle);
        int segments = Math.clamp((int) Math.ceil(2 * radians * range / radius), 2, budget);
        var rays = new ArrayList<Vec3>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double angle = -radians + 2 * radians * i / segments;
            rays.add(forward.scale(Math.cos(angle)).add(right.scale(Math.sin(angle))).scale(range));
        }
        return List.copyOf(rays);
    }
    public static boolean intersects(AABB bounds, Vec3 eye, Vec3 look, List<Vec3> offsets, double radius) {
        // Do not let inflation turn the shared hilt into a behind-player proximity defense.
        if (bounds.getCenter().subtract(eye).dot(look) <= 0) return false;
        AABB expanded = bounds.inflate(radius);
        return offsets.stream().anyMatch(ray -> ProjectileCollision.contact(expanded, eye, eye.add(ray)).isPresent());
    }
}
