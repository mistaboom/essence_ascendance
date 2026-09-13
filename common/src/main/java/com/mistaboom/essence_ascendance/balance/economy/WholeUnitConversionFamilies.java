package com.mistaboom.essence_ascendance.balance.economy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Integer lattices for lossless material conversions. The ratios come entirely
 * from loaded recipes: a reversible 9:1/9:1 chain has units 1:9:81, for example.
 * Reconciling that family together prevents repeated independent flooring from
 * eroding a perfectly safe positive conversion family to zero.
 */
final class WholeUnitConversionFamilies {
    private final Map<String, Family> byItem = new TreeMap<>();
    private final List<Family> families = new ArrayList<>();

    WholeUnitConversionFamilies(ProductionGraph graph) {
        Map<String, List<Edge>> edges = new TreeMap<>();
        for (ProductionGraph.Process process : graph.processes()) {
            // A stock replenishment cycle produces new material through an
            // independent source; it is not an equality between material forms.
            if (BoundedProductionPolicy.isBoundedSource(process)) continue;
            List<ProductionGraph.Input> consumed = process.inputs().stream().filter(ProductionGraph.Input::consumed).toList();
            if (consumed.isEmpty() || consumed.stream().anyMatch(input -> input.alternatives().size() != 1)
                    || process.outputs().stream().anyMatch(output -> output.probability() != 1)) continue;
            Map<String, BigDecimal> inputs = new TreeMap<>(), outputs = new TreeMap<>();
            consumed.forEach(input -> inputs.merge(input.alternatives().getFirst(), BigDecimal.valueOf(input.count()), BigDecimal::add));
            process.outputs().forEach(output -> outputs.merge(output.itemId(), BigDecimal.valueOf(output.count()), BigDecimal::add));
            if (inputs.size() != 1 || outputs.size() != 1) continue;
            String from = inputs.keySet().iterator().next(), to = outputs.keySet().iterator().next();
            edges.computeIfAbsent(from, ignored -> new ArrayList<>()).add(new Edge(to,
                    Ratio.of(inputs.get(from)).divide(Ratio.of(outputs.get(to)))));
            edges.computeIfAbsent(to, ignored -> new ArrayList<>());
        }
        for (Set<String> component : components(edges)) {
            if (component.size() < 2) continue;
            Map<String, Ratio> ratios = consistentRatios(component, edges);
            if (ratios != null) {
                add(ratios);
                continue;
            }
            // A lossy/gainful route in a larger component need not invalidate
            // its genuinely reversible subfamilies. Only exact reverse pairs
            // impose an equality; other inequalities remain with the solver.
            Map<String, List<Edge>> exact = new TreeMap<>();
            for (String from : component) {
                List<Edge> selected = edges.get(from).stream().filter(edge -> component.contains(edge.to())
                        && edges.get(edge.to()).stream().anyMatch(reverse -> reverse.to().equals(from)
                        && edge.ratio().multiply(reverse.ratio()).equals(Ratio.ONE))).toList();
                exact.put(from, selected);
            }
            for (Set<String> subgroup : components(exact)) {
                if (subgroup.size() < 2) continue;
                Map<String, Ratio> subratios = consistentRatios(subgroup, exact);
                if (subratios != null) add(subratios);
            }
        }
    }

    Set<String> reconcileAll(Map<String, Long> values) {
        Set<String> changed = new TreeSet<>();
        families.forEach(family -> changed.addAll(family.reconcile(values)));
        return changed;
    }

    Set<String> reconcile(String item, Map<String, Long> values) {
        Family family = byItem.get(item);
        return family == null ? Set.of() : family.reconcile(values);
    }

    /** Apply one primitive Essence vector to every exactly reversible form. */
    void reconcileRoutes(Map<String, Map<String, Long>> routes, Map<String, Map<String, Long>> weights,
                         Map<String, DissolutionYield> yields) {
        for (Family family : families) {
            String anchor = family.weights().keySet().stream().min(java.util.Comparator
                    .<String, BigInteger>comparing(family.weights()::get).thenComparing(id -> id)).orElseThrow();
            BigInteger unit = family.weights().get(anchor);
            BigInteger total = BigInteger.valueOf(yields.getOrDefault(anchor, new DissolutionYield(0)).microUnits()
                    / FractionalAmountService.SCALE);
            BigInteger[] division = total.divideAndRemainder(unit);
            if (division[1].signum() != 0) throw new IllegalStateException("Non-integral conversion family payout " + anchor);
            Map<String, Long> primitive = EconomyGenerator.scaleRoutes(weights.getOrDefault(anchor, Map.of()), division[0].longValueExact());
            for (Map.Entry<String, BigInteger> member : family.weights().entrySet()) {
                Map<String, Long> allocated = new TreeMap<>();
                primitive.forEach((essence, amount) -> allocated.put(essence,
                        BigInteger.valueOf(amount).multiply(member.getValue()).longValueExact()));
                routes.put(member.getKey(), allocated);
            }
        }
    }

    void validateRoutes(Map<String, Map<String, Long>> routes) {
        for (Family family : families) {
            String anchor = family.weights().keySet().iterator().next();
            Map<String, Long> anchorRoutes = routes.getOrDefault(anchor, Map.of());
            Set<String> essences = new TreeSet<>();
            family.weights().keySet().forEach(id -> essences.addAll(routes.getOrDefault(id, Map.of()).keySet()));
            for (Map.Entry<String, BigInteger> member : family.weights().entrySet()) {
                Map<String, Long> memberRoutes = routes.getOrDefault(member.getKey(), Map.of());
                for (String essence : essences) {
                    BigInteger left = BigInteger.valueOf(memberRoutes.getOrDefault(essence, 0L)).multiply(family.weights().get(anchor));
                    BigInteger right = BigInteger.valueOf(anchorRoutes.getOrDefault(essence, 0L)).multiply(member.getValue());
                    if (!left.equals(right)) throw new IllegalArgumentException("Reversible whole-Essence category mismatch for "
                            + member.getKey() + " / " + essence + "; conversion families must preserve each Essence exactly");
                }
            }
        }
    }

