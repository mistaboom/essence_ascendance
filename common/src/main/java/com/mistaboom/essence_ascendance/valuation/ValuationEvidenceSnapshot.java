package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.engine.AcquisitionSource;
import com.mistaboom.essence_ascendance.balance.engine.ProgressionBand;
import com.mistaboom.essence_ascendance.balance.engine.SourceAvailability;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import java.util.*;

/** Advisory recipe facts. Empty source lists mean unexamined access, not impossible acquisition. */
public record ValuationEvidenceSnapshot(Map<String, List<AcquisitionSource>> sources,
                                        Map<String, List<List<String>>> recipeInputs,
                                        Map<String, Long> summary) {
    public static ValuationEvidenceSnapshot collect(MinecraftServer server, List<ProceduralValuationResult> values) {
        var index = ProceduralValuationEngine.generationIndex(server);
        Map<String, List<AcquisitionSource>> sources = new TreeMap<>(); Map<String, List<List<String>>> inputs = new TreeMap<>();
        Map<String, List<String>> requirements = new HashMap<>();
        Map<List<String>, List<String>> alternativeNames = new HashMap<>();
        var timer = new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of());
        for (var value : values) {
            List<AcquisitionSource> rows = new ArrayList<>();
            for (var recipe : index.recipesProducing(BuiltInRegistries.ITEM.get(value.itemId()))) {
                String recipeId = recipe.id().toString();
                // The normalized graph already owns sorted immutable item identities.
                // Reuse them across slots, source rows and byproducts instead of building
                // a second string/list graph from the same native Item alternatives.
                var alternatives = inputs.computeIfAbsent(recipeId, ignored -> recipe.production().inputs().stream()
                        .map(input -> alternativeNames.computeIfAbsent(input.alternatives(), ValuationEvidenceSnapshot::canonicalAlternatives)).toList());
                var requiredItems = requirements.computeIfAbsent(recipeId, ignored ->
                        alternatives.stream().flatMap(List::stream).distinct().sorted().toList());
                var availability = new SourceAvailability(recipe.id().toString(), SourceAvailability.Category.UNKNOWN,
                        SourceAvailability.Scope.SHARED, List.of(), List.of(), List.of("loaded recipe; input counts retained in production graph"),
                        timer, timer, false, 1, recipe.expectedOutputCount(),
                        List.of("Ingredient access, station setup and recurring collection rate are not evaluated"));
                rows.add(new AcquisitionSource(recipe.id().toString(), AcquisitionSource.Kind.RECIPE, ProgressionBand.ENTRY,
                        recipe.expectedOutputCount(), false, false, 0, recipe.production().confidence(),
                        requiredItems,
                        "Loaded recipe structure supports a material estimate, not a source/access proof; conservation_complete=" + recipe.production().conservationComplete(), availability));
            }
            sources.put(value.itemId().toString(), List.copyOf(rows));
        }
        var s = index.summary();
        return new ValuationEvidenceSnapshot(Collections.unmodifiableMap(sources), Collections.unmodifiableMap(inputs),
                Map.of("recipes", (long) s.recipeCount(), "unsupported_recipes", (long) s.skippedRecipeCount(),
                        "ingredient_links", (long) s.ingredientLinkCount(), "property_estimates", (long) values.size(), "source_access_proofs", 0L));
    }
    static List<String> canonicalAlternatives(List<String> alternatives) {
        // Third-party providers may use an implicit minecraft namespace. Preserve the
        // old Item-to-ID diagnostic normalization while sharing already canonical lists.
        if (alternatives.stream().allMatch(id -> id.indexOf(':') > 0)) return alternatives;
        return alternatives.stream().map(net.minecraft.resources.ResourceLocation::parse)
                .map(Object::toString).sorted().toList();
    }
}
