package com.mistaboom.essence_ascendance.balance.economy;

import com.mistaboom.essence_ascendance.equipment.FractionalIntegerCostService;
import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.config.BalanceOverrides;
import com.mistaboom.essence_ascendance.balance.engine.PackEvidence;
import com.mistaboom.essence_ascendance.balance.engine.Automation;
import com.mistaboom.essence_ascendance.balance.engine.Availability;
import com.mistaboom.essence_ascendance.balance.engine.ProgressionBand;
import com.mistaboom.essence_ascendance.balance.engine.ResourceEvidence;
import com.mistaboom.essence_ascendance.valuation.EssenceRoutingPolicy;

import java.util.List;
import java.util.Map;

/** Dependency-free synthetic graph and exact transaction invariants. */
public final class EconomyInvariantTest {
    private static int assertions;
    public static void main(String[] args) {
        compression(); catalystsAndContainers(); multiOutputAndProbability(); boundedCycles();
        alternatives(); fractionalTransactions(); conversionLosses(); sourcePressure(); sparseRoutingAndBulkResources();
        wholeYieldRounding(); wholeConversionFamilies(); wholeProductionSafety(); wholeProfileGeneration();
        boundedFarmProduction(); nativeResourceProduction();
        System.out.println("EconomyInvariantTest: " + assertions + " assertions passed");
    }

    private static void nativeResourceProduction() {
        var source = new ProductionGraph.Process("source", "extraction", List.of(input("machine", 1, false)),
                List.of(new ProductionGraph.Output("resource", 2, .25, false)), 0, 0, "observed:fixture", .9,
                Map.of(BoundedProductionPolicy.CONSTRAINT, BoundedProductionPolicy.NATIVE_RESOURCE_SOURCE,
                        "renewability", "finite", "operation", "automated", "source_provenance", "observed fixture behavior"));
        var finite = BoundedProductionPolicy.withSourceBudgets(graph(source), Map.of("resource", 10L, "machine", 10000L), 1);
        var renewableMetadata = new java.util.TreeMap<>(source.metadata()); renewableMetadata.put("renewability", "renewable");
        var renewable = BoundedProductionPolicy.withSourceBudgets(graph(withMetadata(source, renewableMetadata)), Map.of("resource", 10L), 1);
        check(finite.processes().getFirst().metadata().get("renewability").equals("finite")
                && renewable.processes().getFirst().metadata().get("renewability").equals("renewable"), "Finite and renewable sources keep distinct factual semantics");
        check(BoundedProductionPolicy.sourceBudgetMicros(finite.processes().getFirst()) == 20 * FractionalAmountService.SCALE,
                "Realized source outputs share frozen proposal ceiling; reusable construction grants no per-output credit");
        check(finite.processes().getFirst().outputs().getFirst().probability() == .25 && source.duration() == null,
                "Chance evidence and unknown duration survive bounded source policy without invented throughput");
        renewableMetadata.put("operations_per_second", "1000");
        var fast = BoundedProductionPolicy.withSourceBudgets(graph(withMetadata(source, renewableMetadata)), Map.of("resource", 10L), 1);
        check(BoundedProductionPolicy.sourceBudgetMicros(fast.processes().getFirst()) < BoundedProductionPolicy.sourceBudgetMicros(renewable.processes().getFirst()),
                "Observed throughput can only discount a frozen source ceiling");
        renewableMetadata.put("operations_per_second", "0");
        rejected(() -> BoundedProductionPolicy.isBoundedSource(withMetadata(source, renewableMetadata)));
    }

    private static void compression() {
        ProductionGraph graph = graph(process("compress", List.of(input("ingot", 9, true)), List.of(output("block", 1))),
                process("decompress", List.of(input("block", 1, true)), List.of(output("ingot", 9))));
        EconomyConservationSolver.Result result = EconomyConservationSolver.solve(graph,
                Map.of("ingot", DissolutionYield.of(1), "block", DissolutionYield.of(15)));
        check(result.yields().get("block").microUnits() <= result.yields().get("ingot").microUnits() * 9,
                "Compression cannot inflate dissolution");
        check(result.invariants().stream().allMatch(EconomyConservationSolver.Invariant::passed), "Both conversion directions conserve");
        check(result.yields().get("ingot").microUnits() == 1_000_000
                        && result.yields().get("block").microUnits() == 9_000_000 && result.passes() < 4,
                "An exact reversible conversion reaches its positive fixed point without artificial micro-unit decay");
        check(result.equals(EconomyConservationSolver.solve(new ProductionGraph(graph.processes().reversed(), List.of()),
                Map.of("block", DissolutionYield.of(15), "ingot", DissolutionYield.of(1)))), "Input iteration does not affect result");
    }

    private static void catalystsAndContainers() {
        EconomyConservationSolver.Result catalyst = EconomyConservationSolver.solve(graph(process("catalyst",
                        List.of(input("ore", 1, true), input("tool", 1, false)), List.of(output("ingot", 1)))),
                Map.of("ore", DissolutionYield.of(10), "tool", DissolutionYield.of(1000), "ingot", DissolutionYield.of(200)));
        check(catalyst.yields().get("ingot").amount() <= 10, "Reusable tool provides no consumed credit");
        check(catalyst.yields().get("tool").amount() == 1000, "Catalyst economic yield is not consumed by solving");
        EconomyConservationSolver.Result containers = EconomyConservationSolver.solve(graph(process("container",
                        List.of(input("milk_bucket", 1, true)), List.of(output("meal", 1), new ProductionGraph.Output("bucket", 1, 1, true)))),
                Map.of("milk_bucket", DissolutionYield.of(10), "meal", DissolutionYield.of(8), "bucket", DissolutionYield.of(7)));
        check(containers.yields().get("meal").microUnits() + containers.yields().get("bucket").microUnits() <= 10_000_000,
                "Returned containers share the input budget");
    }

