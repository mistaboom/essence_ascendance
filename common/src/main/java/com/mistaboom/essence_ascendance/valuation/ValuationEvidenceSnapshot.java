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
        var timer = new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of());
        for (var value : values) {
            List<AcquisitionSource> rows = new ArrayList<>();
            for (var recipe : index.recipesProducing(BuiltInRegistries.ITEM.get(value.itemId()))) {
                var alternatives = recipe.ingredients().stream().map(input -> input.alternatives().stream()
                        .map(item -> BuiltInRegistries.ITEM.getKey(item).toString()).sorted().toList()).toList();
                inputs.put(recipe.id().toString(), alternatives);
                var availability = new SourceAvailability(recipe.id().toString(), SourceAvailability.Category.UNKNOWN,
                        SourceAvailability.Scope.SHARED, List.of(), List.of(), List.of("loaded recipe; input counts retained in production graph"),
                        timer, timer, false, 1, recipe.expectedOutputCount(),
                        List.of("Ingredient access, station setup and recurring collection rate are not evaluated"));
                rows.add(new AcquisitionSource(recipe.id().toString(), AcquisitionSource.Kind.RECIPE, ProgressionBand.ENTRY,
                        recipe.expectedOutputCount(), false, false, 0, recipe.production().confidence(),
                        alternatives.stream().flatMap(List::stream).distinct().sorted().toList(),
                        "Loaded recipe structure supports a material estimate, not a source/access proof; conservation_complete=" + recipe.production().conservationComplete(), availability));
            }
            sources.put(value.itemId().toString(), List.copyOf(rows));
        }
        var s = index.summary();
        return new ValuationEvidenceSnapshot(Collections.unmodifiableMap(sources), Collections.unmodifiableMap(inputs),
                Map.of("recipes", (long) s.recipeCount(), "unsupported_recipes", (long) s.skippedRecipeCount(),
                        "ingredient_links", (long) s.ingredientLinkCount(), "property_estimates", (long) values.size(), "source_access_proofs", 0L));
    }
}
