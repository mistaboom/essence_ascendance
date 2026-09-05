package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ShadowValuationResult(
        ResourceLocation itemId,
        long totalValue,
        long intrinsicValue,
        Optional<RecipeChoice> recipeChoice,
        double downstreamMultiplier,
        ProgressionBand progressionBand,
        double inferredProgressionScore,
        int progressionEvidenceCount,
        double confidence,
        double renewabilityMultiplier,
        Map<EssenceDefinition, Long> routedEssence,
        int producingRecipeCount,
        int downstreamRecipeCount,
        int significantDownstreamRecipeCount,
        int crossModDownstreamRecipeCount,
        int sourceEntityCount,
        int sourceBlockCount,
        int sourceContainerCount,
        int sourceFishingCount,
        int sourceTradeCount,
        List<ResourceLocation> downstreamExamples,
        List<String> factors,
        RoutingDiagnostics routingDiagnostics,
        boolean modeledAcquisition,
        int sourceArchaeologyCount,
        String conservationStatus
) {

    public ShadowValuationResult {
        recipeChoice = recipeChoice == null ? Optional.empty() : recipeChoice;
        routedEssence = Map.copyOf(routedEssence);
        downstreamExamples = List.copyOf(downstreamExamples);
        factors = List.copyOf(factors);
    }

    public ConfidenceBand confidenceBand() {
        if (confidence >= 0.76) {
            return ConfidenceBand.HIGH;
        }
        if (confidence >= 0.52) {
            return ConfidenceBand.MEDIUM;
        }
        return ConfidenceBand.LOW;
    }

    /** Acquisition/value confidence above is independent of routing confidence. */
    public record RoutingDiagnostics(
            List<String> evidence,
            ConfidenceBand confidence,
            String nameHintSource,
            List<String> nameHints,
            List<String> structuredSignals
    ) {
        public RoutingDiagnostics {
            evidence = List.copyOf(evidence);
            nameHints = List.copyOf(nameHints);
            structuredSignals = List.copyOf(structuredSignals);
        }
    }

    public record RecipeChoice(
            ResourceLocation recipeId,
            String recipeType,
            long valuePerOutput,
            int outputCount,
            int ingredientSlots,
            int uniqueChosenIngredients,
            int easyChosenIngredients,
            int rareChosenIngredients,
            int modSpecificChosenIngredients,
            int depth,
            boolean reversibleTransform
    ) {
    }

    public enum ConfidenceBand {
        LOW,
        MEDIUM,
        HIGH
    }

    public enum ProgressionBand {
        OVERWORLD(0),
        NETHER(1),
        END(2),
        BOSS_SCALE(3);

        private final int rank;

        ProgressionBand(int rank) {
            this.rank = rank;
        }

        public int rank() {
            return rank;
        }

        public static ProgressionBand max(
                ProgressionBand first,
                ProgressionBand second
        ) {
            return first.rank >= second.rank ? first : second;
        }
    }
}
