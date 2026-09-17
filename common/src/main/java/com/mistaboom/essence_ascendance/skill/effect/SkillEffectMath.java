package com.mistaboom.essence_ascendance.skill.effect;

import java.util.List;

/** Pure, bounded arithmetic shared by skill handlers and the in-mod diagnostic. */
public final class SkillEffectMath {
    private SkillEffectMath() { }

    public static double clamp(double value, double minimum, double maximum) {
        return Double.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : minimum;
    }

    /** Add only the shortfall needed for a native attribute floor; never erase another source. */
    public static double attributeFloorAddition(double subtotal, double multiplier, double minimum) {
        if (!Double.isFinite(subtotal) || !Double.isFinite(multiplier) || !Double.isFinite(minimum)
                || multiplier <= 0 || minimum <= 0) return 0;
        double addition = minimum / multiplier - subtotal;
        return Double.isFinite(addition) ? Math.max(0, addition) : 0;
    }

    public static int stacks(int count, int maximum) {
        return Math.max(0, Math.min(Math.max(0, maximum), count));
    }

    public static double stackMultiplier(int count, int maximum, double percentPerStack) {
        return 1.0 + stacks(count, maximum) * clamp(percentPerStack, 0.0, 10_000.0) / 100.0;
    }

    /** Invalid health fails closed: it must never manufacture a low-health advantage. */
    public static double healthFraction(double health, double maxHealth) {
        if (!Double.isFinite(health) || !Double.isFinite(maxHealth) || maxHealth <= 0.0) return 1.0;
        return clamp(health / maxHealth, 0.0, 1.0);
    }

    public static double desperationMultiplier(double health, double maxHealth, double maximumPercent) {
        return 1.0 + (1.0 - healthFraction(health, maxHealth))
                * clamp(maximumPercent, 0.0, 10_000.0) / 100.0;
    }

    public static boolean rushEligible(double fraction, double threshold) {
        return Double.isFinite(fraction) && fraction >= 0.0
                && fraction < clamp(threshold, 0.0, 1.0);
    }

    public static boolean rushRefreshes(double fraction, double threshold, double nearDeathThreshold) {
        return rushEligible(fraction, threshold)
                && fraction <= clamp(nearDeathThreshold, 0.0, clamp(threshold, 0.0, 1.0));
    }

    public static long expiresAt(long now, int durationTicks) {
        long duration = Math.max(1, Math.min(72_000, durationTicks));
        return now > Long.MAX_VALUE - duration ? Long.MAX_VALUE : now + duration;
    }

    public static long remaining(long expiry, long now) {
        return expiry > now ? expiry - now : 0;
    }

    /** Independent expiries; an ordinary kill at cap intentionally changes no expiry. */
    public static List<Long> grantRush(List<Long> previous, long now, int maximum,
                                       int durationTicks, double healthFraction,
                                       double killThreshold, double nearDeathThreshold) {
        TimedStackState state = TimedStackState.independent(previous);
        state.reconcile(now, maximum);
        if (rushEligible(healthFraction, killThreshold)) {
            state.grant(now, maximum, durationTicks,
                    rushRefreshes(healthFraction, killThreshold, nearDeathThreshold));
        }
        return state.expiries();
    }
}
