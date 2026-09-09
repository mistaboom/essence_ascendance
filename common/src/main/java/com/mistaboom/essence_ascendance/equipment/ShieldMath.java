package com.mistaboom.essence_ascendance.equipment;

/** Pure, shared shield arithmetic. Inputs are percentages, never already-converted fractions. */
public final class ShieldMath {
    private ShieldMath() {}

    public static double percent(double value) {
        return Double.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0;
    }

    public static double ordinaryPercent(double nativeReflection, double shieldInvestment, double armorInvestment) {
        return saturatedAdd(nonnegative(nativeReflection), Math.max(nonnegative(shieldInvestment), nonnegative(armorInvestment)));
    }

    public static double blockedPercent(double nativeReflection, double invested, double amplification) {
        return saturatedMultiply(saturatedAdd(nonnegative(nativeReflection), nonnegative(invested)), nonnegative(amplification));
    }

    public static double reflectedPortion(float amount, double reflectionPercent) {
        if (!Float.isFinite(amount) || amount <= 0 || !Double.isFinite(reflectionPercent) || reflectionPercent <= 0) return 0;
        return saturatedMultiply(amount / 100.0D, reflectionPercent);
    }

    private static double nonnegative(double value) {
        return Double.isNaN(value) || value <= 0 ? 0 : Math.min(Double.MAX_VALUE, value);
    }

    private static double saturatedAdd(double left, double right) {
        return left > Double.MAX_VALUE - right ? Double.MAX_VALUE : left + right;
    }

    private static double saturatedMultiply(double left, double right) {
        if (left <= 0 || right <= 0) return 0;
        return left > Double.MAX_VALUE / right ? Double.MAX_VALUE : left * right;
    }

    public static int disableTicks(int originalTicks, double recoveryPercent, int minimumTicks) {
        if (originalTicks <= 0) return 0;
        return Math.min(originalTicks, Math.max(Math.max(1, minimumTicks),
                (int) Math.ceil(originalTicks * (1.0 - percent(recoveryPercent) / 100.0))));
    }

    /**
     * Converts Guard Readiness into Minecraft's whole-tick shield activation
     * threshold. Ceiling matches the guard-break reducer and never grants a
     * faster activation than the resolved percentage has actually earned.
     */
    public static int raiseDelayTicks(int originalTicks, double readinessPercent, int minimumTicks) {
        if (originalTicks <= 0) return 0;
        return disableTicks(originalTicks, readinessPercent, minimumTicks);
    }

    public static float movementMultiplier(double slowdownRemovedPercent) {
        return movementMultiplier(0.2F, slowdownRemovedPercent);
    }

    /**
     * Removes a percentage of the shield's supplied slowdown rather than
     * granting a second general movement-speed bonus. The result is always
     * between the supplied blocking multiplier and ordinary (1x) movement.
     */
    public static float movementMultiplier(float blockingMultiplier, double slowdownRemovedPercent) {
        float baseline = Float.isFinite(blockingMultiplier)
                ? Math.max(0.0F, Math.min(1.0F, blockingMultiplier))
                : 0.2F;
        return (float) (baseline
                + (1.0F - baseline) * percent(slowdownRemovedPercent) / 100.0D);
    }

    public static float safeDamage(double amount) {
        if (Double.isNaN(amount) || amount <= 0) return 0;
        // Numeric safety only, NOT a 100% gameplay ceiling. Leave headroom in float-based hooks.
        return (float) Math.min(Float.MAX_VALUE / 16.0, amount);
    }
}
