package com.mistaboom.essence_ascendance.balance.economy;

import com.mistaboom.essence_ascendance.balance.config.BalanceOverrides;
import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.PackEvidence;
import com.mistaboom.essence_ascendance.balance.engine.ResourceEvidence;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.valuation.ProceduralValuationEngine;
import com.mistaboom.essence_ascendance.valuation.ProductionGraphAdapter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class EconomyGenerator {
    private static final Map<String, ProductionProvider> PROVIDERS = new TreeMap<>();
    private EconomyGenerator() { }

    public static synchronized void register(ProductionProvider provider) {
        if (PROVIDERS.putIfAbsent(provider.id(), provider) != null)
            throw new IllegalArgumentException("Duplicate production provider " + provider.id());
    }

    public static EconomyProfile generate(MinecraftServer server, PackEvidence evidence,
                                           BalanceSettings settings, BalanceOverrides overrides) {
        ProductionGraph generic = ProductionGraphAdapter.collect(server);
        Map<String, ProductionGraph.Process> processes = new TreeMap<>();
        generic.processes().forEach(process -> processes.put(process.id(), process));
        List<String> warnings = new ArrayList<>(generic.warnings());
        List<ProductionProvider> providers;
        synchronized (EconomyGenerator.class) { providers = List.copyOf(PROVIDERS.values()); }
        providers.stream().sorted(Comparator.comparingInt((ProductionProvider provider) -> priority(provider, overrides))
                        .thenComparing(ProductionProvider::id))
                .filter(provider -> !disabledProvider(provider, overrides)).forEach(provider -> {
                    ProductionGraph supplied = provider.collect(server, evidence);
                    supplied.processes().forEach(process -> processes.put(process.id(), process));
                    warnings.addAll(supplied.warnings());
                });
        ProductionGraph graph = new ProductionGraph(List.copyOf(processes.values()), warnings);
        Map<String, Map<String, Long>> routeWeights = new TreeMap<>();
        ProceduralValuationEngine.evaluateAll(server).forEach(value -> {
            Map<String, Long> weights = new TreeMap<>();
            value.routedEssence().forEach((essence, weight) -> weights.put(essence.id().toString(), weight));
            routeWeights.put(value.itemId().toString(), weights);
        });
        return generate(evidence, graph, routeWeights, settings, overrides);
    }

    /** Pure generation seam for deterministic replay of captured pack evidence and recipe graphs. */
    public static EconomyProfile generate(PackEvidence evidence, ProductionGraph graph,
                                           Map<String, Map<String, Long>> routeWeights,
                                           BalanceSettings settings, BalanceOverrides overrides) {
        Map<String, ProductionGraph.Process> processes = new TreeMap<>();
        graph.processes().forEach(process -> processes.put(process.id(), process));
        List<String> warnings = new ArrayList<>(graph.warnings());
        applyProcessFacts(processes, overrides, warnings);
        ProductionGraph resolvedGraph = new ProductionGraph(List.copyOf(processes.values()), warnings);

        com.mistaboom.essence_ascendance.balance.engine.EvidenceSink sourceFacts = new com.mistaboom.essence_ascendance.balance.engine.EvidenceSink();
        evidence.facts().forEach(sourceFacts::add);
        Map<String, SourcePressurePolicy.Result> sourcePressure = new TreeMap<>();
        evidence.resources().forEach((id, resource) -> sourcePressure.put(id, sourcePressure(resource, sourceFacts, settings)));
        Map<String, Map<String, Long>> proposedRoutes = new TreeMap<>();
        routeWeights.forEach((itemId, weights) -> {
            ResourceEvidence resource = evidence.resources().get(itemId);
            if (resource == null) return;
            Map<String, Long> routes = new TreeMap<>();
            double total = weights.values().stream().mapToDouble(Long::doubleValue).sum();
            if (resource.reachable() && resource.external() && total > 0) {
                double payout = resource.economicValue() * dissolutionSuitability(resource, settings) * sourcePressure.get(resource.itemId()).multiplier();
                routes.putAll(scaleRoutes(weights, roundedWholeYield(payout)));
            }
            proposedRoutes.put(resource.itemId(), routes);
        });
        evidence.resources().keySet().forEach(id -> proposedRoutes.putIfAbsent(id, new TreeMap<>()));
        Map<String, Long> ordinaryProposals = new TreeMap<>();
        proposedRoutes.forEach((id, routes) -> ordinaryProposals.put(id, sum(routes)));
        resolvedGraph = BoundedProductionPolicy.withSourceBudgets(resolvedGraph, ordinaryProposals, settings.automationPressure());
        applyExactYields(proposedRoutes, evidence, overrides);
        Map<String, DissolutionYield> proposed = new TreeMap<>();
        proposedRoutes.forEach((id, routes) -> proposed.put(id,
                new DissolutionYield(Math.multiplyExact(sum(routes), FractionalAmountService.SCALE))));
        EconomyConservationSolver.Result result = EconomyConservationSolver.solveWholeUnits(resolvedGraph, proposed);
        java.util.Set<String> adjustedItems = new java.util.HashSet<>(result.adjustedItems());
        Map<String, Map<String, Long>> finalWholeRoutes = new TreeMap<>();
        evidence.resources().keySet().forEach(id -> finalWholeRoutes.put(id, scaleRoutes(proposedRoutes.get(id),
                result.yields().getOrDefault(id, new DissolutionYield(0)).microUnits() / FractionalAmountService.SCALE)));
        new WholeUnitConversionFamilies(resolvedGraph).reconcileRoutes(finalWholeRoutes, proposedRoutes, result.yields());
        validateExactYieldBounds(finalWholeRoutes, overrides);
        Map<String, EconomyProfile.ResourceValue> resources = new TreeMap<>();
        evidence.resources().forEach((id, resource) -> {
            DissolutionYield yield = result.yields().getOrDefault(id, new DissolutionYield(0));
            Map<String, Double> routes = new TreeMap<>();
            finalWholeRoutes.get(id).forEach((essence, units) -> {
                if (units > 0) routes.put(essence, units.doubleValue());
            });
            List<String> reasons = new ArrayList<>(resource.warnings());
            reasons.addAll(sourcePressure.get(id).explanations());
            reasons.add("Source-quantity pressure multiplier " + sourcePressure.get(id).multiplier());
            reasons.add("Dissolution suitability " + dissolutionSuitability(resource, settings)
                    + " from " + resource.availability() + "/" + resource.automation() + " under " + settings.resourcePolicy());
            reasons.add("whole_essence_v1: proposed total rounded to nearest whole (half up); recipe conservation"
                    + " and proportional category allocation operate in whole Essence units; displayed yield equals actual credit");
            if (excludesInfiniteSource(resource, settings))
                reasons.add("infinite_source_no_generated_dissolution: effectively infinite sources are not Essence fuel"
                        + " under " + settings.resourcePolicy() + "; abundance_aware permits pressure-discounted yields"
                        + "; explicit yield overrides are still checked against production conservation");
            if (adjustedItems.contains(id)) reasons.add("Final dissolution reduced by production conservation after overrides");
            if (adjustedItems.contains(id) && yield.microUnits() == 0)
                reasons.add("dissolution_disabled_by_conservation: no positive whole payout survives a gainful, zero-credit, or integer conversion constraint; limiting processes: "
                        + String.join(", ", result.limitingProcesses().getOrDefault(id, List.of("bounded_cycle_dependency"))));
            resources.put(id, new EconomyProfile.ResourceValue(new EconomicValue(resource.economicValue()), yield, routes, reasons));
        });
        EconomyProfile profile = new EconomyProfile(resources, result.invariants(), resolvedGraph.processes(), result.warnings(), result.passes(),
                EconomyProcessingPolicy.derive(settings));
        validateWhole(profile);
        return profile;
    }

    private static SourcePressurePolicy.Result sourcePressure(ResourceEvidence resource,
            com.mistaboom.essence_ascendance.balance.engine.EvidenceSink facts, BalanceSettings settings) {
        Double rate = sourceNumber(facts, resource.itemId(), "throughput");
        if (rate == null) {
            java.util.OptionalDouble observed = resource.sources().stream().filter(source -> source.rateKnown())
                    .mapToDouble(source -> source.unitsPerSecond()).max();
            if (observed.isPresent()) rate = observed.getAsDouble();
        }
        return SourcePressurePolicy.evaluate(new SourcePressurePolicy.Inputs(rate,
                sourceNumber(facts, resource.itemId(), "startup_cost"), sourceNumber(facts, resource.itemId(), "marginal_cost"),
                sourceNumber(facts, resource.itemId(), "parallelizability"), sourceNumber(facts, resource.itemId(), "player_attention")),
                new EconomicValue(resource.economicValue()), settings.automationPressure());
    }

    private static Double sourceNumber(com.mistaboom.essence_ascendance.balance.engine.EvidenceSink facts,
                                        String item, String property) {
        var fact = facts.get(com.mistaboom.essence_ascendance.balance.engine.EvidenceFact.Subject.ITEM, item, property);
        return fact != null && fact.value().type() == com.mistaboom.essence_ascendance.balance.engine.EvidenceFact.ValueType.NUMBER
                ? fact.value().number() : null;
    }

    static double dissolutionSuitability(ResourceEvidence resource, BalanceSettings settings) {
        if (!resource.reachable() || !resource.external()) return 0;
        if (excludesInfiniteSource(resource, settings)) return 0;
        double availability = switch (resource.availability()) {
            case ADMINISTRATIVE -> 0;
            case EFFECTIVELY_INFINITE -> Math.pow(.005, settings.bulkResourcePenalty());
            case RENEWABLE_AUTOMATED -> 1 - .85 * settings.bulkResourcePenalty();
            case RENEWABLE_MANUAL -> 1 - .2 * settings.bulkResourcePenalty();
            case UNKNOWN -> .5;
            default -> 1;
        };
        double automation = switch (resource.automation()) {
            case INFINITE -> 1;
            case PASSIVE -> .95;
            case SCALABLE -> .85;
            case BOUNDED -> .35;
            case UNKNOWN -> .2;
            default -> 0;
        };
        double policy = switch (settings.resourcePolicy()) {
            case CONSERVATIVE -> resource.availability() == com.mistaboom.essence_ascendance.balance.engine.Availability.FINITE ? 1 : .75;
            case ABUNDANCE_AWARE -> 1 - .25 * automation;
            case BALANCED -> 1;
        };
        return policy * availability * (1 - automation * settings.automationPressure());
    }

    private static boolean excludesInfiniteSource(ResourceEvidence resource, BalanceSettings settings) {
        return resource.availability() == com.mistaboom.essence_ascendance.balance.engine.Availability.EFFECTIVELY_INFINITE
                && settings.resourcePolicy() != BalanceSettings.ResourcePolicy.ABUNDANCE_AWARE;
    }

    private static void applyExactYields(Map<String, Map<String, Long>> routes,
                                         PackEvidence evidence, BalanceOverrides overrides) {
        overrides.exactValues().forEach((pointer, raw) -> {
            if (!pointer.startsWith("/economy/")) return;
            String[] parts = pointer.split("/", -1);
            if (parts.length != 6 || !parts[2].equals("resources") || !parts[4].equals("routedYields")
                    || !(raw instanceof Number number))
                throw new IllegalArgumentException("Unsupported economy exact override " + pointer
                        + "; use /economy/resources/<itemId>/routedYields/<essenceId> = number");
            String item = unescape(parts[3]);
            String essence = unescape(parts[5]);
            ResourceEvidence resource = evidence.resources().get(item);
            ResourceLocation essenceId = ResourceLocation.tryParse(essence);
            if (resource == null || essenceId == null || EssenceRegistry.get(essenceId).isEmpty())
                throw new IllegalArgumentException("Unknown item or Essence in exact override " + pointer);
            long units = exactWholeYield(number.doubleValue(), pointer);
            if (units > 0 && (!resource.reachable() || !resource.external()))
                throw new IllegalArgumentException("Cannot assign dissolution to excluded/unreachable item " + item
                        + "; correct acquisition facts first");
            routes.get(item).put(essence, units);
        });
    }

    private static String unescape(String pointerToken) { return pointerToken.replace("~1", "/").replace("~0", "~"); }

    private static void validateExactYieldBounds(Map<String, Map<String, Long>> resolved, BalanceOverrides overrides) {
        overrides.exactValues().forEach((pointer, raw) -> {
            if (!pointer.startsWith("/economy/")) return;
            // applyExactYields has already validated pointer shape, IDs, and whole numbers.
            String[] parts = pointer.split("/", -1);
            String item = unescape(parts[3]), essence = unescape(parts[5]);
            long requested = exactWholeYield(((Number) raw).doubleValue(), pointer);
            if (resolved.getOrDefault(item, Map.of()).getOrDefault(essence, 0L) > requested)
                throw new IllegalArgumentException("Exact dissolution override " + pointer
                        + " conflicts with the recipe-derived reversible family Essence proportions;"
                        + " set consistent whole yields for the family's primitive form and its converted forms."
                        + " A requested zero or category limit cannot be silently increased by family alignment.");
        });
    }

    private static int priority(ProductionProvider provider, BalanceOverrides overrides) {
        return overrides.facts().stream().filter(fact -> fact.kind() == BalanceOverrides.SubjectKind.PROVIDER
                        && fact.selector().equals(provider.id())).mapToInt(BalanceOverrides.FactOverride::priority)
                .max().orElse(provider.priority());
    }

    private static boolean disabledProvider(ProductionProvider provider, BalanceOverrides overrides) {
        return overrides.facts().stream().filter(fact -> fact.kind() == BalanceOverrides.SubjectKind.PROVIDER
                        && fact.selector().equals(provider.id())).max(Comparator.comparingInt(BalanceOverrides.FactOverride::priority)
                        .thenComparing(BalanceOverrides.FactOverride::id)).flatMap(fact -> fact.flag("disabled")).orElse(false);
    }

    private static void applyProcessFacts(Map<String, ProductionGraph.Process> processes,
                                           BalanceOverrides overrides, List<String> warnings) {
        List<BalanceOverrides.FactOverride> facts = overrides.facts().stream()
                .filter(fact -> fact.kind() == BalanceOverrides.SubjectKind.RECIPE || fact.kind() == BalanceOverrides.SubjectKind.RECIPE_FAMILY)
                .sorted(Comparator.comparingInt(BalanceOverrides.FactOverride::priority).thenComparing(BalanceOverrides.FactOverride::id)).toList();
        java.util.Set<String> matched = new java.util.HashSet<>();
        for (ProductionGraph.Process before : List.copyOf(processes.values())) {
            Map<String, Object> resolved = new TreeMap<>();
            List<String> applied = new ArrayList<>();
            for (BalanceOverrides.FactOverride fact : facts) {
                if (!fact.selector().equals(fact.kind() == BalanceOverrides.SubjectKind.RECIPE ? before.id() : before.family())) continue;
                matched.add(fact.id());
                resolved.putAll(fact.values());
                applied.add(fact.id());
            }
            if (applied.isEmpty()) continue;
            if (Boolean.TRUE.equals(resolved.get("disabled")) || Boolean.FALSE.equals(resolved.get("attainable"))) {
                processes.remove(before.id());
                continue;
            }
            List<ProductionGraph.Output> outputs = new ArrayList<>();
            for (ProductionGraph.Output output : before.outputs()) {
                double count = output.byproduct() ? output.count() : number(resolved, "output_count", output.count());
                double probability = output.byproduct() ? output.probability() : number(resolved, "probability", output.probability());
                if (count > 0 && probability > 0) outputs.add(new ProductionGraph.Output(output.itemId(), count, probability, output.byproduct()));
            }
            if (outputs.isEmpty()) { processes.remove(before.id()); continue; }
            Map<String, String> metadata = new TreeMap<>(before.metadata());
            metadata.put("overrides", String.join(",", applied));
            processes.put(before.id(), new ProductionGraph.Process(before.id(), before.family(), before.inputs(), outputs,
                    number(resolved, "processing_time", before.processingTicks()), before.externalCost(), before.provider(),
                    number(resolved, "confidence", before.confidence()), metadata));
        }
        for (BalanceOverrides.FactOverride fact : facts)
            if (!matched.contains(fact.id())) warnings.add("Production override " + fact.id() + " matches no loaded process (possibly excluded during acquisition analysis)");
    }

    private static double number(Map<String, Object> values, String key, double fallback) {
        return values.get(key) instanceof Number value ? value.doubleValue() : fallback;
    }

    private static long sum(Map<String, Long> routes) {
        long total = 0;
        for (long value : routes.values()) total = Math.addExact(total, value);
        return total;
    }

    /** Exact largest-remainder apportionment; stable Essence IDs resolve equal remainders. */
    static Map<String, Long> scaleRoutes(Map<String, Long> weights, long target) {
        Map<String, Long> result = new TreeMap<>();
        long total = sum(weights);
        if (target == 0 || total == 0) return result;
        BigInteger denominator = BigInteger.valueOf(total);
        Map<String, BigInteger> remainders = new TreeMap<>();
        long assigned = 0;
        for (Map.Entry<String, Long> entry : weights.entrySet()) {
            if (entry.getValue() <= 0) continue;
            BigInteger[] division = BigInteger.valueOf(target).multiply(BigInteger.valueOf(entry.getValue())).divideAndRemainder(denominator);
            long amount = division[0].longValueExact();
            result.put(entry.getKey(), amount);
            remainders.put(entry.getKey(), division[1]);
            assigned = Math.addExact(assigned, amount);
        }
        List<String> remainderOrder = remainders.keySet().stream().sorted(
                Comparator.<String, BigInteger>comparing(remainders::get).reversed().thenComparing(id -> id)).toList();
        for (String essence : remainderOrder) {
            if (assigned >= target) break;
            result.put(essence, result.get(essence) + 1);
            assigned++;
        }
        if (assigned != target) throw new IllegalStateException("Dissolution apportionment failed");
        result.values().removeIf(amount -> amount == 0);
        return result;
    }

    static long roundedWholeYield(double amount) {
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Invalid proposed dissolution yield");
        long whole = BigDecimal.valueOf(amount).setScale(0, RoundingMode.HALF_UP).longValueExact();
        Math.multiplyExact(whole, FractionalAmountService.SCALE);
        return whole;
    }

    static long exactWholeYield(double amount, String pointer) {
        if (!Double.isFinite(amount) || amount < 0 || amount != Math.floor(amount))
            throw new IllegalArgumentException("Exact dissolution override " + pointer
                    + " must be a nonnegative whole Essence amount; fractional yields are not supported");
        long whole = BigDecimal.valueOf(amount).longValueExact();
        Math.multiplyExact(whole, FractionalAmountService.SCALE);
        return whole;
    }

    /** Strict for newly generated whole-unit profiles; legacy cached fractional profiles use validate. */
    public static void validateWhole(EconomyProfile profile) {
        validate(profile);
        Map<String, Map<String, Long>> wholeRoutes = new TreeMap<>();
        profile.resources().forEach((id, value) -> {
            if (value.dissolutionYield().microUnits() % FractionalAmountService.SCALE != 0)
                throw new IllegalArgumentException("Whole-Essence profile contains fractional dissolution for " + id);
            Map<String, Long> routes = new TreeMap<>();
            value.routedYields().forEach((essence, amount) -> routes.put(essence, exactWholeYield(amount,
                    "/economy/resources/" + id + "/routedYields/" + essence)));
            wholeRoutes.put(id, routes);
        });
        Map<String, Long> published = new TreeMap<>();
        profile.resources().forEach((id, value) -> published.put(id, value.dissolutionYield().microUnits()));
        if (EconomyConservationSolver.validate(new ProductionGraph(profile.processes(), List.of()), published)
                .stream().anyMatch(invariant -> !invariant.passed()))
            throw new IllegalArgumentException("Published whole-Essence yields violate production conservation");
        new WholeUnitConversionFamilies(new ProductionGraph(profile.processes(), List.of())).validateRoutes(wholeRoutes);
    }

    /** Cheap cached-load validation. Never recollects evidence or solves a graph. */
    public static void validate(EconomyProfile profile) {
        if (profile == null || profile.resources().isEmpty() || profile.solverPasses() < 1 || profile.solverPasses() > 256)
            throw new IllegalArgumentException("Incomplete generated economy");
        if (profile.invariants().size() != profile.processes().size()) throw new IllegalArgumentException("Missing economy invariant results");
        for (int i = 0; i < profile.invariants().size(); i++) {
            EconomyConservationSolver.Invariant invariant = profile.invariants().get(i);
            if (!invariant.path().equals(profile.processes().get(i).id()) || !invariant.passed()
                    || !Double.isFinite(invariant.inputValue()) || !Double.isFinite(invariant.expectedOutputValue())
                    || !Double.isFinite(invariant.maximumOutputValue()) || !Double.isFinite(invariant.netGain())
                    || invariant.inputValue() < 0 || invariant.expectedOutputValue() < 0 || invariant.maximumOutputValue() < 0
                    || invariant.maximumOutputValue() > invariant.inputValue())
                throw new IllegalArgumentException("Invalid economy conservation record " + invariant.path());
        }
    }
}
