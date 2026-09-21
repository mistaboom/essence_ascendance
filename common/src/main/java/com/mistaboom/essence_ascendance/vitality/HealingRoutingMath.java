package com.mistaboom.essence_ascendance.vitality;

/** Accepted healing is measured before native maximum-health clamping. Never uses a pre-event request. */
public final class HealingRoutingMath {
    private HealingRoutingMath() { }
    /** All terms are post-source-scaling health units. Floors in progression never act as caps here. */
    public static double demand(double missingHealth, double missingFood, double foodPerHealth,
                                double debt, double debtPerHealth, boolean foodOrigin) {
        double health = Double.isFinite(missingHealth) ? Math.max(0, missingHealth) : 0;
        if (!foodOrigin && Double.isFinite(missingFood) && Double.isFinite(foodPerHealth)
                && missingFood > 0 && foodPerHealth > 0) health += missingFood / foodPerHealth;
        return usefulHealing(Math.min(Float.MAX_VALUE, health), debt, debtPerHealth);
    }
    public static double accepted(double before, double proposed) {
        return Double.isFinite(before) && Double.isFinite(proposed) ? Math.max(0, proposed - before) : 0;
    }
    public static double mirrored(double accepted, double queue, double multiplier) {
        if (!Double.isFinite(accepted) || !Double.isFinite(queue) || !Double.isFinite(multiplier)
                || accepted <= 0 || queue <= 0 || multiplier <= 0) return 0;
        return Math.min(queue, accepted * Math.min(1, multiplier));
    }
    /** A clamped healing producer can service HP and debt in parallel, not add them into extra healing. */
    public static double usefulHealing(double missingHealth, double queue, double multiplier) {
        double health = Double.isFinite(missingHealth) ? Math.max(0, missingHealth) : 0;
        if (!Double.isFinite(queue) || !Double.isFinite(multiplier) || queue <= 0 || multiplier <= 0) return health;
        return Math.max(health, Math.min(Float.MAX_VALUE, queue / multiplier));
    }
}
