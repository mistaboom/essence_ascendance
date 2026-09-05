package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import dev.architectury.platform.Platform;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingManager;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingRegistry;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Exports the shared internal economic analysis plus live-resolution diagnostics.
 * Live values are reported only for verification; they NEVER feed economic analysis.
 * Historical shadow_valuation filename retained for existing CSV comparisons. */
public final class ShadowValuationCsvExporter {

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.ROOT);

    private ShadowValuationCsvExporter() {
    }

    public static ExportReport export(MinecraftServer server) throws IOException {
        long started = System.nanoTime();
        List<ShadowValuationResult> results = ShadowValuationEngine.evaluateAll(server);

        Path directory = Platform.getConfigFolder()
                .resolve(EssenceAscendance.MOD_ID)
                .resolve("valuation_exports");
        Files.createDirectories(directory);

        String timestamp = FILE_TIMESTAMP.format(LocalDateTime.now());
        Path output = directory.resolve("shadow_valuation_" + timestamp + ".csv");

        try (BufferedWriter writer = Files.newBufferedWriter(
                output,
                StandardCharsets.UTF_8
        )) {
            writeHeader(writer);
            for (ShadowValuationResult result : results) {
                writeRow(writer, result);
            }
        }

        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        return new ExportReport(output.toAbsolutePath().normalize(), results.size(), elapsedMillis);
    }

    private static void writeHeader(BufferedWriter writer) throws IOException {
        writeCsvRow(writer, List.of(
                "item_id",
                "namespace",
                "path",
                "total_value",
                "intrinsic_value",
                "confidence_band",
                "confidence_percent",
                "renewability_multiplier",
                "progression_band",
                "advancement_progression_percent",
                "progression_evidence_count",
                "producing_recipe_count",
                "downstream_recipe_count",
                "significant_downstream_recipe_count",
                "cross_mod_downstream_recipe_count",
                "downstream_multiplier",
                "source_block_count",
                "source_entity_count",
                "source_container_count",
                "source_fishing_count",
                "source_trade_count",
                "chosen_recipe_id",
                "chosen_recipe_type",
                "recipe_value_per_output",
                "recipe_output_count",
                "recipe_ingredient_slots",
                "recipe_unique_ingredients",
                "recipe_easy_ingredients",
                "recipe_rare_ingredients",
                "recipe_mod_specific_ingredients",
                "recipe_depth",
                "recipe_transform",
                "offense",
                "defense",
                "vitality",
                "mobility",
                "gathering",
                "utility",
                "offense_percent",
                "defense_percent",
                "vitality_percent",
                "mobility_percent",
                "gathering_percent",
                "utility_percent",
                "downstream_examples",
                "factors",
                "routing_evidence",
                "routing_confidence_band",
                "name_hint_source",
                "name_hint_matches",
                "structured_routing_signals",
                "modeled_acquisition",
                "source_archaeology_count",
                "conservation_status",
                "generated_eligibility",
                "eligibility_reason",
                "live_mapping_source",
                "live_total",
                "live_generation",
                "live_offense", "live_defense", "live_vitality", "live_mobility", "live_gathering", "live_utility"
        ));
    }

    private static void writeRow(
            BufferedWriter writer,
            ShadowValuationResult result
    ) throws IOException {
        GeneratedYieldEligibility.Decision eligibility = ItemEssenceMappingManager.generatedDecision(result);
        ItemEssenceMappingResult live = ItemEssenceMappingRegistry.resolve(
                new ItemStack(BuiltInRegistries.ITEM.getOptional(result.itemId()).orElseThrow()));
        long liveTotal = 0;
        for (long amount : live.outputs().values()) liveTotal = Math.addExact(liveTotal, amount);
        Optional<ShadowValuationResult.RecipeChoice> recipe = result.recipeChoice();
        long total = result.totalValue();
        Map<EssenceDefinition, Long> routed = result.routedEssence();

        long offense = routedValue(routed, EssenceTypes.OFFENSE);
        long defense = routedValue(routed, EssenceTypes.DEFENSE);
        long vitality = routedValue(routed, EssenceTypes.VITALITY);
        long mobility = routedValue(routed, EssenceTypes.MOBILITY);
        long gathering = routedValue(routed, EssenceTypes.GATHERING);
        long utility = routedValue(routed, EssenceTypes.UTILITY);

        String downstreamExamples = result.downstreamExamples().stream()
                .map(Object::toString)
                .collect(Collectors.joining(" | "));
        String factors = String.join(" | ", result.factors());

        writeCsvRow(writer, List.of(
                result.itemId().toString(),
                result.itemId().getNamespace(),
                result.itemId().getPath(),
                Long.toString(total),
                Long.toString(result.intrinsicValue()),
                result.confidenceBand().name(),
                formatDecimal(result.confidence() * 100.0),
                formatDecimal(result.renewabilityMultiplier()),
                result.progressionBand().name(),
                formatDecimal(result.inferredProgressionScore() * 100.0),
                Integer.toString(result.progressionEvidenceCount()),
                Integer.toString(result.producingRecipeCount()),
                Integer.toString(result.downstreamRecipeCount()),
                Integer.toString(result.significantDownstreamRecipeCount()),
                Integer.toString(result.crossModDownstreamRecipeCount()),
                formatDecimal(result.downstreamMultiplier()),
                Integer.toString(result.sourceBlockCount()),
                Integer.toString(result.sourceEntityCount()),
                Integer.toString(result.sourceContainerCount()),
                Integer.toString(result.sourceFishingCount()),
                Integer.toString(result.sourceTradeCount()),
                recipe.map(choice -> choice.recipeId().toString()).orElse(""),
                recipe.map(ShadowValuationResult.RecipeChoice::recipeType).orElse(""),
                recipe.map(choice -> Long.toString(choice.valuePerOutput())).orElse(""),
                recipe.map(choice -> Integer.toString(choice.outputCount())).orElse(""),
                recipe.map(choice -> Integer.toString(choice.ingredientSlots())).orElse(""),
                recipe.map(choice -> Integer.toString(choice.uniqueChosenIngredients())).orElse(""),
                recipe.map(choice -> Integer.toString(choice.easyChosenIngredients())).orElse(""),
                recipe.map(choice -> Integer.toString(choice.rareChosenIngredients())).orElse(""),
                recipe.map(choice -> Integer.toString(choice.modSpecificChosenIngredients())).orElse(""),
                recipe.map(choice -> Integer.toString(choice.depth())).orElse(""),
                recipe.map(choice -> choice.reversibleTransform() ? "REVERSIBLE" : "PROCESSING").orElse(""),
                Long.toString(offense),
                Long.toString(defense),
                Long.toString(vitality),
                Long.toString(mobility),
                Long.toString(gathering),
                Long.toString(utility),
                formatPercent(offense, total),
                formatPercent(defense, total),
                formatPercent(vitality, total),
                formatPercent(mobility, total),
                formatPercent(gathering, total),
                formatPercent(utility, total),
                downstreamExamples,
                factors,
                String.join(" | ", result.routingDiagnostics().evidence()),
                result.routingDiagnostics().confidence().name(),
                result.routingDiagnostics().nameHintSource(),
                String.join(" | ", result.routingDiagnostics().nameHints()),
                String.join(" | ", result.routingDiagnostics().structuredSignals()),
                Boolean.toString(result.modeledAcquisition()),
                Integer.toString(result.sourceArchaeologyCount()),
                result.conservationStatus(),
                eligibility.status().name(),
                eligibility.reason(),
                ItemEssenceMappingRegistry.source(result.itemId()),
                Long.toString(liveTotal),
                Long.toString(ItemEssenceMappingRegistry.generation()),
                Long.toString(live.amountFor(EssenceTypes.OFFENSE)),
                Long.toString(live.amountFor(EssenceTypes.DEFENSE)),
                Long.toString(live.amountFor(EssenceTypes.VITALITY)),
                Long.toString(live.amountFor(EssenceTypes.MOBILITY)),
                Long.toString(live.amountFor(EssenceTypes.GATHERING)),
                Long.toString(live.amountFor(EssenceTypes.UTILITY))
        ));
    }

    private static long routedValue(
            Map<EssenceDefinition, Long> routed,
            EssenceDefinition essence
    ) {
        return routed.getOrDefault(essence, 0L);
    }

    private static String formatPercent(long value, long total) {
        if (total <= 0L || value <= 0L) {
            return "0.000";
        }
        return formatDecimal((double) value * 100.0 / total);
    }

    private static String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static void writeCsvRow(
            BufferedWriter writer,
            List<String> values
    ) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(escape(values.get(i)));
        }
        writer.newLine();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean quote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!quote) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    public record ExportReport(
            Path path,
            int itemCount,
            long elapsedMillis
    ) {
    }
}