    private void add(Map<String, Ratio> ratios) {
        BigInteger denominator = BigInteger.ONE;
        for (Ratio ratio : ratios.values()) denominator = lcm(denominator, ratio.denominator());
        Map<String, BigInteger> weights = new TreeMap<>();
        BigInteger gcd = BigInteger.ZERO;
        for (Map.Entry<String, Ratio> entry : ratios.entrySet()) {
            BigInteger weight = entry.getValue().numerator().multiply(denominator.divide(entry.getValue().denominator()));
            weights.put(entry.getKey(), weight);
            gcd = gcd.gcd(weight);
        }
        BigInteger divisor = gcd;
        weights.replaceAll((id, weight) -> weight.divide(divisor));
        Family family = new Family(weights);
        families.add(family);
        weights.keySet().forEach(id -> byItem.put(id, family));
    }

    private static Map<String, Ratio> consistentRatios(Set<String> component, Map<String, List<Edge>> edges) {
        Map<String, Ratio> ratios = new TreeMap<>();
        String first = component.iterator().next();
        ratios.put(first, Ratio.ONE);
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(first);
        while (!pending.isEmpty()) {
            String from = pending.removeFirst();
            for (Edge edge : edges.get(from)) {
                if (!component.contains(edge.to())) continue;
                Ratio expected = ratios.get(from).multiply(edge.ratio());
                Ratio existing = ratios.putIfAbsent(edge.to(), expected);
                if (existing == null) pending.addLast(edge.to());
                else if (!existing.equals(expected)) return null;
            }
        }
        return ratios;
    }

    /** Iterative Kosaraju traversal keeps large recipe graphs off the JVM call stack. */
    private static List<Set<String>> components(Map<String, List<Edge>> edges) {
        List<String> finished = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (String start : edges.keySet()) {
            if (!visited.add(start)) continue;
            ArrayDeque<Visit> stack = new ArrayDeque<>();
            stack.push(new Visit(start, 0));
            while (!stack.isEmpty()) {
                Visit visit = stack.pop();
                List<Edge> next = edges.get(visit.item());
                if (visit.index() == next.size()) { finished.add(visit.item()); continue; }
                stack.push(new Visit(visit.item(), visit.index() + 1));
                String target = next.get(visit.index()).to();
                if (visited.add(target)) stack.push(new Visit(target, 0));
            }
        }
        Map<String, List<String>> reverse = new TreeMap<>();
        edges.keySet().forEach(id -> reverse.put(id, new ArrayList<>()));
        edges.forEach((from, list) -> list.forEach(edge -> reverse.get(edge.to()).add(from)));
        visited.clear();
        List<Set<String>> result = new ArrayList<>();
        for (String start : finished.reversed()) {
            if (!visited.add(start)) continue;
            Set<String> component = new TreeSet<>();
            ArrayDeque<String> pending = new ArrayDeque<>();
            pending.add(start);
            while (!pending.isEmpty()) {
                String item = pending.removeFirst();
                component.add(item);
                for (String predecessor : reverse.get(item)) if (visited.add(predecessor)) pending.addLast(predecessor);
            }
            result.add(component);
        }
        return result;
    }

    private static BigInteger lcm(BigInteger a, BigInteger b) { return a.divide(a.gcd(b)).multiply(b); }
    private record Visit(String item, int index) { }
    private record Edge(String to, Ratio ratio) { }
    private record Ratio(BigInteger numerator, BigInteger denominator) {
        private static final Ratio ONE = new Ratio(BigInteger.ONE, BigInteger.ONE);
        private Ratio {
            BigInteger gcd = numerator.gcd(denominator);
            numerator = numerator.divide(gcd); denominator = denominator.divide(gcd);
        }
        private static Ratio of(BigDecimal decimal) {
            return decimal.scale() >= 0
                    ? new Ratio(decimal.unscaledValue(), BigInteger.TEN.pow(decimal.scale()))
                    : new Ratio(decimal.unscaledValue().multiply(BigInteger.TEN.pow(-decimal.scale())), BigInteger.ONE);
        }
        private Ratio multiply(Ratio other) { return new Ratio(numerator.multiply(other.numerator), denominator.multiply(other.denominator)); }
        private Ratio divide(Ratio other) { return new Ratio(numerator.multiply(other.denominator), denominator.multiply(other.numerator)); }
    }
    private record Family(Map<String, BigInteger> weights) {
        private Set<String> reconcile(Map<String, Long> values) {
            BigInteger coefficient = BigInteger.valueOf(Long.MAX_VALUE);
            for (Map.Entry<String, BigInteger> entry : weights.entrySet()) coefficient = coefficient.min(
                    BigInteger.valueOf(values.getOrDefault(entry.getKey(), 0L)).divide(entry.getValue()));
            Set<String> changed = new TreeSet<>();
            for (Map.Entry<String, BigInteger> entry : weights.entrySet()) {
                long after = coefficient.multiply(entry.getValue()).longValueExact();
                if (after < values.getOrDefault(entry.getKey(), 0L)) {
                    values.put(entry.getKey(), after); changed.add(entry.getKey());
                }
            }
            return changed;
        }
    }
}
