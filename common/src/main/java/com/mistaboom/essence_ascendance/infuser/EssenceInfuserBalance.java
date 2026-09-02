package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.config.InfuserBalanceSettings;
import com.mistaboom.essence_ascendance.pylon.EssencePylonFocusTier;
import net.minecraft.world.item.ItemStack;

/** Central conversion arithmetic and Focus-to-Infusion-Grade evaluation. */
public final class EssenceInfuserBalance {

    private static final long EFFICIENCY_SCALE = 10_000L;

    private EssenceInfuserBalance() {
    }

    public static double linkRange() {
        return EssenceConfigManager.get().infuserBalance().linkRange();
    }

    public static Profile profile(ItemStack focusStack) {
        EssencePylonFocusTier focusTier =
                com.mistaboom.essence_ascendance.pylon.EssencePylonContent
                        .focusTier(focusStack);
        return profile(focusTier);
    }

    public static Profile profile(EssencePylonFocusTier focusTier) {
        InfuserBalanceSettings settings =
                EssenceConfigManager.get().infuserBalance();

        EssencePylonFocusTier grade = focusTier == null
                ? EssencePylonFocusTier.DORMANT
                : focusTier;
        InfuserBalanceSettings.GradeSettings gradeSettings =
                settings.grade(grade.serializedName());

        int efficiency = focusTier == null
                ? settings.noFocusEfficiencyBasisPoints()
                : gradeSettings.efficiencyBasisPoints();
        int processingTicks = focusTier == null
                ? settings.noFocusProcessingTicks()
                : gradeSettings.processingTicks();

        return new Profile(grade, efficiency, processingTicks);
    }

    /** Returns 0 when the calculation cannot be represented safely. */
    public static long requiredSource(long targetAmount, int efficiencyBasisPoints) {
        if (targetAmount <= 0L
                || efficiencyBasisPoints <= 0
                || efficiencyBasisPoints >= EFFICIENCY_SCALE) {
            return 0L;
        }
        if (targetAmount > Long.MAX_VALUE / EFFICIENCY_SCALE) {
            return 0L;
        }

        long numerator = targetAmount * EFFICIENCY_SCALE;
        long quotient = numerator / efficiencyBasisPoints;
        long remainder = numerator % efficiencyBasisPoints;
        if (remainder == 0L) {
            return quotient;
        }
        return quotient == Long.MAX_VALUE ? 0L : quotient + 1L;
    }

    public record Profile(
            EssencePylonFocusTier grade,
            int efficiencyBasisPoints,
            int processingTicks
    ) {
        public double efficiencyPercent() {
            return efficiencyBasisPoints / 100.0D;
        }
    }
}
