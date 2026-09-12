package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Reusable percentage-yield arithmetic for authoritative server rewards.
 *
 * <p>Whole bonus units are guaranteed. A fractional remainder is rolled once
 * per original output stack, so even a one-item harvest receives its exact
 * long-run expected bonus instead of being rounded permanently to zero.</p>
 */
public final class PercentageBonusYieldService {

    /* Prevent a malformed server override from allocating an unbounded list. */
    private static final int MAX_ADDITIONAL_STACKS_PER_OUTPUT = 4096;

    private PercentageBonusYieldService() {
    }

    public static YieldResult apply(
            List<ItemStack> baseDrops,
            double bonusPercent,
            RandomSource random,
            Predicate<ItemStack> eligibleOutput
    ) {
        Objects.requireNonNull(baseDrops, "Base drops cannot be null");
        Objects.requireNonNull(random, "Random source cannot be null");
        Objects.requireNonNull(eligibleOutput, "Output predicate cannot be null");

        if (!Double.isFinite(bonusPercent) || bonusPercent <= 0.0D
                || baseDrops.isEmpty()) {
            return new YieldResult(baseDrops, 0L, 0L, Math.max(0.0D, bonusPercent));
        }

        List<ItemStack> resolved = new ArrayList<>(baseDrops);
        long eligibleUnits = 0L;
        long additionalUnits = 0L;

        for (ItemStack base : baseDrops) {
            if (base == null || base.isEmpty() || !eligibleOutput.test(base)) {
                continue;
            }

            int baseCount = base.getCount();
            if (baseCount <= 0) {
                continue;
            }

            eligibleUnits = saturatingAdd(eligibleUnits, baseCount);

            double expected = baseCount * (bonusPercent / 100.0D);
            if (!Double.isFinite(expected) || expected <= 0.0D) {
                continue;
            }

            long extra = expected >= Long.MAX_VALUE
                    ? Long.MAX_VALUE
                    : (long) Math.floor(expected);
            double remainder = expected - Math.floor(expected);
            if (remainder > 0.0D && random.nextDouble() < remainder
                    && extra < Long.MAX_VALUE) {
                extra++;
            }

            int maximumStackSize = Math.max(1, base.getMaxStackSize());
            long maximumUnits = (long) maximumStackSize
                    * MAX_ADDITIONAL_STACKS_PER_OUTPUT;
            extra = Math.min(extra, maximumUnits);

            long remaining = extra;
            while (remaining > 0L) {
                int count = (int) Math.min(remaining, maximumStackSize);
                resolved.add(base.copyWithCount(count));
                remaining -= count;
            }

            additionalUnits = saturatingAdd(additionalUnits, extra);
        }

        if (additionalUnits == 0L) {
            return new YieldResult(baseDrops, eligibleUnits, 0L, bonusPercent);
        }

        return new YieldResult(
                List.copyOf(resolved),
                eligibleUnits,
                additionalUnits,
                bonusPercent
        );
    }

    private static long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    public record YieldResult(
            List<ItemStack> drops,
            long eligibleBaseUnits,
            long additionalUnits,
            double bonusPercent
    ) {
        public YieldResult {
            drops = List.copyOf(drops);
        }
    }
}
