package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.config.InfuserBalanceSettings;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import net.minecraft.world.item.ItemStack;

/** Central conversion arithmetic and Focus-to-Infusion-Grade evaluation. */
public final class EssenceInfuserBalance {

    private static final long EFFICIENCY_SCALE = 10_000L;
    private static final int TICKS_PER_SECOND = 20;

    private EssenceInfuserBalance() {
    }

    public static double linkRange() {
        return EssenceConfigManager.get().infuserBalance().linkRange();
    }

    public static Profile profile(ItemStack focusStack) {
        EssenceFocusTier focusTier =
                com.mistaboom.essence_ascendance.pylon.EssencePylonContent
                        .focusTier(focusStack);
        return profile(focusTier);
    }

    public static Profile profile(EssenceFocusTier focusTier) {
        InfuserBalanceSettings settings =
                EssenceConfigManager.get().infuserBalance();

        EssenceFocusTier grade = focusTier == null
                ? EssenceFocusTier.DORMANT
                : focusTier;
        InfuserBalanceSettings.GradeSettings gradeSettings =
                settings.grade(grade.serializedName());

        int efficiency = focusTier == null
                ? settings.noFocusEfficiencyBasisPoints()
                : gradeSettings.efficiencyBasisPoints();
        long infusionThroughputPerSecond = focusTier == null
                ? settings.noFocusInfusionThroughputPerSecond()
                : gradeSettings.infusionThroughputPerSecond();

        return new Profile(grade, efficiency, infusionThroughputPerSecond);
    }

    /** Returns 0 when the calculation cannot be represented safely. */
    public static long requiredSource(long targetAmount, int efficiencyBasisPoints) {
        if (efficiencyBasisPoints >= EFFICIENCY_SCALE) return 0;
        return com.mistaboom.essence_ascendance.balance.economy.FractionalAmountService.requiredForEfficiency(
                targetAmount, efficiencyBasisPoints);
    }

    /**
     * Converts infusion work into a machine duration without multiplying the
     * potentially huge work value by 20. The returned value is rounded up so
     * a recipe can never finish faster than the configured throughput.
     */
    public static int processingTicksForWork(
            long infusionWork,
            long infusionThroughputPerSecond
    ) {
        if (infusionWork <= 0L || infusionThroughputPerSecond <= 0L) {
            return 1;
        }

        long wholeSeconds = infusionWork / infusionThroughputPerSecond;
        long remainder = infusionWork % infusionThroughputPerSecond;
        if (wholeSeconds >= Integer.MAX_VALUE / (long) TICKS_PER_SECOND) {
            return Integer.MAX_VALUE;
        }

        long ticks = wholeSeconds * TICKS_PER_SECOND;
        if (remainder > 0L) {
            ticks += fractionalTicksRoundedUp(remainder, infusionThroughputPerSecond);
        }
        if (ticks <= 0L) {
            return 1;
        }
        return ticks >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ticks;
    }

    /**
     * Returns floor(rate * ticks / 20) plus an updated twentieth-of-an-Essence
     * remainder. This lets streamed Focus infusion honor non-divisible
     * Essence/sec rates exactly over time without floating-point state.
     */
    public static ThroughputSlice throughputForTicks(
            long infusionThroughputPerSecond,
            int ticks,
            int previousTwentieths
    ) {
        if (infusionThroughputPerSecond <= 0L || ticks <= 0) {
            return new ThroughputSlice(0L, 0);
        }

        int safePrevious = Math.floorMod(previousTwentieths, TICKS_PER_SECOND);
        long wholePerTick = infusionThroughputPerSecond / TICKS_PER_SECOND;
        int rateRemainder = (int) (infusionThroughputPerSecond % TICKS_PER_SECOND);

        long whole;
        try {
            whole = Math.multiplyExact(wholePerTick, (long) ticks);
        } catch (ArithmeticException overflow) {
            return new ThroughputSlice(Long.MAX_VALUE, 0);
        }

        long remainderNumerator = (long) rateRemainder * ticks + safePrevious;
        long extra = remainderNumerator / TICKS_PER_SECOND;
        int nextRemainder = (int) (remainderNumerator % TICKS_PER_SECOND);

        if (whole > Long.MAX_VALUE - extra) {
            return new ThroughputSlice(Long.MAX_VALUE, 0);
        }
        return new ThroughputSlice(whole + extra, nextRemainder);
    }

    private static int fractionalTicksRoundedUp(long remainder, long ratePerSecond) {
        long wholeTwentieth = ratePerSecond / TICKS_PER_SECOND;
        long twentiethRemainder = ratePerSecond % TICKS_PER_SECOND;

        for (int tick = 1; tick <= TICKS_PER_SECOND; tick++) {
            long threshold = wholeTwentieth * tick
                    + (twentiethRemainder * tick) / TICKS_PER_SECOND;
            if (remainder <= threshold) {
                return tick;
            }
        }
        return TICKS_PER_SECOND;
    }

    public record ThroughputSlice(long amount, int remainderTwentieths) {
    }

    public record Profile(
            EssenceFocusTier grade,
            int efficiencyBasisPoints,
            long infusionThroughputPerSecond
    ) {
        public double efficiencyPercent() {
            return efficiencyBasisPoints / 100.0D;
        }

        public int processingTicksForWork(long infusionWork) {
            return EssenceInfuserBalance.processingTicksForWork(
                    infusionWork,
                    infusionThroughputPerSecond
            );
        }
    }
}
