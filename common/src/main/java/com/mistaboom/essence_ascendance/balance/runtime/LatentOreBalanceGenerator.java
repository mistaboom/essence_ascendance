package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.economy.EconomyProfile;
import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import com.mistaboom.essence_ascendance.balance.economy.WholeUnitConversionFamilies;
import com.mistaboom.essence_ascendance.balance.engine.AcquisitionSource;
import com.mistaboom.essence_ascendance.balance.engine.Availability;
import com.mistaboom.essence_ascendance.balance.engine.PackEvidence;
import com.mistaboom.essence_ascendance.balance.engine.ProgressionBand;
import com.mistaboom.essence_ascendance.balance.engine.ResourceEvidence;
import com.mistaboom.essence_ascendance.config.LatentOreWorldgenSettings;
import com.mistaboom.essence_ascendance.config.LatentOreWorldgenSettings.DimensionSettings;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Generation-only supply policy, using the same evidence and final yields as the rest of balance. */
public final class LatentOreBalanceGenerator {
    private static final double TARGET_SOURCE_FAMILIES = 4;
    private static final int MINIMUM_ATTEMPTS = 12;
    private static final int MINIMUM_VEIN_SIZE = 9;
    private LatentOreBalanceGenerator() { }

    public record CategorySupply(String essenceId, int sourceFamilies, double effectiveSources, double coverage) { }
    public record Analysis(List<CategorySupply> categories, double multiplier, double referenceYield,
                           List<String> assumptions) {
        public Analysis { categories = List.copyOf(categories); assumptions = List.copyOf(assumptions); }
    }

    public static Analysis analyze(PackEvidence evidence, EconomyProfile economy, Collection<String> essenceIds) {
        Map<String, Map<String, Map<String, Double>>> candidates = new TreeMap<>();
        essenceIds.forEach(id -> candidates.put(id, new TreeMap<>()));
        Map<String, String> materialIds = economy == null ? Map.of()
                : WholeUnitConversionFamilies.canonicalMaterials(new ProductionGraph(economy.processes(), List.of()));
        if (economy != null) for (ResourceEvidence resource : evidence.resources().values()) {
            if (!accessible(resource) || resource.availability() == Availability.ADMINISTRATIVE
                    || resource.availability() == Availability.UNKNOWN || resource.confidence() < 0.5) continue;
            var value = economy.resources().get(resource.itemId());
            if (value == null || value.dissolutionYield().amount() <= 0) continue;
            for (AcquisitionSource source : resource.sources()) {
                double access = sourceWeight(source);
                if (access == 0 || source.confidence() < 0.5 || source.stage().ordinal() > ProgressionBand.EARLY.ordinal()
                        || !(source.expectedOutput() > 0)
                        || (source.rateKnown() && !(source.unitsPerSecond() > 0))
                        || source.dependencies().stream().anyMatch(id -> !accessible(evidence.resources().get(id)))) continue;
                double confidence = Math.clamp(Math.min(resource.confidence(), source.confidence()), 0, 1);
                String family = source.kind().name() + ":" + source.id();
                String material = materialIds.getOrDefault(resource.itemId(), resource.itemId());
                value.routedYields().forEach((essence, yield) -> {
                    if (candidates.containsKey(essence) && yield > 0) {
                        // One usable whole Essence unit is enough; huge payouts cannot disguise a monopoly.
                        double strength = Math.min(1, yield) * confidence * access;
                        if (strength > 0) candidates.get(essence)
                                .computeIfAbsent(family, ignored -> new TreeMap<>())
                                .merge(material, strength, Math::max);
                    }
                });
            }
        }
        List<CategorySupply> categories = new ArrayList<>();
        candidates.forEach((id, sources) -> {
            SupplyMatching matching = matchSupply(sources);
            categories.add(new CategorySupply(id, matching.families(), matching.effectiveSources(),
                    matching.effectiveSources() / TARGET_SOURCE_FAMILIES));
        });
        double weakest = categories.stream().mapToDouble(CategorySupply::coverage).min().orElse(0);
        return new Analysis(categories, 0.75 + 0.75 * (1 - weakest), 1, List.of(
                "Uses final post-conservation routed yields, reachable external ENTRY/EARLY resources, source stages and known dependencies.",
                "Diversity uses maximum-weight matching between observed source events and lossless material families, using existing recipe-family analysis. Each event and material can contribute once through an actual relationship; overlapping outputs, stone/deepslate drops and compressed forms cannot multiply diversity.",
                "Matching stops once four effective sources establish full coverage. Effective-source scores are capped at four; source-family counts describe the matched independent routes needed to establish the reported coverage, not every observed source.",
                "Unknown availability and confidence below 0.5 do not establish abundance. Recipes, manufactured/conditional block breaking and unsupported machine/byproduct paths cannot invent direct sources.",
                "Four confidence-weighted direct source families cover a category. Loot/trade/fishing/mob access weights are conservative policy assumptions, not measured rates.",
                "The least-covered registered Essence controls supply: a missing category gives 1.5x attempts, all covered categories give 0.75x. Large yields in other categories cannot hide a shortage.",
                "Automatic enabled distributions retain at least 12 attempts of size 9. With default inputs, this is 12..24 attempts and no air-exposure discard, aimed at accessible construction and conversion fuel.",
                "Vanilla 1.21.1 iron_middle uses 10 attempts of size 9; diamond uses smaller/buried passes with exposure discard. Attempts and vein size are placement opportunities, never guaranteed ore counts or mining time.",
                "Known rate zero excludes a source; unknown rates remain unknown. Complex processed-only acquisition conservatively requests more ore until supported evidence is available.",
                "Dimension names do not imply progression or abundance. Exact dimension distributions and exact runtime overrides remain authoritative; only newly generated chunks change."));
    }