    private static void multiOutputAndProbability() {
        EconomyConservationSolver.Result multi = EconomyConservationSolver.solve(graph(process("multiple",
                        List.of(input("ore", 1, true)), List.of(output("metal", 2), new ProductionGraph.Output("dust", 3, .25, true)))),
                Map.of("ore", DissolutionYield.of(10), "metal", DissolutionYield.of(8), "dust", DissolutionYield.of(2)));
        check(multi.invariants().getFirst().maximumOutputValue() <= 10, "Every credited byproduct shares one input budget");
        check(multi.invariants().getFirst().expectedOutputValue() < multi.invariants().getFirst().maximumOutputValue(),
                "Expected probability remains distinct from realized safety ceiling");
        EconomicValue value = new EconomicValue(500);
        check(value.amount() == 500, "Yield clamps do not change economic opportunity cost");
    }

    private static void boundedCycles() {
        EconomyConservationSolver.Result gain = EconomyConservationSolver.solve(graph(
                        process("a", List.of(input("x", 1, true)), List.of(output("y", 2))),
                        process("b", List.of(input("y", 1, true)), List.of(output("x", 1)))),
                Map.of("x", DissolutionYield.of(100), "y", DissolutionYield.of(100)));
        check(gain.yields().get("x").microUnits() == 0 && gain.yields().get("y").microUnits() == 0,
                "Gainful circular production has no positive conservative fixed point");
        EconomyConservationSolver.Result slow = EconomyConservationSolver.solve(graph(
                        process("slow", List.of(input("x", 1, true)), List.of(output("x", 1.000001)))),
                Map.of("x", DissolutionYield.of(100_000)));
        check(slow.passes() <= 256 && slow.yields().get("x").microUnits() == 0, "Nonconvergence is bounded and fails closed");
        check(slow.warnings().stream().anyMatch(warning -> warning.contains("bound")), "Bounded-cycle fallback is diagnosed");
    }

    private static void alternatives() {
        EconomyConservationSolver.Result result = EconomyConservationSolver.solve(graph(process("tag",
                        List.of(new ProductionGraph.Input(List.of("rare", "bulk"), 1, true)), List.of(output("crafted", 1)))),
                Map.of("rare", DissolutionYield.of(100), "bulk", DissolutionYield.of(.05), "crafted", DissolutionYield.of(200)));
        check(result.yields().get("crafted").amount() <= .05, "Cheapest tag member bounds a transformation after overrides");
    }

    private static void fractionalTransactions() {
        long carry = 0, total = 0;
        for (int i = 0; i < 100; i++) {
            FractionalAmountService.Resolution step = FractionalAmountService.accumulate(125_000, 1, carry);
            carry = step.nextCarry(); total += step.wholeAmount();
        }
        FractionalAmountService.Resolution batch = FractionalAmountService.accumulate(125_000, 100, 0);
        check(total == batch.wholeAmount() && carry == batch.nextCarry(), "Stacking and individual actions have identical credit");
        check(total == 12 && carry == 500_000, "No floor loss or per-action inflation");
        FractionalAmountService.Resolution preview = FractionalAmountService.accumulate(750_000, 1, carry);
        check(preview.equals(FractionalAmountService.accumulate(750_000, 1, carry)), "Retrying preview without commit cannot advance carry");
        FractionalAmountService.Resolution resumed = FractionalAmountService.accumulate(500_000, 1, carry);
        check(resumed.wholeAmount() == 1 && resumed.nextCarry() == 0, "Persisted carry resumes exactly");
        check(FractionalAmountService.accumulate(Long.MAX_VALUE, 1, 0).wholeAmount() == Long.MAX_VALUE / 1_000_000,
                "Splitting before multiplication prevents false overflow");
        rejected(() -> FractionalAmountService.accumulate(1, 1, 1_000_000));
        rejected(() -> FractionalAmountService.units(Double.NaN));
        rejected(() -> FractionalAmountService.accumulate(Long.MAX_VALUE, Long.MAX_VALUE, 0));
        double costCarry = 0; int paid = 0;
        for (int i = 0; i < 10; i++) {
            FractionalIntegerCostService.Resolution cost = FractionalIntegerCostService.resolve(1, 90, costCarry, 0);
            paid += cost.resolvedCost(); costCarry = cost.nextCarry();
        }
        check(paid == 1, "Existing zero-cost menu efficiency carry remains fair");
    }

    private static void conversionLosses() {
        for (int efficiency : new int[] {1, 1250, 7500, 9999}) {
            for (long target : new long[] {1, 2, 9, 1000, 1_000_000}) {
                long charged = FractionalAmountService.requiredForEfficiency(target, efficiency);
                check(charged > target, "Carrier creation cannot profit at any grade");
                long converted = FractionalAmountService.requiredForEfficiency(charged, 9000);
                check(converted >= charged, "Cross-type conversion adds distinct loss");
            }
        }
        check(FractionalAmountService.requiredForEfficiency(Long.MAX_VALUE, 5000) == 0, "Overflow rejects transaction");
        check(FractionalAmountService.requiredForEfficiency(Long.MAX_VALUE, 10000) == Long.MAX_VALUE, "Exact representable conversion remains possible");
        long nugget = FractionalAmountService.yieldForEfficiencyUnits(11, 9800);
        long ingot = FractionalAmountService.yieldForEfficiencyUnits(99, 9800);
        check(nugget * 9 == ingot, "Carrier compression does not evade extraction loss through rounding");
        check(FractionalAmountService.accumulate(nugget, 9, 0).equals(FractionalAmountService.accumulate(ingot, 1, 0)),
                "Nugget and ingot extraction commit identical whole credits and fractional carry");
        check(FractionalAmountService.yieldForEfficiencyUnits(100, 9800) < 100 * FractionalAmountService.SCALE,
                "Extraction loss remains separate from fabrication cost");
    }

