package com.mistaboom.essence_ascendance.valuation;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Keeps functional identity readable without giving incidental recipe votes their own payout. */
public final class EssenceRoutingPolicy {
    public static final int MAX_CATEGORIES = 2;
    public static final double SECONDARY_RELATIVE_MINIMUM = .25;

    private EssenceRoutingPolicy() { }

    /**
     * Select weights before monetary allocation, once per reversible family.
     * There is deliberately no absolute yield cutoff: a nugget must keep the
     * same categories as its compressed form, even when its payout is below one.
     */
    public static <T> Map<T, Double> dominant(Map<T, Double> weights, Function<T, String> stableId) {
        var ranked = weights.entrySet().stream()
                .filter(entry -> Double.isFinite(entry.getValue()) && entry.getValue() > 0)
                .sorted(Comparator.<Map.Entry<T, Double>>comparingDouble(Map.Entry::getValue).reversed()
                        .thenComparing(entry -> stableId.apply(entry.getKey())))
                .toList();
        Map<T, Double> selected = new LinkedHashMap<>();
        if (ranked.isEmpty()) return selected;
        double minimum = ranked.getFirst().getValue() * SECONDARY_RELATIVE_MINIMUM;
        for (var entry : ranked) {
            if (selected.size() == MAX_CATEGORIES || entry.getValue() < minimum) break;
            selected.put(entry.getKey(), entry.getValue());
        }
        return selected;
    }
}