    private record SupplyLink(int material, double strength) { }
    private record SupplyMatching(int families, double effectiveSources) { }

    /**
     * Sparse maximum-weight bipartite matching, bounded by the evidence needed for full coverage.
     * An alternating path adds unmatched event/material pairs and can undo earlier pairs to make
     * room for a better joint assignment. Shortest residual paths preserve an optimum at each
     * cardinality, so adding an eligible relationship cannot lower the final capped score.
     */
    private static SupplyMatching matchSupply(Map<String, Map<String, Double>> sources) {
        var materialNames = new TreeSet<String>();
        sources.values().forEach(values -> materialNames.addAll(values.keySet()));
        Map<String, Integer> materialIndex = new TreeMap<>();
        materialNames.forEach(id -> materialIndex.put(id, materialIndex.size()));
        List<List<SupplyLink>> links = new ArrayList<>();
        sources.values().forEach(values -> links.add(values.entrySet().stream()
                .map(entry -> new SupplyLink(materialIndex.get(entry.getKey()), entry.getValue())).toList()));
        int sourceCount = links.size(), materialCount = materialIndex.size();
        int[] sourceMatch = new int[sourceCount], materialMatch = new int[materialCount];
        Arrays.fill(sourceMatch, -1);
        Arrays.fill(materialMatch, -1);
        double[] matchedWeight = new double[materialCount];
        int families = 0;
        double effective = 0;
        final double tolerance = 1.0e-12;
        while (effective < TARGET_SOURCE_FAMILIES) {
            double[] distance = new double[sourceCount + materialCount];
            Arrays.fill(distance, Double.POSITIVE_INFINITY);
            int[] parentSource = new int[materialCount];
            Arrays.fill(parentSource, -1);
            double[] parentWeight = new double[materialCount];
            boolean[] queued = new boolean[distance.length];
            var pending = new ArrayDeque<Integer>();
            for (int source = 0; source < sourceCount; source++) if (sourceMatch[source] < 0) {
                distance[source] = 0;
                pending.addLast(source);
                queued[source] = true;
            }
            // Negative forward costs represent gained evidence; reverse costs undo existing pairs.
            // Queue relaxation avoids scanning unrelated edges on every alternating-path step.
            while (!pending.isEmpty()) {
                int node = pending.removeFirst();
                queued[node] = false;
                if (node < sourceCount) {
                    for (SupplyLink link : links.get(node)) {
                        if (sourceMatch[node] == link.material()) continue;
                        int target = sourceCount + link.material();
                        double candidate = distance[node] - link.strength();
                        if (candidate + tolerance >= distance[target]) continue;
                        distance[target] = candidate;
                        parentSource[link.material()] = node;
                        parentWeight[link.material()] = link.strength();
                        if (!queued[target]) { pending.addLast(target); queued[target] = true; }
                    }
                } else {
                    int material = node - sourceCount;
                    int target = materialMatch[material];
                    if (target < 0) continue;
                    double candidate = distance[node] + matchedWeight[material];
                    if (candidate + tolerance >= distance[target]) continue;
                    distance[target] = candidate;
                    if (!queued[target]) { pending.addLast(target); queued[target] = true; }
                }
            }
            int selected = -1;
            double improvement = -tolerance;
            for (int material = 0; material < materialCount; material++) {
                if (materialMatch[material] < 0 && distance[sourceCount + material] < improvement) {
                    selected = material;
                    improvement = distance[sourceCount + material];
                }
            }
            if (selected < 0) break;
            for (int material = selected; material >= 0;) {
                int source = parentSource[material];
                int previous = sourceMatch[source];
                sourceMatch[source] = material;
                materialMatch[material] = source;
                matchedWeight[material] = parentWeight[material];
                if (previous >= 0) { materialMatch[previous] = -1; matchedWeight[previous] = 0; }
                material = previous;
            }
            families++;
            effective = Arrays.stream(matchedWeight).sum();
        }
        return new SupplyMatching(families, Math.min(TARGET_SOURCE_FAMILIES, effective));
    }

