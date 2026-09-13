package com.mistaboom.essence_ascendance.balance.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Monotone, bounded conservation over the final table, including exact overrides. */
public final class EconomyConservationSolver {
    private static final int MAX_PASSES = 256;
    private EconomyConservationSolver() { }

    public static Result solve(ProductionGraph graph, Map<String, DissolutionYield> proposed) {
        return solve(graph, proposed, 1);
    }

    /** New generated item yields use whole Essence throughout conservation, not a final display floor. */
    public static Result solveWholeUnits(ProductionGraph graph, Map<String, DissolutionYield> proposed) {
        return solve(graph, proposed, FractionalAmountService.SCALE);
    }

    private static Result solve(ProductionGraph graph, Map<String, DissolutionYield> proposed, long quantum) {
        Map<String, Long> values = new TreeMap<>();
        proposed.forEach((id, value) -> {
            if (value.microUnits() % quantum != 0)
                throw new IllegalArgumentException("Whole-Essence conservation received a fractional yield for " + id);
            values.put(id, value.microUnits() / quantum);
        });
        List<String> warnings = new ArrayList<>(graph.warnings());
        Set<String> adjusted = new java.util.TreeSet<>();
        Map<String, Set<String>> limitingPaths = new TreeMap<>();
        WholeUnitConversionFamilies families = quantum == FractionalAmountService.SCALE
                ? new WholeUnitConversionFamilies(graph) : null;
        if (families != null) {
            Set<String> reconciled = families.reconcileAll(values);
            adjusted.addAll(reconciled);
            reconciled.forEach(id -> limitingPaths.computeIfAbsent(id, ignored -> new java.util.TreeSet<>())
                    .add("whole_unit_reversible_family"));
            if (!reconciled.isEmpty()) warnings.add("Whole-Essence accounting reconciled " + reconciled.size()
                    + " reversible material forms together to their recipe-derived integer conversion ratios.");
        }
        int passes = 0;
        for (; passes < MAX_PASSES; passes++) {
            boolean changed = false;
            for (ProductionGraph.Process process : graph.processes()) {
                BigDecimal input = inputUnits(process, values, quantum);
                BigDecimal output = outputUnits(process, values, false);
                if (output.compareTo(input) <= 0) continue;
                Map<String, Long> candidates = new TreeMap<>();
                for (ProductionGraph.Output product : process.outputs()) {
                    long before = values.getOrDefault(product.itemId(), 0L);
                    // Round the final allocation once, with the exact observed
                    // decimal counts. Unconditionally nudging a double ratio
                    // downward destroys exact 9:1 compression fixed points and
                    // eventually misidentifies safe reversible forms as cycles.
                    long after = BigDecimal.valueOf(before).multiply(input)
                            .divide(output, 0, RoundingMode.FLOOR).longValueExact();
                    if (after < before) candidates.put(product.itemId(), after);
                }
                // Evaluate every product from the same pre-process table. A
                // repeated byproduct ID or two outputs in one conversion family
                // must not receive the same reduction a second time.
                values.putAll(candidates);
                Set<String> affected = new java.util.TreeSet<>(candidates.keySet());
                if (families != null) for (String item : candidates.keySet())
                    affected.addAll(families.reconcile(item, values));
                adjusted.addAll(affected);
                for (String item : affected) {
                    Set<String> paths = limitingPaths.computeIfAbsent(item, ignored -> new java.util.TreeSet<>());
                    paths.add(process.id());
                    if (paths.size() > 5) paths.remove(paths.stream().max(String::compareTo).orElseThrow());
                }
                changed |= !affected.isEmpty();
            }
            if (!changed) break;
        }

        // A gainful cycle has no positive conservative fixed point. If bounded
        // relaxation has not converged, disable its outputs and all dependents.
        // This terminates after visiting each item once, including long cycles.
        Set<String> unsafe = new HashSet<>();
        for (ProductionGraph.Process process : graph.processes()) {
            if (outputUnits(process, values, false).compareTo(inputUnits(process, values, quantum)) > 0)
                process.outputs().forEach(output -> unsafe.add(output.itemId()));
        }
        if (!unsafe.isEmpty()) {
            Map<String, Set<String>> downstream = new HashMap<>();
            for (ProductionGraph.Process process : graph.processes())
                for (ProductionGraph.Input input : process.inputs())
                    if (input.consumed()) for (String alternative : input.alternatives())
                        for (ProductionGraph.Output output : process.outputs())
                            downstream.computeIfAbsent(alternative, ignored -> new HashSet<>()).add(output.itemId());
            ArrayDeque<String> pending = new ArrayDeque<>(unsafe);
            while (!pending.isEmpty()) {
                String item = pending.removeFirst();
                for (String dependent : downstream.getOrDefault(item, Set.of()))
                    if (unsafe.add(dependent)) pending.addLast(dependent);
            }
            unsafe.forEach(id -> { values.put(id, 0L); adjusted.add(id); });
            warnings.add("Conservation relaxation reached its bound; disabled " + unsafe.size()
                    + " outputs in gainful cycles/dependent paths. Correct source/recipe evidence to restore them.");
        }
        List<Invariant> invariants = validate(graph, values, quantum);
        if (invariants.stream().anyMatch(result -> !result.passed()))
            throw new IllegalStateException("Final generated dissolution table violates production conservation");
        Map<String, DissolutionYield> resolved = new TreeMap<>();
        values.forEach((id, units) -> resolved.put(id, new DissolutionYield(Math.multiplyExact(units, quantum))));
        if (!adjusted.isEmpty()) warnings.add("Conservation reduced " + adjusted.size()
                + " final dissolution yields after source policy and overrides; economic values are unchanged.");
        long disabled = adjusted.stream().filter(id -> values.getOrDefault(id, 0L) == 0L
                && proposed.getOrDefault(id, new DissolutionYield(0)).microUnits() > 0L).count();
        if (disabled > 0) warnings.add("Conservation disabled dissolution for " + disabled
                + " previously positive items because of gainful cycles, zero-credit dependent paths, or unrepresentable whole-unit conversion families under the recorded process assumptions."
                + " Their economic values remain meaningful; inspect discounted trades and recipe evidence before overriding these results.");
        Map<String, List<String>> paths = new TreeMap<>();
        limitingPaths.forEach((id, ids) -> paths.put(id, List.copyOf(ids)));
        return new Result(Collections.unmodifiableMap(resolved), invariants,
                warnings.stream().sorted().distinct().toList(), List.copyOf(adjusted), Math.min(passes + 1, MAX_PASSES),
                Collections.unmodifiableMap(paths));
    }

