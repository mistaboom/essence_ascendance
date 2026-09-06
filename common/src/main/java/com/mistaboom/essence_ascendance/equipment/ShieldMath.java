package com.mistaboom.essence_ascendance.equipment;

/** Pure, shared shield arithmetic. Inputs are percentages, never already-converted fractions. */
public final class ShieldMath {
    private ShieldMath() {}

    public static double percent(double value) {
        return Double.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0;
    }

    public static double ordinaryPercent(double innate, double shieldInvestment, double armorInvestment) {
        return saturatedAdd(nonnegative(innate), Math.max(nonnegative(shieldInvestment), nonnegative(armorInvestment)));
    }

    public static double blockedPercent(double innate, double invested, double amplification) {
        return saturatedMultiply(saturatedAdd(nonnegative(innate), nonnegative(invested)), nonnegative(amplification));
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

    public static float movementMultiplier(double slowdownRemovedPercent) {
        return (float) (0.2 + 0.8 * percent(slowdownRemovedPercent) / 100.0);
    }

    public static float safeDamage(double amount) {
        if (Double.isNaN(amount) || amount <= 0) return 0;
        // Numeric safety only, NOT a 100% gameplay ceiling. Leave headroom in float-based hooks.
        return (float) Math.min(Float.MAX_VALUE / 16.0, amount);
    }
}
