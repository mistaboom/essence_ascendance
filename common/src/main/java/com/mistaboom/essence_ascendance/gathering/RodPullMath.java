package com.mistaboom.essence_ascendance.gathering;

import com.mistaboom.essence_ascendance.config.FishingRodBalanceSettings;
import net.minecraft.world.phys.Vec3;

/** Added force only: no velocity assignment, teleport, hold timeout or release braking. */
public final class RodPullMath {
    private RodPullMath() { }
    public static Vec3 anchorForce(Vec3 toward, Vec3 velocity, Vec3 aim, FishingRodBalanceSettings.Tier tier,
            int poweredTicks, FishingRodBalanceSettings policy, double gravity) {
        Vec3 force = force(toward, velocity, aim, tier, poweredTicks, policy, 1, 0);
        if (toward.y > 0) force = force.add(0, Math.min(tier.maximumForce(), Math.max(0, gravity)), 0);
        double limit = forceLimit(tier, poweredTicks, policy);
        double length = force.length();
        return length > limit ? force.scale(limit / length) : force;
    }
    public static Vec3 force(Vec3 toward, Vec3 velocity, Vec3 aim, FishingRodBalanceSettings.Tier tier,
            int poweredTicks, FishingRodBalanceSettings policy, double strength, double resistance) {
        return force(toward, velocity, aim, tier, poweredTicks, policy, strength, resistance, 1);
    }
    /** Personalized perks change only the opening impulse and its speed allowance. */
    public static Vec3 force(Vec3 toward, Vec3 velocity, Vec3 aim, FishingRodBalanceSettings.Tier tier,
            int poweredTicks, FishingRodBalanceSettings policy, double strength, double resistance, double openingMultiplier) {
        return force(toward, velocity, aim, tier, poweredTicks, policy, strength, resistance, openingMultiplier,
                1, toward.normalize(), toward.normalize());
    }
    /** A target's opening frame stays fixed while it moves. Capture only unintended closing motion;
     * look-directed launches and the velocity present before the pull remain exempt. */
    public static Vec3 entityForce(Vec3 toward, Vec3 velocity, Vec3 aim, FishingRodBalanceSettings.Tier tier,
            int poweredTicks, FishingRodBalanceSettings policy, double strength, double resistance,
            double openingMultiplier, double steeringMultiplier, OpeningFrame frame) {
        Vec3 force = entityLaunchForce(toward, velocity, aim, tier, poweredTicks, policy, strength, resistance,
                openingMultiplier, steeringMultiplier, frame);
        return captureEntityForce(toward, velocity, aim, tier, poweredTicks, policy, strength, resistance, openingMultiplier, frame, force);
    }
    public static Vec3 entityLaunchForce(Vec3 toward, Vec3 velocity, Vec3 aim, FishingRodBalanceSettings.Tier tier,
            int poweredTicks, FishingRodBalanceSettings policy, double strength, double resistance,
            double openingMultiplier, double steeringMultiplier, OpeningFrame frame) {
        if (!Double.isFinite(toward.lengthSqr()) || !Double.isFinite(velocity.lengthSqr()) || toward.lengthSqr() < 1e-14)
            return Vec3.ZERO;
        if (frame == null) frame = new OpeningFrame(toward, velocity);
        Vec3 steeringAxis = frame.toward().normalize();
        Vec3 reference = steeringAxis;
        Vec3 horizontalReference = new Vec3(reference.x, 0, reference.z);
        double draw = toward.dot(horizontalReference) > 0
                ? Math.clamp((toward.horizontalDistance() - policy.settlingDistance()) / policy.settlingDistance(), 0, 1) : 0;
        reference = horizontalReference.scale(draw).add(0, reference.y, 0);
        return force(toward, velocity, aim, tier, poweredTicks, policy, strength, resistance,
                openingMultiplier, steeringMultiplier, reference, steeringAxis);
    }
    public static Vec3 captureEntityForce(Vec3 toward, Vec3 velocity, Vec3 aim, FishingRodBalanceSettings.Tier tier,
            int poweredTicks, FishingRodBalanceSettings policy, double strength, double resistance,
            double openingMultiplier, OpeningFrame frame, Vec3 force) {
        if (frame == null) frame = new OpeningFrame(toward, velocity);
        double control = Math.max(0, strength) * (1 - Math.clamp(resistance, 0, 1));
        double horizontalDistance = toward.horizontalDistance();
        if (control <= 0 || horizontalDistance < 1e-7 || !Double.isFinite(velocity.lengthSqr())) return force;
        Vec3 inward = new Vec3(toward.x / horizontalDistance, 0, toward.z / horizontalDistance);
        double closing = velocity.dot(inward);
        double protectedSpeed = Math.max(0, frame.velocity().dot(inward));
        double opening = openingWeight(poweredTicks, policy);
        Vec3 look = aim != null && Double.isFinite(aim.lengthSqr()) ? aim.normalize() : Vec3.ZERO;
        double aimedSpeed = Math.max(0, look.dot(inward)) * tier.reelSpeed() * openingMultiplier * opening;
        double speedAllowance = 1 + (Math.max(1, openingMultiplier) - 1) * opening;
        double desired = protectedSpeed + aimedSpeed
                + Math.min(tier.reelSpeed() * speedAllowance, Math.max(0, horizontalDistance - policy.settlingDistance()) * .25);
        double addedClosing = force.dot(inward);
        double excess = Math.max(0, closing + addedClosing - desired);
        // First trim the new inward acceleration, then use bounded held force to settle the
        // closing speed already contributed by this pull. Release never applies this correction.
        double trim = Math.min(Math.max(0, addedClosing), excess);
        force = force.subtract(inward.scale(trim));
        double limit = Math.max(forceLimit(tier, poweredTicks, policy),
                openingImpulse(tier, policy) * Math.max(1, openingMultiplier) * opening) * control;
        double brake = Math.min(excess - trim, limit);
        force = force.subtract(inward.scale(brake));
        double length = force.length();
        return length > limit ? force.scale(limit / length) : force;
    }
    public record OpeningFrame(Vec3 toward, Vec3 velocity) { }

