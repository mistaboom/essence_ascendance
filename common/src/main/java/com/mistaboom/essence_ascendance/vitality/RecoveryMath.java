package com.mistaboom.essence_ascendance.vitality;

/** Pure recovery calculations; health uses native health points, never hearts. */
public final class RecoveryMath {
    private RecoveryMath() { }

    public static double speed(double health, double maximum, double bonus, double exponent) {
        if (!Double.isFinite(health) || !Double.isFinite(maximum) || maximum <= 0
                || !Double.isFinite(bonus) || !Double.isFinite(exponent)) return 1;
        double missing = Math.max(0, Math.min(1, 1 - health / maximum));
        return 1 + Math.max(0, bonus) * Math.pow(missing, Math.max(1, exponent));
    }

    public static double healing(double damage, double missing, double base, double increment, int hits, int cap) {
        if (!Double.isFinite(damage) || !Double.isFinite(missing) || damage <= 0 || missing <= 0 || hits <= 0) return 0;
        return Math.min(missing, damage * Math.max(0, base + Math.max(0, Math.min(hits, cap) - 1) * increment));
    }
}
