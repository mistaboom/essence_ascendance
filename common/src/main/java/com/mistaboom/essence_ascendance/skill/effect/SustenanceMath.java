package com.mistaboom.essence_ascendance.skill.effect;

import net.minecraft.world.food.FoodConstants;

/** Native capacities and deterministic timing used by the server service and invariant fixtures. */
public final class SustenanceMath {
    /** Vanilla starts natural food regeneration at this food-bar level. */
    public static final int NATURAL_REGENERATION_FOOD_LEVEL = 18;

    private SustenanceMath() { }
    public static int useDuration(int nativeTicks, double multiplier) {
        if (nativeTicks <= 0 || !Double.isFinite(multiplier) || multiplier <= 0) return nativeTicks;
        return Math.max(1, Math.min(nativeTicks, (int) Math.ceil(nativeTicks * multiplier)));
    }
    public static boolean full(int food, float saturation) {
        return food >= FoodConstants.MAX_FOOD && Float.isFinite(saturation) && saturation >= FoodConstants.MAX_FOOD;
    }
    public static boolean automaticMeal(double healthLost, float saturation, boolean usingItem) {
        return Double.isFinite(healthLost) && healthLost > 0 && Float.isFinite(saturation) && saturation <= 0 && !usingItem;
    }
    /**
     * Feast Reflex uses the visible food bar as the player's need signal. A
     * non-full food bar is actionable even when Minecraft still has a small
     * saturation reserve; waiting for saturation to reach exactly zero made
     * the feature appear broken at ordinary low hunger levels.
     */
    public static boolean automaticMeal(double healthLost, int foodLevel, float saturation, boolean usingItem) {
        return Double.isFinite(healthLost) && healthLost > 0
                && foodLevel >= 0 && foodLevel < FoodConstants.MAX_FOOD
                && Float.isFinite(saturation) && !usingItem;
    }

    /**
     * Decides whether one particular food stack is useful for an automatic
     * meal. While hurt, every nutritious meal below the regeneration threshold
     * is progress toward recovery; one serving need not bridge the whole gap.
     * At full health it only tops up hunger when fewer than
     * half of that food's nutrition points would be wasted.
     */
    public static boolean automaticMealOpportunity(boolean healingNeeded, int foodLevel,
                                                   int nutrition, boolean usingItem) {
        return AutomaticMealPolicy.useful(healingNeeded, foodLevel, nutrition, usingItem,
                FoodConstants.MAX_FOOD, NATURAL_REGENERATION_FOOD_LEVEL);
    }
    public static boolean automaticMealOpportunity(boolean healingNeeded, boolean foodCanHeal, int foodLevel,
                                                   int nutrition, boolean usingItem) {
        return automaticMealOpportunity(healingNeeded, foodCanHeal, false, foodLevel, nutrition, usingItem);
    }
    public static boolean automaticMealOpportunity(boolean healingNeeded, boolean foodCanHeal,
                                                   boolean maintenanceNeeded, int foodLevel,
                                                   int nutrition, boolean usingItem) {
        return AutomaticMealPolicy.useful(healingNeeded, foodCanHeal, maintenanceNeeded, foodLevel, nutrition,
                usingItem, FoodConstants.MAX_FOOD, NATURAL_REGENERATION_FOOD_LEVEL);
    }
    public static final class RecoveryClock implements SkillEffectState {
        private long previousTick = Long.MIN_VALUE;
        private int quietTicks;
        public boolean tick(long now, boolean combat, int interval) {
            if (now == previousTick) return false;
            if (previousTick != Long.MIN_VALUE && now != previousTick + 1) quietTicks = 0;
            previousTick = now;
            if (combat) { quietTicks = 0; return false; }
            if (++quietTicks < interval) return false;
            quietTicks = 0;
            return true;
        }
        public int progress() { return quietTicks; }
        @Override public void clear() { previousTick = Long.MIN_VALUE; quietTicks = 0; }
    }
}