    private static void sourcePressure() {
        EconomicValue value = new EconomicValue(100);
        SourcePressurePolicy.Inputs unknown = new SourcePressurePolicy.Inputs(null, null, null, null, null);
        check(SourcePressurePolicy.evaluate(unknown, value, .65).multiplier() == 1, "Unknown source rates are not invented");
        double fast = SourcePressurePolicy.evaluate(new SourcePressurePolicy.Inputs(100.0, null, null, null, null), value, .65).multiplier();
        double slow = SourcePressurePolicy.evaluate(new SourcePressurePolicy.Inputs(1.0, null, null, null, null), value, .65).multiplier();
        check(fast < slow, "Declared higher throughput lowers marginal dissolution");
        double passive = SourcePressurePolicy.evaluate(new SourcePressurePolicy.Inputs(null, null, null, 8.0, 0.0), value, .65).multiplier();
        double manual = SourcePressurePolicy.evaluate(new SourcePressurePolicy.Inputs(null, null, null, 1.0, 1.0), value, .65).multiplier();
        check(passive < manual, "Parallel passive production receives more pressure");
        double cheap = SourcePressurePolicy.evaluate(new SourcePressurePolicy.Inputs(null, 0.0, 0.0, null, null), value, .65).multiplier();
        double costly = SourcePressurePolicy.evaluate(new SourcePressurePolicy.Inputs(null, 10000.0, 100.0, null, null), value, .65).multiplier();
        check(cheap < costly && costly <= 1, "Setup and marginal costs affect pressure but cannot mint extra value");
        check(SourcePressurePolicy.evaluate(new SourcePressurePolicy.Inputs(1000.0, 0.0, 0.0, 1000.0, 0.0), value, 0).multiplier() == 1,
                "Zero configured pressure disables source quantity penalties");
        rejected(() -> new SourcePressurePolicy.Inputs(null, null, null, null, 2.0));
    }

    private static void sparseRoutingAndBulkResources() {
        Map<String, Double> diamond = Map.of("defense", 76.511273, "gathering", 22.315788,
                "mobility", 5.844611, "offense", 11.157893, "utility", 89.794479, "vitality", 6.375939);
        Map<String, Double> selected = EssenceRoutingPolicy.dominant(diamond, id -> id);
        check(selected.keySet().equals(java.util.Set.of("utility", "defense")),
                "The uploaded diamond's incidental votes cannot produce six Essences");
        check(EssenceRoutingPolicy.dominant(Map.of("vitality", 8.0, "utility", .3), id -> id)
                        .keySet().equals(java.util.Set.of("vitality")),
                "Weak source context does not add an unrelated payout to food");
        check(EssenceRoutingPolicy.dominant(Map.of("defense", 8.0, "vitality", 2.0), id -> id).size() == 2,
                "A substantial secondary function is retained at the documented threshold");
        check(EssenceRoutingPolicy.dominant(Map.of("utility", 1.0), id -> id).size() == 1,
                "The unknown-function fallback remains a single category");
        Map<String, Double> scaled = new java.util.LinkedHashMap<>();
        diamond.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByKey().reversed())
                .forEach(entry -> scaled.put(entry.getKey(), entry.getValue() / 81));
        check(EssenceRoutingPolicy.dominant(scaled, id -> id).keySet().equals(selected.keySet()),
                "Sub-unit reversible forms keep the same categories regardless of scale or iteration order");
        check(EssenceRoutingPolicy.dominant(Map.of("utility", 1.0, "defense", 1.0, "mobility", 1.0), id -> id)
                        .keySet().equals(java.util.Set.of("defense", "mobility")),
                "Tied functional votes resolve deterministically by stable Essence identifier");

        BalanceSettings defaults = BalanceSettings.defaults();
        ResourceEvidence infinite = resource("bulk", Availability.EFFECTIVELY_INFINITE, Automation.SCALABLE, 42);
        check(EconomyGenerator.dissolutionSuitability(infinite, defaults) == 0,
                "The default policy cannot turn a cobblestone-style infinite source into Essence");
        BalanceSettings abundant = settingsWithResourcePolicy(BalanceSettings.ResourcePolicy.ABUNDANCE_AWARE);
        check(EconomyGenerator.dissolutionSuitability(infinite, abundant) > 0,
                "Abundance-aware is an explicit opt-in to discounted infinite-source payouts");
        check(EconomyGenerator.dissolutionSuitability(infinite,
                        settingsWithResourcePolicy(BalanceSettings.ResourcePolicy.CONSERVATIVE)) == 0,
                "Conservative policy also excludes infinite source payouts");
        ResourceEvidence small = resource("finite_nugget", Availability.FINITE, Automation.NONE, .125);
        check(EconomyGenerator.dissolutionSuitability(small, defaults) == 1,
                "A finite small reward is not erased by an arbitrary five-Essence cutoff");
        check(EconomyGenerator.dissolutionSuitability(resource("crop", Availability.RENEWABLE_MANUAL, Automation.NONE, 12), defaults) > 0,
                "Cultivation remains a meaningful distinct source from infinite liquid generation");

