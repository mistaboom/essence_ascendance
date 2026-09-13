package com.mistaboom.essence_ascendance.balance.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Finite merchant stock is a production constraint in its own right. A staffed
 * trading hall can earn value on restock just as a farm earns value on growth;
 * an ordinary material recipe cannot. These allowances never raise an item's
 * proposal and are frozen before exact dissolution overrides are considered.
 */
public final class BoundedProductionPolicy {
    public static final String CONSTRAINT = "production_constraint";
    public static final String FINITE_TRADE_STOCK = "finite_trade_stock";
    public static final String BUDGET = "conservation_source_budget_micros";

    private BoundedProductionPolicy() { }

    public static boolean isBoundedSource(ProductionGraph.Process process) {
        String constraint = process.metadata().get(CONSTRAINT);
        if (constraint == null) {
            if (process.metadata().containsKey(BUDGET))
                throw invalid(process, "source allowance has no finite-stock evidence");
            return false;
        }
        if (!constraint.equals(FINITE_TRADE_STOCK))
            throw invalid(process, "unknown bounded-production constraint " + constraint);
        positiveInteger(process, "stock_uses");
        String kind = process.metadata().get("stock_kind");
        if ("restocking_villager".equals(kind)) {
            positiveInteger(process, "restocks_per_day");
            positiveNumber(process, "restock_period_ticks");
        } else if ("finite_wandering_trader".equals(kind)) {
            if (process.metadata().containsKey("restocks_per_day")
                    || process.metadata().containsKey("restock_period_ticks"))
                throw invalid(process, "a non-restocking trader cannot declare a fabricated replenishment rate");
        } else throw invalid(process, "unknown or missing finite-stock kind");
        if (process.metadata().containsKey("discount_cost_a_minimum")
                || process.metadata().containsKey("discount_cost_a_index")) {
            if (!"restocking_villager".equals(kind))
                throw invalid(process, "discount evidence requires a restocking villager");
            int index = discountIndex(process);
            double minimum = positiveNumber(process, "discount_cost_a_minimum");
            if (!process.inputs().get(index).consumed() || minimum > process.inputs().get(index).count())
                throw invalid(process, "discount minimum must fit the observed consumed first price");
        }
        return true;
    }

    /** Actual offer counts stay in diagnostics; observed engine discounts remain safe. */
    static double materialInputCount(ProductionGraph.Process process, int index) {
        if (process.metadata().containsKey("discount_cost_a_index")) {
            if (!isBoundedSource(process)) throw invalid(process, "discount allowance has no finite-stock evidence");
            if (index == discountIndex(process)) return positiveNumber(process, "discount_cost_a_minimum");
        }
        return process.inputs().get(index).count();
    }

    /** Declared source credit is exact; arbitrary processing time/energy grants none. */
    public static long sourceBudgetMicros(ProductionGraph.Process process) {
        if (!isBoundedSource(process)) return 0;
        String raw = process.metadata().get(BUDGET);
        if (raw == null) throw invalid(process, "finite-stock evidence has no generated source allowance");
        try {
            long budget = Long.parseLong(raw);
            if (budget < 0) throw invalid(process, "negative source allowance");
            return budget;
        } catch (NumberFormatException bad) {
            throw invalid(process, "source allowance must be an exact nonnegative micro-unit integer");
        }
    }

    /**
     * Throughput discounts an already source-adjusted ordinary proposal. It
     * cannot increase one. Actual material input quantities remain separately
     * credited by conservation, and each offer's products share one allowance.
     */
    static ProductionGraph withSourceBudgets(ProductionGraph graph, Map<String, Long> ordinaryProposals,
                                             double automationPressure) {
        List<ProductionGraph.Process> processes = new ArrayList<>();
        List<String> warnings = new ArrayList<>(graph.warnings());
        int bounded = 0;
        for (ProductionGraph.Process process : graph.processes()) {
            if (!isBoundedSource(process)) {
                processes.add(process);
                continue;
            }
            bounded++;
            BigDecimal allowance = BigDecimal.ZERO;
            Map<String, BigDecimal> counts = new TreeMap<>();
            process.outputs().forEach(output -> counts.merge(output.itemId(), BigDecimal.valueOf(output.count()), BigDecimal::add));
            for (Map.Entry<String, BigDecimal> output : counts.entrySet()) {
                Double rate = null;
                if ("restocking_villager".equals(process.metadata().get("stock_kind"))) {
                    // Twenty ticks per second is the engine time unit, not a
                    // generated balancing value or a guessed farm throughput.
                    rate = output.getValue().doubleValue() * positiveInteger(process, "stock_uses")
                            * positiveInteger(process, "restocks_per_day")
                            * 20 / positiveNumber(process, "restock_period_ticks");
                    if (!Double.isFinite(rate)) throw invalid(process, "stock throughput overflow");
                }
                long proposal = ordinaryProposals.getOrDefault(output.getKey(), 0L);
                double pressure = SourcePressurePolicy.evaluate(
                        new SourcePressurePolicy.Inputs(rate, null, null, null, null),
                        new EconomicValue(proposal), automationPressure).multiplier();
                allowance = allowance.add(BigDecimal.valueOf(proposal)
                        .multiply(output.getValue())
                        .multiply(BigDecimal.valueOf(pressure)));
            }
            // One whole shared source allowance also keeps record validation
            // exact when the remaining material credit is zero.
            long units = allowance.setScale(0, RoundingMode.FLOOR).longValueExact();
            long micros = Math.multiplyExact(units, FractionalAmountService.SCALE);
            Map<String, String> metadata = new TreeMap<>(process.metadata());
            metadata.put(BUDGET, Long.toString(micros));
            metadata.put("conservation_source_policy", "bounded_stock_v1: pre-override proposal ceiling; throughput only discounts; no per-item farm bonus");
            processes.add(new ProductionGraph.Process(process.id(), process.family(), process.inputs(), process.outputs(),
                    process.processingTicks(), process.externalCost(), process.provider(), process.confidence(), metadata));
        }
        if (bounded > 0) warnings.add("Conservation recognizes " + bounded
                + " finite-stock merchant production paths. Consumed materials and a separately recorded,"
                + " uninflated source allowance share each output budget. Restock-limited trading can earn Essence"
                + " even from zero-yield bulk inputs; free material conversion and duplication still grant no allowance.");
        return new ProductionGraph(processes, warnings);
    }

    private static int positiveInteger(ProductionGraph.Process process, String key) {
        try {
            int value = Integer.parseInt(process.metadata().get(key));
            if (value > 0) return value;
        } catch (NumberFormatException ignored) { }
        throw invalid(process, key + " must be a positive observed integer");
    }

    private static int discountIndex(ProductionGraph.Process process) {
        try {
            int index = Integer.parseInt(process.metadata().get("discount_cost_a_index"));
            if (index >= 0 && index < process.inputs().size()) return index;
        } catch (NumberFormatException ignored) { }
        throw invalid(process, "discount_cost_a_index must identify an observed input slot");
    }

    private static double positiveNumber(ProductionGraph.Process process, String key) {
        String raw = process.metadata().get(key);
        try {
            double value = raw == null ? Double.NaN : Double.parseDouble(raw);
            if (Double.isFinite(value) && value > 0) return value;
        } catch (NumberFormatException ignored) { }
        throw invalid(process, key + " must be a positive finite observation");
    }

    private static IllegalArgumentException invalid(ProductionGraph.Process process, String detail) {
        return new IllegalArgumentException("Invalid bounded production evidence for " + process.id() + ": " + detail);
    }
}
