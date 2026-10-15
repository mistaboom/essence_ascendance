package com.mistaboom.essence_ascendance.balance.runtime;

import java.util.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Five-tier native outcome planner. Placement is a shared impact policy, never an acquisition claim. */
final class BonusProgressionPlanner {
    private BonusProgressionPlanner() { }
    static final double INCREMENT_GROWTH = 1.5;
    static final double FIRST_TIER_IMPACT = .05;
    interface Mechanics {
        double ceiling(int tier);
        boolean improves(int tier, double previous, double value);
        double impact(int tier, double value);
    }
    record Plan(int start, List<Double> values) {
        Plan { values = List.copyOf(values); }
        int end() { return start + values.size() - 1; }
    }

    static Plan automatic(int tiers, double maximum, double firstFloor, double laterFloor, double quantum, Mechanics mechanics) {
        var feasible = new ArrayList<Integer>();
        for (int tier = 0; tier < tiers; tier++) {
            double first = next(tier, 0, firstFloor, quantum, mechanics);
            if (!Double.isFinite(first)) continue;
            feasible.add(tier);
            if (mechanics.impact(tier, first) <= FIRST_TIER_IMPACT * Math.pow(2, tier) + 1e-12) {
                var plan = at(tier, tiers, maximum, first, laterFloor, quantum, mechanics);
                if (plan != null && mechanics.impact(tier, plan.values().getFirst())
                        <= FIRST_TIER_IMPACT * Math.pow(2, tier) + 1e-12) return plan;
            }
        }
        // A nominal maximum is a cap, not permission to front-load an excessive single-state remainder.
        for (int tier : feasible) {
            double first = next(tier, 0, firstFloor, quantum, mechanics);
            if (mechanics.impact(tier, first) <= FIRST_TIER_IMPACT * Math.pow(2, tier) + 1e-12)
                return new Plan(tier, List.of(first));
        }
        // Impact budgets are soft. A later saturated tier must not erase a genuinely useful earlier route.
        if (!feasible.isEmpty()) {
            int tier = feasible.getLast();
            // The exception belongs only to the unavoidable native first state, never a large tail.
            return new Plan(tier, List.of(next(tier, 0, firstFloor, quantum, mechanics)));
        }
        throw new IllegalArgumentException("No active tier can provide the authored minimum and a native improvement");
    }

    private static Plan at(int start, int tiers, double requested, double first, double laterFloor, double quantum, Mechanics mechanics) {
        double maximum = Math.max(first, Math.min(requested, mechanics.ceiling(start)));
        int count = 1;
        BigDecimal total = BigDecimal.valueOf(first), increment = BigDecimal.valueOf(Math.max(first, laterFloor));
        while (start + count < tiers) {
            var next = BigDecimal.valueOf(RuntimeValueQuantization.up(increment.doubleValue(), quantum));
            if (total.add(next).compareTo(BigDecimal.valueOf(maximum)) > 0) break;
            total = total.add(next); count++; increment = increment.multiply(BigDecimal.valueOf(INCREMENT_GROWTH));
        }
        // Try the meaningful count first; native saturation may require fewer consecutive states.
        for (int states = count; states >= 1; states--) {
            int end = start + states - 1;
            double bounded = maximum;
            for (int tier = start; tier <= end; tier++) bounded = Math.min(bounded, mechanics.ceiling(tier));
            if (bounded + 1e-9 < first) continue;
            bounded = RuntimeValueQuantization.down(bounded, quantum);
            try { return configured(start, end, bounded, first, laterFloor, quantum, mechanics); }
            catch (IllegalArgumentException unavailable) { /* Try a shorter useful span, retaining its native cap. */ }
        }
        return null;
    }

