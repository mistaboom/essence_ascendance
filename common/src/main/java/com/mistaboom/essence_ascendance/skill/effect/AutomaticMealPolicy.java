package com.mistaboom.essence_ascendance.skill.effect;

/** Pure meal usefulness policy, parameterized by the native resource limits at the call site. */
public final class AutomaticMealPolicy {
    private AutomaticMealPolicy() { }
    public static boolean useful(boolean healingNeeded, int foodLevel, int nutrition, boolean usingItem,
                                 int capacity, int regenerationThreshold) {
        return useful(healingNeeded, false, foodLevel, nutrition, usingItem, capacity, regenerationThreshold);
    }
    public static boolean useful(boolean healingNeeded, boolean foodCanHeal, int foodLevel, int nutrition,
                                 boolean usingItem, int capacity, int regenerationThreshold) {
        return useful(healingNeeded, foodCanHeal, false, foodLevel, nutrition, usingItem,
                capacity, regenerationThreshold);
    }

    /**
     * Shared Feast Reflex opportunity policy. Maintenance recovery is a first-class resource need just like
     * health recovery: when Metabolic Mending can spend the meal on damaged gear, full hunger does not make
     * the meal useless. The caller still owns food safety, cooldown, and canEat validation.
     */
    public static boolean useful(boolean healingNeeded, boolean foodCanHeal, boolean maintenanceNeeded,
                                 int foodLevel, int nutrition, boolean usingItem,
                                 int capacity, int regenerationThreshold) {
        if (capacity <= 0 || regenerationThreshold <= 0 || regenerationThreshold > capacity)
            throw new IllegalArgumentException("Invalid food limits");
        if (usingItem || foodLevel < 0 || foodLevel > capacity || nutrition <= 0) return false;
        if (maintenanceNeeded) return true;
        // Cross the ordinary regeneration threshold and then continue at full hunger until HP is full.
        if (healingNeeded && foodCanHeal) return true;
        if (foodLevel == capacity) return false;
        if (healingNeeded) return foodLevel < regenerationThreshold;
        // At full health avoid wasting half or more of the candidate's nutrition.
        return 2L * (capacity - foodLevel) > nutrition;
    }
}