    private static boolean accessible(ResourceEvidence resource) {
        return resource != null && resource.reachable() && resource.external()
                && resource.stage().ordinal() <= ProgressionBand.EARLY.ordinal();
    }

    private static double sourceWeight(AcquisitionSource source) {
        return switch (source.kind()) {
            case WORLD_GENERATION, FARMING, PASSIVE_GENERATION, INFINITE_BULK -> 1;
            case MOB_DROP, FISHING -> 0.75;
            case TRADE -> 0.5;
            case LOOT -> 0.25;
            case RECIPE, PLAYER_ACTION, MACHINE, BYPRODUCT, ADMINISTRATIVE -> 0;
        };
    }

    public static LatentOreWorldgenSettings generate(PackEvidence evidence, EconomyProfile economy,
                                                     LatentOreWorldgenSettings inputs) {
        var analysis = analyze(evidence, economy, EssenceRegistry.values().stream().map(e -> e.id().toString()).toList());
        return new LatentOreWorldgenSettings(scale(inputs.overworld(), analysis.multiplier()),
                scale(inputs.nether(), analysis.multiplier()), scale(inputs.end(), analysis.multiplier()),
                inputs.automaticDimensions(), inputs.dimensions());
    }

    private static DimensionSettings scale(DimensionSettings input, double multiplier) {
        if (!input.enabled() || input.veinsPerChunk() == 0) return input;
        return new DimensionSettings(true, Math.max(MINIMUM_VEIN_SIZE, input.veinSize()),
                Math.clamp((int) Math.ceil(input.veinsPerChunk() * multiplier), MINIMUM_ATTEMPTS, 128),
                input.minY(), input.maxY(), input.discardChanceOnAirExposure());
    }

    /** Stored with the profile; exports read it without re-running evidence collection or balance generation. */
    public static JsonObject diagnostics(PackEvidence evidence, EconomyProfile economy,
                                         LatentOreWorldgenSettings inputs, LatentOreWorldgenSettings resolved) {
        var analysis = analyze(evidence, economy, EssenceRegistry.values().stream().map(e -> e.id().toString()).toList());
        JsonObject result = com.mistaboom.essence_ascendance.balance.generated.BalanceDocument.GSON.toJsonTree(analysis).getAsJsonObject();
        result.addProperty("targetEffectiveSources", TARGET_SOURCE_FAMILIES);
        boolean fuel = evidence.resources().values().stream().filter(LatentOreBalanceGenerator::accessible)
                .anyMatch(resource -> economy != null && economy.resources().containsKey(resource.itemId())
                        && economy.resources().get(resource.itemId()).dissolutionYield().amount() > 0);
        result.addProperty("earlyConversionFuelAvailable", fuel);
        if (!fuel) result.getAsJsonArray("assumptions").add(
                "No reachable early external Essence fuel was established. Extra Latent Ore supplies carriers, but cannot create Essence to convert; correct missing source evidence or pack acquisition.");
        result.add("inputs", RuntimeBalanceDefinition.worldgenJson(inputs));
        result.add("resolved", RuntimeBalanceDefinition.worldgenJson(resolved));
        return result;
    }
}