    static Plan configured(int start, int end, double maximum, double firstFloor, double laterFloor,
                           double quantum, Mechanics mechanics) {
        if (start < 0 || end < start || !Double.isFinite(maximum) || maximum <= 0)
            throw new IllegalArgumentException("Invalid configured Bonus span or maximum");
        if (RuntimeValueQuantization.reconstructedGridValue(maximum, quantum) != RuntimeValueQuantization.down(maximum, quantum))
            throw new IllegalArgumentException("Configured bonus_at_max is off its gameplay unit grid");
        if (maximum > mechanics.ceiling(end))
            throw new IllegalArgumentException("Configured bonus_at_max exceeds the native ceiling at tier " + end);
        var values = new ArrayList<Double>();
        double previous = 0, priorIncrement = 0;
        for (int tier = start; tier <= end; tier++) {
            double floor = tier == start ? firstFloor : Math.max(laterFloor, priorIncrement);
            double minimum = next(tier, previous, floor, quantum, mechanics);
            double ceiling = Math.min(maximum, mechanics.ceiling(tier));
            if (!Double.isFinite(minimum) || minimum > ceiling + 1e-9)
                throw new IllegalArgumentException("Configured span cannot provide a meaningful native increment at tier " + tier);
            double value;
            if (tier == end) {
                value = maximum;
                if (value > mechanics.ceiling(tier) || value + 1e-9 < minimum || !mechanics.improves(tier, previous, value))
                    throw new IllegalArgumentException("Configured bonus_at_max exceeds the native ceiling or improvement floor at tier " + tier);
            } else {
                if (!canFinish(tier + 1, end, minimum, minimum - previous, maximum, laterFloor, quantum, mechanics))
                    throw new IllegalArgumentException("Configured span cannot provide a meaningful native increment at every active tier");
                double desired = tier == start ? minimum : distributedNext(previous, maximum, end - tier + 1, quantum);
                desired = Math.max(minimum, Math.min(ceiling, desired));
                long low = Math.round(minimum / quantum), high = unitsDown(desired, quantum);
                while (low < high) {
                    long middle = low + (high - low + 1) / 2;
                    double candidate = grid(middle, quantum);
                    if (canFinish(tier + 1, end, candidate, candidate - previous, maximum, laterFloor, quantum, mechanics)) low = middle;
                    else high = middle - 1;
                }
                value = grid(low, quantum);
                if (!mechanics.improves(tier, previous, value)) value = minimum;
            }
            priorIncrement = value - previous;
            previous = value; values.add(value);
        }
        return new Plan(start, values);
    }

    /** Keep the first native reward; spread the remaining endpoint across all remaining intervals. */
    private static double distributedNext(double previous, double maximum, int intervals, double quantum) {
        BigDecimal weight = BigDecimal.ONE, totalWeight = BigDecimal.ZERO;
        for (int i = 0; i < intervals; i++) {
            totalWeight = totalWeight.add(weight);
            weight = weight.multiply(BigDecimal.valueOf(INCREMENT_GROWTH));
        }
        BigDecimal prior = BigDecimal.valueOf(previous);
        double desired = prior.add(BigDecimal.valueOf(maximum).subtract(prior)
                .divide(totalWeight, MathContext.DECIMAL128)).doubleValue();
        return RuntimeValueQuantization.up(RuntimeValueQuantization.reconstructedGridValue(desired, quantum), quantum);
    }

    private static boolean canFinish(int start, int end, double previous, double priorIncrement, double maximum,
                                     double laterFloor, double quantum, Mechanics mechanics) {
        for (int tier = start; tier <= end; tier++) {
            double value = next(tier, previous, Math.max(laterFloor, priorIncrement), quantum, mechanics);
            if (!Double.isFinite(value) || value > maximum + 1e-9) return false;
            if (tier == end) return maximum <= mechanics.ceiling(tier)
                    && mechanics.improves(tier, previous, maximum);
            priorIncrement = value - previous; previous = value;
        }
        return true;
    }

    private static double next(int tier, double previous, double increment, double quantum, Mechanics mechanics) {
        double minimum = RuntimeValueQuantization.up(java.math.BigDecimal.valueOf(previous)
                .add(java.math.BigDecimal.valueOf(RuntimeValueQuantization.reconstructedGridValue(increment, quantum))).doubleValue(), quantum);
        double ceiling = mechanics.ceiling(tier);
        if (minimum > ceiling + 1e-9 || !mechanics.improves(tier, previous, ceiling)) return Double.NaN;
        if (mechanics.improves(tier, previous, minimum)) return minimum;
        long low = Math.round(minimum / quantum), high = unitsDown(ceiling, quantum);
        if (!mechanics.improves(tier, previous, grid(high, quantum))) return Double.NaN;
        while (low < high) {
            long middle = low + (high - low) / 2;
            if (mechanics.improves(tier, previous, grid(middle, quantum))) high = middle;
            else low = middle + 1;
        }
        return grid(low, quantum);
    }
    private static long unitsDown(double value, double quantum) {
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(quantum), 0, RoundingMode.FLOOR).longValueExact();
    }
    private static double grid(long units, double quantum) {
        return java.math.BigDecimal.valueOf(quantum).multiply(java.math.BigDecimal.valueOf(units)).doubleValue();
    }
}
