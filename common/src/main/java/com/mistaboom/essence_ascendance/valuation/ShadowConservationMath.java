package com.mistaboom.essence_ascendance.valuation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact rational constraints for reversible recipes, independent of Minecraft. */
final class ShadowConservationMath {
    private ShadowConservationMath() { }

    record Edge<T>(T source, T target, long numerator, long denominator) { }
    record Plan<T>(Map<T, Long> units, String problem) {
        Plan { units = Map.copyOf(units); }
        boolean valid() { return problem.isEmpty(); }
    }
    private record Ratio(BigInteger numerator, BigInteger denominator) {
        Ratio {
            BigInteger gcd = numerator.gcd(denominator);
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
        }
        Ratio times(long n, long d) {
            return new Ratio(numerator.multiply(BigInteger.valueOf(n)),
                    denominator.multiply(BigInteger.valueOf(d)));
        }
    }

    static <T> Plan<T> plan(List<T> members, List<Edge<T>> edges, long maximumUnits) {
        if (members.isEmpty() || maximumUnits < 1) return new Plan<>(Map.of(), "empty/invalid family");
        Map<T, List<Edge<T>>> outgoing = new LinkedHashMap<>();
        for (Edge<T> edge : edges) {
            if (edge.numerator() <= 0 || edge.denominator() <= 0)
                return new Plan<>(Map.of(), "nonpositive recipe ratio");
            outgoing.computeIfAbsent(edge.source(), ignored -> new ArrayList<>()).add(edge);
        }
        Map<T, Ratio> ratios = new LinkedHashMap<>();
        List<T> queue = new ArrayList<>();
        T start = members.getFirst();
        queue.add(start);
        ratios.put(start, new Ratio(BigInteger.ONE, BigInteger.ONE));
        for (int i = 0; i < queue.size(); i++) {
            T current = queue.get(i);
            for (Edge<T> edge : outgoing.getOrDefault(current, List.of())) {
                Ratio next = ratios.get(current).times(edge.numerator(), edge.denominator());
                // Bound malicious/extreme graphs before BigInteger growth becomes expensive.
                if (next.numerator().bitLength() > 256 || next.denominator().bitLength() > 256)
                    return new Plan<>(Map.of(), "ratio precision exceeds safety bound");
                Ratio previous = ratios.putIfAbsent(edge.target(), next);
                if (previous == null) queue.add(edge.target());
                else if (!previous.equals(next))
                    return new Plan<>(Map.of(), "contradictory reversible recipe ratios");
            }
        }
        if (ratios.size() != members.size() || !ratios.keySet().containsAll(members))
            return new Plan<>(Map.of(), "disconnected/incomplete family");
        BigInteger commonDenominator = BigInteger.ONE;
        for (Ratio ratio : ratios.values()) {
            commonDenominator = commonDenominator.divide(commonDenominator.gcd(ratio.denominator()))
                    .multiply(ratio.denominator());
            if (commonDenominator.bitLength() > 4096)
                return new Plan<>(Map.of(), "family denominator exceeds safety bound");
        }
        Map<T, BigInteger> unscaled = new LinkedHashMap<>();
        BigInteger gcd = BigInteger.ZERO;
        for (T member : members) {
            Ratio ratio = ratios.get(member);
            BigInteger units = ratio.numerator().multiply(commonDenominator.divide(ratio.denominator()));
            unscaled.put(member, units);
            gcd = gcd.gcd(units);
        }
        Map<T, Long> result = new LinkedHashMap<>();
        for (T member : members) {
            BigInteger units = unscaled.get(member).divide(gcd);
            if (units.compareTo(BigInteger.valueOf(maximumUnits)) > 0)
                return new Plan<>(Map.of(), "family ratio exceeds representable payout range");
            result.put(member, units.longValueExact());
        }
        return new Plan<>(result, "");
    }

    /** Round once, down, at the primitive-family unit; never saturate forms separately. */
    static long unitPayout(double estimate, long largestUnits, long maximumValue) {
        if (!Double.isFinite(estimate) || estimate <= 0 || largestUnits < 1 || maximumValue < 1) return 0;
        long upper = maximumValue / largestUnits;
        return (long) Math.min(upper, Math.floor(estimate));
    }
}
