package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.balance.economy.FractionalAmountService;

/** Pure arithmetic for carrying percentage reductions across integer costs. */
public final class FractionalIntegerCostService {

    private static final double EPSILON = 1.0E-9D;

    private FractionalIntegerCostService() {
    }

    public static Resolution resolve(
            int baseCost,
            double reductionPercent,
            double previousCarry,
            int minimumCost
    ) {
        if (baseCost < 0 || minimumCost < 0 || minimumCost > baseCost) {
            throw new IllegalArgumentException("Invalid integer cost bounds");
        }
        if (!Double.isFinite(reductionPercent)
                || reductionPercent < 0.0D
                || reductionPercent > 100.0D) {
            throw new IllegalArgumentException("Cost reduction must be in [0, 100]");
        }
        if (!Double.isFinite(previousCarry)
                || previousCarry < 0.0D
                || previousCarry >= 1.0D) {
            throw new IllegalArgumentException("Cost carry must be in [0, 1)");
        }
        if (baseCost == 0) {
            return new Resolution(0, previousCarry);
        }

        double exactCost = baseCost * (1.0D - reductionPercent / 100.0D)
                + previousCarry;
        int resolvedCost = (int) FractionalAmountService.split(exactCost + EPSILON).whole();
        resolvedCost = Math.max(minimumCost, Math.min(baseCost, resolvedCost));

        double nextCarry;
        if (resolvedCost == minimumCost && exactCost < minimumCost) {
            /* A hard minimum intentionally discards an unusable sub-unit. */
            nextCarry = 0.0D;
        } else {
            nextCarry = exactCost - resolvedCost;
            nextCarry = Math.max(0.0D, Math.min(Math.nextDown(1.0D), nextCarry));
            if (nextCarry < EPSILON || 1.0D - nextCarry < EPSILON) {
                nextCarry = 0.0D;
            }
        }

        return new Resolution(resolvedCost, nextCarry);
    }

    public record Resolution(
            int resolvedCost,
            double nextCarry
    ) {
    }
}