        EconomyConservationSolver.Result result = EconomyConservationSolver.solve(graph(
                        process("bulk_compress", List.of(input("bulk", 9, true)), List.of(output("compressed", 1))),
                        process("bulk_decompress", List.of(input("compressed", 1, true)), List.of(output("bulk", 9))),
                        process("bulk_polish", List.of(input("compressed", 1, true)), List.of(output("polished", 2))),
                        process("finite_compress", List.of(input("finite_nugget", 9, true)), List.of(output("finite_ingot", 1))),
                        process("finite_decompress", List.of(input("finite_ingot", 1, true)), List.of(output("finite_nugget", 9)))),
                Map.of("bulk", DissolutionYield.of(0), "compressed", DissolutionYield.of(100),
                        "polished", DissolutionYield.of(100), "finite_nugget", DissolutionYield.of(.125),
                        "finite_ingot", DissolutionYield.of(9)));
        check(result.yields().get("compressed").microUnits() == 0 && result.yields().get("polished").microUnits() == 0,
                "Compression and free processing cannot launder a zero-yield bulk input into Essence");
        check(result.yields().get("finite_nugget").microUnits() == 125_000
                        && result.yields().get("finite_ingot").microUnits() == 1_125_000,
                "Finite fractional rewards survive conservation with their exact compression ratio");
        check(result.invariants().stream().allMatch(EconomyConservationSolver.Invariant::passed),
                "Every bulk exclusion and finite compression path still conserves value");
    }

    private static void wholeYieldRounding() {
        check(EconomyGenerator.roundedWholeYield(.49) == 0, "Sub-half yields become a real zero, not hidden carry");
        check(EconomyGenerator.roundedWholeYield(.5) == 1, "Nearest rounding uses deterministic half-up ties");
        check(EconomyGenerator.roundedWholeYield(10.8) == 11, "Normal rounding is applied to the proposed total");
        check(EconomyGenerator.roundedWholeYield(10.499999) == 10, "Rounding does not inflate values below a half boundary");
        check(EconomyGenerator.exactWholeYield(15, "/test") == 15, "Whole-valued exact overrides are accepted unchanged");
        rejected(() -> EconomyGenerator.exactWholeYield(1.000001, "/test"));
        rejected(() -> EconomyGenerator.exactWholeYield(-1, "/test"));
        rejected(() -> EconomyGenerator.roundedWholeYield(Double.POSITIVE_INFINITY));
        rejected(() -> EconomyGenerator.roundedWholeYield(Long.MAX_VALUE));
        check(EconomyGenerator.scaleRoutes(Map.of("offense", 7L, "defense", 3L), 10)
                        .equals(Map.of("offense", 7L, "defense", 3L)),
                "Whole category allocation exactly preserves a representable proportional split");
        check(EconomyGenerator.scaleRoutes(Map.of("alphabetically_first", 1L, "last", 9L), 1)
                        .equals(Map.of("last", 1L)),
                "Largest fractional entitlement wins the indivisible unit, not alphabetical order");
        check(EconomyGenerator.scaleRoutes(Map.of("z", 1L, "a", 1L), 1).equals(Map.of("a", 1L)),
                "Only genuinely tied remainders use the stable Essence identifier");
        for (long target = 0; target < 100; target++) {
            Map<String, Long> routes = EconomyGenerator.scaleRoutes(Map.of("a", 19L, "b", 13L, "c", 3L), target);
            check(routes.values().stream().mapToLong(Long::longValue).sum() == target,
                    "Every final whole unit is allocated exactly once");
            long paid = 0, carry = 0;
            for (long amount : routes.values()) {
                FractionalAmountService.Resolution credit = FractionalAmountService.accumulate(
                        Math.multiplyExact(amount, FractionalAmountService.SCALE), 64, 0);
                paid += credit.wholeAmount(); carry += credit.nextCarry();
            }
            check(paid == target * 64 && carry == 0,
                    "Displayed whole per-item values exactly match a stack payout with no hidden remainder");
        }
    }

    private static void wholeConversionFamilies() {
        ProductionGraph family = graph(
                process("ingot_from_nugget", List.of(input("nugget", 9, true)), List.of(output("ingot", 1))),
                process("nugget_from_ingot", List.of(input("ingot", 1, true)), List.of(output("nugget", 9))),
                process("block_from_ingot", List.of(input("ingot", 9, true)), List.of(output("block", 1))),
                process("ingot_from_block", List.of(input("block", 1, true)), List.of(output("ingot", 9))));
        Map<String, DissolutionYield> proposed = Map.of("nugget", DissolutionYield.of(3),
                "ingot", DissolutionYield.of(29), "block", DissolutionYield.of(260));
        EconomyConservationSolver.Result result = EconomyConservationSolver.solveWholeUnits(family, proposed);
        check(result.yields().get("nugget").amount() == 3 && result.yields().get("ingot").amount() == 27
                        && result.yields().get("block").amount() == 243,
                "A finite 1:9:81 family reconciles coherently without iterative rounding collapse");
        check(result.passes() < 4 && result.invariants().stream().allMatch(EconomyConservationSolver.Invariant::passed),
                "Whole conversion families reach a safe positive fixed point promptly");
        check(result.equals(EconomyConservationSolver.solveWholeUnits(
                        new ProductionGraph(family.processes().reversed(), List.of()), proposed)),
                "Whole-family generation does not depend on process enumeration order");
        ProductionGraph slottedFamily = graph(
                process("ingot_from_nugget", java.util.Collections.nCopies(9, input("nugget", 1, true)), List.of(output("ingot", 1))),
                process("nugget_from_ingot", List.of(input("ingot", 1, true)), List.of(output("nugget", 9))),
                process("block_from_ingot", java.util.Collections.nCopies(9, input("ingot", 1, true)), List.of(output("block", 1))),
                process("ingot_from_block", List.of(input("block", 1, true)), List.of(output("ingot", 9))));
        EconomyConservationSolver.Result slotted = EconomyConservationSolver.solveWholeUnits(slottedFamily, proposed);
        check(slotted.yields().equals(result.yields()),
                "Actual crafting graphs with nine separate ingredient slots derive the same lossless family as aggregate counts");
        Map<String, Map<String, Long>> categoryWeights = Map.of("nugget", Map.of("offense", 2L, "defense", 1L),
                "ingot", Map.of("offense", 19L, "defense", 10L), "block", Map.of("offense", 190L, "defense", 70L));
        Map<String, Map<String, Long>> familyRoutes = new java.util.TreeMap<>();
        new WholeUnitConversionFamilies(slottedFamily).reconcileRoutes(familyRoutes, categoryWeights, slotted.yields());
        check(familyRoutes.get("ingot").equals(Map.of("offense", 18L, "defense", 9L))
                        && familyRoutes.get("block").equals(Map.of("offense", 162L, "defense", 81L)),
                "Reversible family forms preserve every Essence category exactly, not just the total");
        java.util.ArrayList<ProductionGraph.Process> constrained = new java.util.ArrayList<>(family.processes());
        constrained.add(process("ore_ingot", List.of(input("ore", 1, true)), List.of(output("ingot", 1))));
        Map<String, DissolutionYield> withOre = new java.util.TreeMap<>(proposed);
        withOre.put("ore", DissolutionYield.of(25));
        EconomyConservationSolver.Result bounded = EconomyConservationSolver.solveWholeUnits(
                new ProductionGraph(constrained, List.of()), withOre);
        check(bounded.yields().get("nugget").amount() == 2 && bounded.yields().get("ingot").amount() == 18
                        && bounded.yields().get("block").amount() == 162,
                "A downstream constraint lowers the whole reversible family together, preserving all integer ratios");
        EconomyConservationSolver.Result triangle = EconomyConservationSolver.solveWholeUnits(graph(
                        process("ab", List.of(input("a", 1, true)), List.of(output("b", 3))),
                        process("bc", List.of(input("b", 1, true)), List.of(output("c", 2))),
                        process("ca", List.of(input("c", 6, true)), List.of(output("a", 1)))),
                Map.of("a", DissolutionYield.of(100), "b", DissolutionYield.of(34), "c", DissolutionYield.of(17)));
        check(triangle.yields().get("a").amount() == 96 && triangle.yields().get("b").amount() == 32
                        && triangle.yields().get("c").amount() == 16,
                "Indirect reversible cycles derive their common integer lattice without a direct inverse recipe");
        EconomyConservationSolver.Result lossy = EconomyConservationSolver.solveWholeUnits(graph(
                        process("loss_ab", List.of(input("a", 2, true)), List.of(output("b", 1))),
                        process("loss_ba", List.of(input("b", 1, true)), List.of(output("a", 1)))),
                Map.of("a", DissolutionYield.of(10), "b", DissolutionYield.of(15)));
        check(lossy.yields().get("a").amount() == 10 && lossy.yields().get("b").amount() == 15,
                "A valid lossy conversion cycle is not falsely forced into an equality or disabled");
        rejected(() -> EconomyConservationSolver.solveWholeUnits(family,
                Map.of("nugget", DissolutionYield.of(.5), "ingot", DissolutionYield.of(5))));
    }

    private static void wholeProductionSafety() {
        ProductionGraph graph = graph(
                process("bulk_compress", List.of(input("infinite", 9, true)), List.of(output("compressed", 1))),
                process("bulk_uncompress", List.of(input("compressed", 1, true)), List.of(output("infinite", 9))),
                process("bulk_byproduct", List.of(input("compressed", 1, true)), List.of(output("byproduct", 1))),
                process("multiple", List.of(input("ore", 1, true), input("catalyst", 1, false)),
                        List.of(output("metal", 2), new ProductionGraph.Output("dust", 3, .25, true))),
                process("container", List.of(input("filled_container", 1, true)),
                        List.of(output("food", 1), new ProductionGraph.Output("empty_container", 1, 1, true))),
                process("alternative", List.of(new ProductionGraph.Input(List.of("infinite", "ore"), 1, true)),
                        List.of(output("crafted", 1))));
        EconomyConservationSolver.Result result = EconomyConservationSolver.solveWholeUnits(graph, Map.ofEntries(
                Map.entry("infinite", DissolutionYield.of(0)), Map.entry("compressed", DissolutionYield.of(100)),
                Map.entry("byproduct", DissolutionYield.of(100)), Map.entry("ore", DissolutionYield.of(10)),
                Map.entry("catalyst", DissolutionYield.of(1000)), Map.entry("metal", DissolutionYield.of(8)),
                Map.entry("dust", DissolutionYield.of(2)), Map.entry("filled_container", DissolutionYield.of(10)),
                Map.entry("food", DissolutionYield.of(8)), Map.entry("empty_container", DissolutionYield.of(7)),
                Map.entry("crafted", DissolutionYield.of(100))));
        check(result.yields().values().stream().allMatch(yield -> yield.microUnits() % FractionalAmountService.SCALE == 0),
                "Conservation cannot reintroduce fractional per-item yields");
        check(result.yields().get("compressed").amount() == 0 && result.yields().get("byproduct").amount() == 0
                        && result.yields().get("crafted").amount() == 0,
                "No compression, byproduct, or tag alternative launders a zero-yield infinite source");
        check(result.yields().get("catalyst").amount() == 1000,
                "A reusable catalyst never adds consumed material credit or loses its own value");
        check(result.invariants().stream().allMatch(EconomyConservationSolver.Invariant::passed),
                "Every realized output and returned container shares the final whole input budget");
        EconomyConservationSolver.Result gain = EconomyConservationSolver.solveWholeUnits(graph(
                        process("gain_a", List.of(input("x", 1, true)), List.of(output("y", 2))),
                        process("gain_b", List.of(input("y", 1, true)), List.of(output("x", 1)))),
                Map.of("x", DissolutionYield.of(100), "y", DissolutionYield.of(100)));
        check(gain.yields().get("x").amount() == 0 && gain.yields().get("y").amount() == 0,
                "Gainful production cycles also fail closed under whole-Essence accounting");
        EconomyConservationSolver.Result duplicate = EconomyConservationSolver.solveWholeUnits(graph(
                        process("duplicate", List.of(input("source", 1, true)),
                                List.of(output("product", 1), new ProductionGraph.Output("product", 1, .5, true)))),
                Map.of("source", DissolutionYield.of(10), "product", DissolutionYield.of(10)));
        check(duplicate.yields().get("product").amount() == 5,
                "Duplicate main/byproduct IDs receive one shared reduction, not repeated attenuation");
        EconomyConservationSolver.Result familyOutput = EconomyConservationSolver.solveWholeUnits(graph(
                        process("a_to_b", List.of(input("a", 1, true)), List.of(output("b", 1))),
                        process("b_to_a", List.of(input("b", 1, true)), List.of(output("a", 1))),
                        process("coproduct", List.of(input("source", 1, true)), List.of(output("a", 1), output("b", 1)))),
                Map.of("source", DissolutionYield.of(10), "a", DissolutionYield.of(10), "b", DissolutionYield.of(10)));
        check(familyOutput.yields().get("a").amount() == 5 && familyOutput.yields().get("b").amount() == 5,
                "Two coproducts in a reversible family share one snapshot reduction before family reconciliation");
    }

    private static void wholeProfileGeneration() {
        String offense = "essence_ascendance:offense", defense = "essence_ascendance:defense";
        PackEvidence evidence = new PackEvidence(Map.of(
                "finite", resource("finite", Availability.FINITE, Automation.NONE, 10.8),
                "bulk", resource("bulk", Availability.EFFECTIVELY_INFINITE, Automation.SCALABLE, 42)),
                List.of(), List.of(), Map.of(), List.of(), List.of(), Map.of());
        Map<String, Map<String, Long>> weights = Map.of("finite", Map.of(offense, 7L, defense, 3L),
                "bulk", Map.of(defense, 1L));
        EconomyProfile profile = EconomyGenerator.generate(evidence, graph(), weights,
                BalanceSettings.defaults(), BalanceOverrides.empty());
        check(profile.resources().get("finite").dissolutionYield().amount() == 11
                        && profile.resources().get("finite").routedYields().equals(Map.of(offense, 8.0, defense, 3.0)),
                "The real generation path rounds the total and publishes exactly its whole category allocation");
        check(profile.resources().get("bulk").dissolutionYield().amount() == 0
                        && profile.resources().get("bulk").routedYields().isEmpty(),
                "Generic infinite-source exclusion runs before nearest rounding and stays zero");
        check(profile.equals(EconomyGenerator.generate(evidence, graph(), weights,
                        BalanceSettings.defaults(), BalanceOverrides.empty())),
                "Whole economy generation is deterministic from captured evidence and routing weights");
        EconomyGenerator.validateWhole(profile);
        String pointer = "/economy/resources/finite/routedYields/" + offense;
        try {
            EconomyGenerator.generate(evidence, graph(), weights, BalanceSettings.defaults(),
                    new BalanceOverrides(List.of(), Map.of(pointer, 1.25)));
            throw new AssertionError("Fractional exact-yield override was accepted");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains(pointer) && expected.getMessage().contains("whole Essence"),
                    "Actual generation rejects fractional exact overrides with their exact configuration pointer");
        }
        EconomyProfile legacy = new EconomyProfile(Map.of("finite", new EconomyProfile.ResourceValue(
                new EconomicValue(10.8), DissolutionYield.of(.75), Map.of(offense, .75), List.of())),
                List.of(), List.of(), List.of(), 1, EconomyProcessingPolicy.defaults());
        EconomyGenerator.validate(legacy);
        rejected(() -> EconomyGenerator.validateWhole(legacy));
        ProductionGraph process = graph(process("one_way", List.of(input("source", 1, true)), List.of(output("product", 1))));
        List<EconomyConservationSolver.Invariant> honestRecords = EconomyConservationSolver.validate(process,
                Map.of("source", 1_000_000L, "product", 1_000_000L));
        EconomyProfile forged = new EconomyProfile(Map.of(
                "source", new EconomyProfile.ResourceValue(new EconomicValue(1), DissolutionYield.of(1), Map.of(offense, 1.0), List.of()),
                "product", new EconomyProfile.ResourceValue(new EconomicValue(5), DissolutionYield.of(5), Map.of(offense, 5.0), List.of())),
                honestRecords, process.processes(), List.of(), 1, EconomyProcessingPolicy.defaults());
        rejected(() -> EconomyGenerator.validateWhole(forged));
        PackEvidence familyEvidence = new PackEvidence(Map.of(
                "nugget", resource("nugget", Availability.FINITE, Automation.NONE, 3),
                "ingot", resource("ingot", Availability.FINITE, Automation.NONE, 27)),
                List.of(), List.of(), Map.of(), List.of(), List.of(), Map.of());
        ProductionGraph familyGraph = graph(
                process("compress", java.util.Collections.nCopies(9, input("nugget", 1, true)), List.of(output("ingot", 1))),
                process("decompress", List.of(input("ingot", 1, true)), List.of(output("nugget", 9))));
        Map<String, Map<String, Long>> familyWeights = Map.of("nugget", Map.of(offense, 2L, defense, 1L),
                "ingot", Map.of(offense, 2L, defense, 1L));
        String familyPointer = "/economy/resources/ingot/routedYields/" + offense;
        try {
            EconomyGenerator.generate(familyEvidence, familyGraph, familyWeights, BalanceSettings.defaults(),
                    new BalanceOverrides(List.of(), Map.of(familyPointer, 0L)));
            throw new AssertionError("Family routing silently replaced a requested zero exact override");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains(familyPointer) && expected.getMessage().contains("reversible family"),
                    "Incompatible family-specific exact yields fail with their pointer instead of raising a requested zero");
        }
        EconomyProfile consistentFamily = EconomyGenerator.generate(familyEvidence, familyGraph, familyWeights,
                BalanceSettings.defaults(), new BalanceOverrides(List.of(), Map.of(
                        familyPointer, 0L, "/economy/resources/nugget/routedYields/" + offense, 0L)));
        check(consistentFamily.resources().get("nugget").routedYields().equals(Map.of(defense, 1.0))
                        && consistentFamily.resources().get("ingot").routedYields().equals(Map.of(defense, 9.0)),
                "Consistent whole-category exact overrides preserve zero throughout the reversible family");
    }

    private static ResourceEvidence resource(String id, Availability availability, Automation automation, double value) {
        return new ResourceEvidence(id, ProgressionBand.ENTRY, availability, automation, true, true, value, 1, List.of(), List.of());
    }

    private static void boundedFarmProduction() {
        String essence = "essence_ascendance:utility";
        Map<String, ResourceEvidence> resources = Map.of(
                "bulk", resource("bulk", Availability.EFFECTIVELY_INFINITE, Automation.SCALABLE, 42),
                "polished", resource("polished", Availability.FINITE, Automation.NONE, 100),
                "crop", resource("crop", Availability.RENEWABLE_MANUAL, Automation.PLAYER_GATED, 20),
                "currency", resource("currency", Availability.RENEWABLE_MANUAL, Automation.SCALABLE, 160),
                "armor", resource("armor", Availability.RENEWABLE_MANUAL, Automation.SCALABLE, 260),
                "nugget", resource("nugget", Availability.RENEWABLE_MANUAL, Automation.SCALABLE, 12),
                "ingot", resource("ingot", Availability.RENEWABLE_MANUAL, Automation.SCALABLE, 108),
                "block", resource("block", Availability.RENEWABLE_MANUAL, Automation.SCALABLE, 972));
        PackEvidence evidence = new PackEvidence(resources, List.of(), List.of(), Map.of(), List.of(), List.of(), Map.of());
        Map<String, Map<String, Long>> weights = new java.util.TreeMap<>();
        resources.keySet().forEach(id -> weights.put(id, Map.of(essence, 1L)));
        ProductionGraph graph = graph(
                stockProcess("sell_bulk", List.of(input("bulk", 20, true)), List.of(output("currency", 1)), 16, false),
                stockProcess("sell_crop", List.of(input("crop", 20, true)), List.of(output("currency", 1)), 16, false),
                stockProcess("buy_armor", List.of(input("currency", 24, true)), List.of(output("armor", 1)), 12, false),
                process("polish", List.of(input("bulk", 1, true)), List.of(output("polished", 1))),
                process("recycle", List.of(input("armor", 1, true)), List.of(output("nugget", 1))),
                process("compress_nuggets", List.of(input("nugget", 9, true)), List.of(output("ingot", 1))),
                process("uncompress_ingot", List.of(input("ingot", 1, true)), List.of(output("nugget", 9))),
                process("compress_ingots", List.of(input("ingot", 9, true)), List.of(output("block", 1))),
                process("uncompress_block", List.of(input("block", 1, true)), List.of(output("ingot", 9))));
        EconomyProfile profile = EconomyGenerator.generate(evidence, graph, weights, BalanceSettings.defaults(), BalanceOverrides.empty());
        EconomyProfile noProcesses = EconomyGenerator.generate(evidence, graph(), weights, BalanceSettings.defaults(), BalanceOverrides.empty());
        for (String id : List.of("crop", "currency", "armor", "nugget", "ingot", "block")) {
            double amount = profile.resources().get(id).dissolutionYield().amount();
            check(amount > 0, "Legitimate crop, stock-limited trade and recycled material production remain useful: " + id);
            check(amount <= noProcesses.resources().get(id).dissolutionYield().amount(),
                    "Finite-stock production never inflates an item's source-adjusted ordinary proposal: " + id);
        }
        check(profile.resources().get("bulk").dissolutionYield().amount() == 0
                        && profile.resources().get("polished").dissolutionYield().amount() == 0,
                "A productive merchant does not restore direct bulk fuel or free bulk processing");
        check(profile.resources().get("ingot").dissolutionYield().microUnits() == 9 * profile.resources().get("nugget").dissolutionYield().microUnits()
                        && profile.resources().get("block").dissolutionYield().microUnits() == 9 * profile.resources().get("ingot").dissolutionYield().microUnits(),
                "Restored farm materials preserve exact whole 1:9:81 conversions");
        EconomyGenerator.validateWhole(profile);
        check(profile.equals(EconomyGenerator.generate(evidence, new ProductionGraph(graph.processes().reversed(), List.of()),
                        weights, BalanceSettings.defaults(), BalanceOverrides.empty())),
                "Bounded production remains deterministic independent of process iteration");
        ProductionGraph.Process sell = profile.processes().stream().filter(p -> p.id().equals("sell_bulk")).findFirst().orElseThrow();
        check(sell.inputs().getFirst().count() == 20 && BoundedProductionPolicy.materialInputCount(sell, 0) == 1,
                "Observed prices remain honest while first-slot discount safety is accounted separately");
        check(profile.invariants().stream().filter(i -> i.path().equals("sell_bulk")).findFirst().orElseThrow()
                        .explanation().contains("Consumed material Essence 0; bounded production source Essence"),
                "A zero-material trade explains the independent finite-stock production credit");
        EconomyProfile overridden = EconomyGenerator.generate(evidence, graph, weights, BalanceSettings.defaults(),
                new BalanceOverrides(List.of(), Map.of("/economy/resources/currency/routedYields/" + essence, 100_000L)));
        ProductionGraph.Process overriddenSell = overridden.processes().stream().filter(p -> p.id().equals("sell_bulk")).findFirst().orElseThrow();
        check(BoundedProductionPolicy.sourceBudgetMicros(sell) == BoundedProductionPolicy.sourceBudgetMicros(overriddenSell)
                        && profile.resources().get("currency").dissolutionYield().equals(overridden.resources().get("currency").dissolutionYield()),
                "Exact yield overrides cannot inflate a frozen productive source allowance");

        ProductionGraph small = BoundedProductionPolicy.withSourceBudgets(graph(
                stockProcess("source", List.of(input("bulk", 20, true)), List.of(output("currency", 1)), 8, false)), Map.of("currency", 100L), .65);
        ProductionGraph fast = BoundedProductionPolicy.withSourceBudgets(graph(
                stockProcess("source", List.of(input("bulk", 20, true)), List.of(output("currency", 1)), 800, false)), Map.of("currency", 100L), .65);
        check(BoundedProductionPolicy.sourceBudgetMicros(fast.processes().getFirst())
                        < BoundedProductionPolicy.sourceBudgetMicros(small.processes().getFirst()),
                "Greater declared stock throughput lowers the per-use source allowance rather than adding a farm bonus");
        ProductionGraph wandering = BoundedProductionPolicy.withSourceBudgets(graph(
                stockProcess("limited", List.of(input("currency", 1, true)), List.of(output("crop", 1)), 5, true)), Map.of("crop", 18L), .65);
        check(BoundedProductionPolicy.sourceBudgetMicros(wandering.processes().getFirst()) == 18 * FractionalAmountService.SCALE,
                "Finite wandering stock is recognized without fabricating a renewable spawn/restock rate");
        ProductionGraph repeatedOutput = BoundedProductionPolicy.withSourceBudgets(graph(
                stockProcess("repeated", List.of(input("bulk", 1, true)), List.of(output("currency", 1), output("currency", 1)), 16, false)),
                Map.of("currency", 100L), .65);
        ProductionGraph combinedOutput = BoundedProductionPolicy.withSourceBudgets(graph(
                stockProcess("combined", List.of(input("bulk", 1, true)), List.of(output("currency", 2)), 16, false)),
                Map.of("currency", 100L), .65);
        check(BoundedProductionPolicy.sourceBudgetMicros(repeatedOutput.processes().getFirst())
                        == BoundedProductionPolicy.sourceBudgetMicros(combinedOutput.processes().getFirst()),
                "Splitting the same product into duplicate output entries cannot evade source-throughput pressure");
        ProductionGraph productiveCycle = BoundedProductionPolicy.withSourceBudgets(graph(
                stockProcess("a", List.of(input("crop", 1, true)), List.of(output("currency", 2)), 16, false),
                stockProcess("b", List.of(input("currency", 1, true)), List.of(output("crop", 1)), 16, false)),
                Map.of("crop", 20L, "currency", 20L), .65);
        EconomyConservationSolver.Result cycle = EconomyConservationSolver.solveWholeUnits(productiveCycle,
                Map.of("crop", DissolutionYield.of(20), "currency", DissolutionYield.of(20)));
        check(cycle.yields().get("crop").amount() == 20 && cycle.yields().get("currency").amount() == 20
                        && cycle.invariants().stream().allMatch(EconomyConservationSolver.Invariant::passed),
                "A stock-limited productive trade cycle is not mistaken for free material duplication or compression");
        ProductionGraph.Process dualPrice = stockProcess("dual", List.of(input("currency", 20, true), input("crop", 5, true)),
                List.of(output("armor", 1)), 12, false);
        check(BoundedProductionPolicy.materialInputCount(dualPrice, 0) == 1
                        && BoundedProductionPolicy.materialInputCount(dualPrice, 1) == 5,
                "Only the price slot affected by merchant discounts uses the engine minimum; second payment stays exact");

        Map<String, String> malformed = new java.util.TreeMap<>(sell.metadata());
        malformed.remove("stock_uses");
        rejected(() -> BoundedProductionPolicy.sourceBudgetMicros(withMetadata(sell, malformed)));
        malformed.put("stock_uses", "0");
        rejected(() -> BoundedProductionPolicy.sourceBudgetMicros(withMetadata(sell, malformed)));
        malformed.put("stock_uses", "16"); malformed.put(BoundedProductionPolicy.BUDGET, "0.5");
        rejected(() -> BoundedProductionPolicy.sourceBudgetMicros(withMetadata(sell, malformed)));
        malformed.put(BoundedProductionPolicy.BUDGET, "-1");
        rejected(() -> BoundedProductionPolicy.sourceBudgetMicros(withMetadata(sell, malformed)));
        malformed.remove(BoundedProductionPolicy.CONSTRAINT);
        rejected(() -> BoundedProductionPolicy.sourceBudgetMicros(withMetadata(sell, malformed)));
        Map<String, String> inventedRate = new java.util.TreeMap<>(wandering.processes().getFirst().metadata());
        inventedRate.put("restocks_per_day", "2");
        rejected(() -> BoundedProductionPolicy.sourceBudgetMicros(withMetadata(wandering.processes().getFirst(), inventedRate)));

        ProductionGraph unrestricted = graph(process("duplicate", List.of(input("crop", 1, true)), List.of(output("crop", 2))),
                process("process_duplicate", List.of(input("crop", 1, true)), List.of(output("armor", 1))));
        EconomyConservationSolver.Result duplicate = EconomyConservationSolver.solveWholeUnits(unrestricted,
                Map.of("crop", DissolutionYield.of(20), "armor", DissolutionYield.of(100)));
        check(duplicate.yields().get("crop").amount() == 0 && duplicate.yields().get("armor").amount() == 0,
                "Unconstrained gain and its processed outputs still fail closed instead of masquerading as a farm");
        rejected(() -> EconomyConservationSolver.solveWholeUnits(graph(withMetadata(unrestricted.processes().getFirst(),
                        Map.of(BoundedProductionPolicy.BUDGET, "100000000"))), Map.of("crop", DissolutionYield.of(20))));
    }

    private static ProductionGraph.Process stockProcess(String id, List<ProductionGraph.Input> inputs,
                                                        List<ProductionGraph.Output> outputs, int uses, boolean wandering) {
        Map<String, String> metadata = new java.util.TreeMap<>();
        metadata.put(BoundedProductionPolicy.CONSTRAINT, BoundedProductionPolicy.FINITE_TRADE_STOCK);
        metadata.put("stock_uses", Integer.toString(uses));
        metadata.put("stock_kind", wandering ? "finite_wandering_trader" : "restocking_villager");
        if (!wandering) {
            metadata.put("restocks_per_day", "2"); metadata.put("restock_period_ticks", "24000");
            metadata.put("discount_cost_a_index", "0"); metadata.put("discount_cost_a_minimum", "1");
        }
        return new ProductionGraph.Process(id, "test:trading", inputs, outputs, 0, 0, "test", 1, metadata);
    }

    private static ProductionGraph.Process withMetadata(ProductionGraph.Process process, Map<String, String> metadata) {
        return new ProductionGraph.Process(process.id(), process.family(), process.inputs(), process.outputs(),
                process.processingTicks(), process.externalCost(), process.provider(), process.confidence(), metadata);
    }

    private static BalanceSettings settingsWithResourcePolicy(BalanceSettings.ResourcePolicy policy) {
        BalanceSettings s = BalanceSettings.defaults();
        return new BalanceSettings(s.overallPower(), s.earlyPower(), s.midPower(), s.latePower(), s.apexPower(),
                s.progressionLength(), s.costPressure(), s.partialBuildViability(), s.equipmentShare(), s.nexusShare(), s.skillShare(),
                s.automationPressure(), s.bulkResourcePenalty(), s.conversionLossPressure(), s.compositionSafeguard(),
                s.flightPolicy(), s.miningPolicy(), policy, s.outlierPolicy(), s.warningConfidence(), s.expandedDiagnostics());
    }

    private static ProductionGraph graph(ProductionGraph.Process... processes) { return new ProductionGraph(List.of(processes), List.of()); }
    private static ProductionGraph.Input input(String id, double count, boolean consumed) { return new ProductionGraph.Input(List.of(id), count, consumed); }
    private static ProductionGraph.Output output(String id, double count) { return new ProductionGraph.Output(id, count, 1, false); }
    private static ProductionGraph.Process process(String id, List<ProductionGraph.Input> inputs, List<ProductionGraph.Output> outputs) {
        return new ProductionGraph.Process(id, "test", inputs, outputs, 0, 0, "test", 1, Map.of());
    }
    private static void check(boolean condition, String reason) { assertions++; if (!condition) throw new AssertionError(reason); }
    private static void rejected(Runnable action) {
        assertions++;
        try { action.run(); } catch (IllegalArgumentException | ArithmeticException expected) { return; }
        throw new AssertionError("Unsafe fractional input accepted");
    }
}