    public static List<Invariant> validate(ProductionGraph graph, Map<String, Long> values) {
        return validate(graph, values, 1);
    }

    private static List<Invariant> validate(ProductionGraph graph, Map<String, Long> values, long quantum) {
        List<Invariant> results = new ArrayList<>();
        for (ProductionGraph.Process process : graph.processes()) {
            BigDecimal inputUnits = inputUnits(process, values, quantum);
            BigDecimal maximumUnits = outputUnits(process, values, false);
            double input = inputUnits.doubleValue();
            double maximum = maximumUnits.doubleValue();
            double expected = outputUnits(process, values, true).doubleValue();
            double unitAmount = (double) quantum / FractionalAmountService.SCALE;
            results.add(new Invariant(process.id(), input * unitAmount,
                    expected * unitAmount, maximum * unitAmount,
                    (maximum - input) * unitAmount, maximumUnits.compareTo(inputUnits) <= 0,
                    "Consumed material Essence " + inputUnits.subtract(sourceUnits(process, quantum)).multiply(BigDecimal.valueOf(unitAmount)).stripTrailingZeros().toPlainString()
                            + "; bounded production source Essence " + sourceUnits(process, quantum).multiply(BigDecimal.valueOf(unitAmount)).stripTrailingZeros().toPlainString()
                            + ". Input value is their combined budget; all credited outputs share it. Probability uses expected value for diagnostics and maximum realized output for safety."
                            + (quantum == FractionalAmountService.SCALE ? " Final payouts are exact whole Essence units." : "")));
        }
        return List.copyOf(results);
    }

    private static BigDecimal inputUnits(ProductionGraph.Process process, Map<String, Long> values, long quantum) {
        BigDecimal total = BigDecimal.ZERO;
        for (int index = 0; index < process.inputs().size(); index++) {
            ProductionGraph.Input input = process.inputs().get(index);
            if (!input.consumed()) continue;
            long minimum = Long.MAX_VALUE;
            for (String alternative : input.alternatives()) minimum = Math.min(minimum, values.getOrDefault(alternative, 0L));
            total = total.add(BigDecimal.valueOf(minimum).multiply(BigDecimal.valueOf(
                    BoundedProductionPolicy.materialInputCount(process, index))));
        }
        // Ordinary processing time and energy grant no material credit. Only
        // separately validated finite-stock production has an explicit source
        // allowance, frozen from the uninflated proposal before exact overrides.
        total = total.add(sourceUnits(process, quantum));
        if (!Double.isFinite(total.doubleValue())) throw new IllegalArgumentException("Production input total overflow");
        return total;
    }

    private static BigDecimal sourceUnits(ProductionGraph.Process process, long quantum) {
        return BigDecimal.valueOf(BoundedProductionPolicy.sourceBudgetMicros(process))
                .divide(BigDecimal.valueOf(quantum));
    }

    private static BigDecimal outputUnits(ProductionGraph.Process process, Map<String, Long> values, boolean expected) {
        BigDecimal total = BigDecimal.ZERO;
        for (ProductionGraph.Output output : process.outputs())
            total = total.add(BigDecimal.valueOf(values.getOrDefault(output.itemId(), 0L))
                    .multiply(BigDecimal.valueOf(expected ? output.expectedCount() : output.count())));
        if (!Double.isFinite(total.doubleValue())) throw new IllegalArgumentException("Production output total overflow");
        return total;
    }

    public record Invariant(String path, double inputValue, double expectedOutputValue, double maximumOutputValue,
                            double netGain, boolean passed, String explanation) { }
    public record Result(Map<String, DissolutionYield> yields, List<Invariant> invariants,
                         List<String> warnings, List<String> adjustedItems, int passes,
                         Map<String, List<String>> limitingProcesses) { }
}