    private static Vec3 force(Vec3 toward, Vec3 velocity, Vec3 aim, FishingRodBalanceSettings.Tier tier,
            int poweredTicks, FishingRodBalanceSettings policy, double strength, double resistance,
            double openingMultiplier, double steeringMultiplier, Vec3 reference, Vec3 steeringAxis) {
        double distance = toward.length();
        if (!Double.isFinite(distance) || distance < 1e-7 || !Double.isFinite(velocity.lengthSqr())) return Vec3.ZERO;
        if (!Double.isFinite(openingMultiplier) || openingMultiplier < 1) openingMultiplier = 1;
        Vec3 inward = toward.scale(1 / distance);
        Vec3 direction = inward;
        double opening = openingWeight(poweredTicks, policy);
        if (opening > 0 && aim != null && Double.isFinite(aim.lengthSqr())) {
            Vec3 look = aim.normalize();
            Vec3 side = look.subtract(steeringAxis.scale(look.dot(steeringAxis)));
            // Camera-relative launch steering, proportional to the look angle. Keep some inward
            // draw even when looking away, then hand off to a straight reel after the opening burst.
            double steering = policy.steeringFraction() * opening;
            double gain = Math.max(1, steeringMultiplier);
            steering = steering * gain / (1 - steering + steering * gain);
            direction = reference.scale(1 - steering).add(side.scale(steering)).normalize();
        }
        double ease = Math.clamp(distance / policy.settlingDistance(), 0, 1);
        double ramp = Math.clamp((poweredTicks + 1.0) / policy.forceRampTicks(), 0, 1);
        // The opening kick tapers over the published ramp window, while sustained force ramps up.
        // Holding beyond that window preserves the existing reel acceleration and speed exactly.
        double acceleration = Math.max(openingImpulse(tier, policy) * opening * openingMultiplier,
                Math.min(tier.maximumForce(), tier.acceleration() * (poweredTicks + 1)) * ramp);
        acceleration *= ease * Math.max(0, strength) * (1 - Math.clamp(resistance, 0, 1));
        double speedAllowance = 1 + (openingMultiplier - 1) * opening;
        double desired = Math.min(tier.reelSpeed() * speedAllowance, distance * .25 * speedAllowance);
        double headroom = Math.max(0, desired - velocity.dot(direction));
        return direction.scale(Math.min(acceleration, headroom));
    }
    private static double forceLimit(FishingRodBalanceSettings.Tier tier, int poweredTicks, FishingRodBalanceSettings policy) {
        return Math.max(tier.maximumForce(), openingImpulse(tier, policy) * openingWeight(poweredTicks, policy));
    }
    private static double openingImpulse(FishingRodBalanceSettings.Tier tier, FishingRodBalanceSettings policy) {
        return Math.min(tier.reelSpeed(), tier.maximumForce() * policy.forceRampTicks());
    }
    private static double openingWeight(int poweredTicks, FishingRodBalanceSettings policy) {
        return Math.clamp(1 - (double) poweredTicks / policy.forceRampTicks(), 0, 1);
    }
    public static double skiingStep(Vec3 before, Vec3 after, boolean activeBlockPull, boolean waterSurface,
            boolean mounted, boolean discontinuity, double maximumStep) {
        if (before == null || !activeBlockPull || !waterSurface || mounted || discontinuity) return 0;
        Vec3 step = after.subtract(before);
        if (!Double.isFinite(step.lengthSqr()) || step.length() > maximumStep) return 0;
        return step.horizontalDistance();
    }
}
