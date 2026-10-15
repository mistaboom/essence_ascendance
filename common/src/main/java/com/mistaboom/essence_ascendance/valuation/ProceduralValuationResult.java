package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Estimated item material value, explicitly separate from proof of survival access. */
public record ProceduralValuationResult(ResourceLocation itemId, long totalValue, long intrinsicValue,
        Optional<RecipeChoice> recipeChoice, double confidence, Map<EssenceDefinition, Long> routedEssence,
        int producingRecipeCount, List<String> factors, RoutingDiagnostics routingDiagnostics,
        ValueEvidence valueEvidence, String conservationStatus) {
    public ProceduralValuationResult {
        recipeChoice = recipeChoice == null ? Optional.empty() : recipeChoice;
        routedEssence = Map.copyOf(routedEssence); factors = List.copyOf(factors);
    }
    public ConfidenceBand confidenceBand() { return confidence >= .76 ? ConfidenceBand.HIGH : confidence >= .52 ? ConfidenceBand.MEDIUM : ConfidenceBand.LOW; }
    public enum ValueEvidence { PROPERTY_RECIPE_ESTIMATE }
    public enum ConfidenceBand { LOW, MEDIUM, HIGH }
    public record RecipeChoice(ResourceLocation recipeId, String recipeType, long valuePerOutput, int outputCount, boolean conservationComplete) { }
    public record RoutingDiagnostics(List<String> evidence, ConfidenceBand confidence) {
        public RoutingDiagnostics { evidence = List.copyOf(evidence); }
    }
}
