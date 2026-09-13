package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.engine.AcquisitionSource;
import com.mistaboom.essence_ascendance.balance.engine.ProgressionBand;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Read-only, identifier-based projection of the existing acquisition graph for balance providers. */
public record ValuationEvidenceSnapshot(Map<String, List<AcquisitionSource>> sources,
                                        Map<String, List<List<String>>> recipeInputs,
                                        Map<String, Double> entityProgression,
                                        Map<String, Long> summary) {
    public static ValuationEvidenceSnapshot collect(MinecraftServer server, List<ProceduralValuationResult> valuations) {
        ProceduralValuationIndex index = ProceduralValuationEngine.generationIndex(server);
        Map<String, List<AcquisitionSource>> sources = new TreeMap<>();
        Map<String, List<List<String>>> recipes = new TreeMap<>();
        Map<String, Double> entityProgression = new TreeMap<>();
        for (ProceduralValuationResult value : valuations) {
            Item item = BuiltInRegistries.ITEM.get(value.itemId());
            List<AcquisitionSource> entries = new ArrayList<>();
            for (var source : index.blockDropSources(item)) {
                boolean farming = renewableGrowth(BuiltInRegistries.BLOCK.get(source.blockId()));
                entries.add(new AcquisitionSource(source.blockId().toString(), farming
                        ? AcquisitionSource.Kind.FARMING : AcquisitionSource.Kind.WORLD_GENERATION,
                        band(source.progressionBand()), source.expectedCount(), farming, false, 0, 0.76,
                        source.reusableTool() == null ? List.of() : List.of(BuiltInRegistries.ITEM.getKey(source.reusableTool()).toString()),
                        String.join("; ", source.signals())));
            }
            for (var source : index.biologicalSources(item)) entries.add(biologicalSource(source,
                    ProgressionBand.at((int) Math.floor(index.progressionForEntity(source.event().producerId()).score() * 4))));
            for (var source : index.dropSources(item)) {
                entries.add(new AcquisitionSource(source.entityId().toString(), AcquisitionSource.Kind.MOB_DROP,
                        source.bossScale() ? ProgressionBand.APEX : ProgressionBand.EARLY, source.expectedCount(),
                        source.repeatableSpawn(), false, 0, 0.68, List.of(), String.join("; ", source.signals())));
            }
            for (var source : index.containerLootSources(item)) {
                entries.add(new AcquisitionSource(source.lootTableId().toString(), AcquisitionSource.Kind.LOOT,
                        band(source.progressionBand()), source.expectedCount(), false, false, 0,
                        source.structureFrequencyKnown() ? 0.78 : 0.52, List.of(), String.join("; ", source.signals())));
            }
            for (var source : index.fishingLootSources(item)) {
                entries.add(new AcquisitionSource(source.lootTableId().toString(), AcquisitionSource.Kind.FISHING,
                        band(source.progressionBand()), source.expectedCount(), true, false, 0, 0.72,
                        List.of(), String.join("; ", source.signals())));
            }
            for (var source : index.tradeSources(item)) {
                List<String> dependencies = new ArrayList<>();
                for (ItemStack cost : List.of(source.costA(), source.costB())) {
                    if (!cost.isEmpty()) dependencies.add(BuiltInRegistries.ITEM.getKey(cost.getItem()).toString());
                }
                entries.add(new AcquisitionSource(source.traderId() + "/" + source.level(), AcquisitionSource.Kind.TRADE,
                        ProgressionBand.at(Math.min(3, source.level() / 2)), source.outputCount(), true, false, 0,
                        0.62, dependencies, "Deterministically sampled offer; stock and restock limit throughput"));
            }
            for (var recipe : index.recipesProducing(item)) {
                List<List<String>> choices = recipe.ingredients().stream().map(ingredient -> ingredient.alternatives().stream()
                        .map(candidate -> BuiltInRegistries.ITEM.getKey(candidate).toString()).sorted().toList()).toList();
                recipes.put(recipe.id().toString(), choices);
                entries.add(new AcquisitionSource(recipe.id().toString(), AcquisitionSource.Kind.RECIPE,
                        ProgressionBand.ENTRY, recipe.outputCount(), false, false, 0, 0.82,
                        choices.stream().flatMap(List::stream).distinct().sorted().toList(),
                        "Loaded recipe; alternative ingredient groups retained in shared graph"));
            }
            entries.sort(java.util.Comparator.comparing((AcquisitionSource source) -> source.kind().name()).thenComparing(AcquisitionSource::id));
            sources.put(value.itemId().toString(), List.copyOf(entries));
        }
        BuiltInRegistries.ENTITY_TYPE.keySet().stream().sorted(java.util.Comparator.comparing(Object::toString))
                .forEach(id -> entityProgression.put(id.toString(), index.progressionForEntity(id).score()));
        var s = index.summary();
        Map<String, Long> summary = new TreeMap<>();
        summary.put("recipes", (long) s.recipeCount()); summary.put("unsupported_recipes", (long) s.skippedRecipeCount());
        summary.put("ingredient_links", (long) s.ingredientLinkCount()); summary.put("entity_loot_tables", (long) s.entityLootTableCount());
        summary.put("block_loot_tables", (long) s.blockLootTableCount()); summary.put("container_loot_tables", (long) s.containerLootTableCount());
        summary.put("trade_offers", (long) s.tradeOfferCount()); summary.put("advancements", (long) s.advancementCount());
        return new ValuationEvidenceSnapshot(Collections.unmodifiableMap(sources), Collections.unmodifiableMap(recipes),
                Collections.unmodifiableMap(entityProgression), Collections.unmodifiableMap(summary));
    }

    private static ProgressionBand band(ProceduralValuationResult.ProgressionBand band) {
        return switch (band) {
            case OVERWORLD -> ProgressionBand.ENTRY;
            case NETHER -> ProgressionBand.LATE;
            case END, BOSS_SCALE -> ProgressionBand.APEX;
        };
    }

    static AcquisitionSource biologicalSource(ProceduralValuationIndex.BiologicalSource source, ProgressionBand stage) {
        var event = source.event();
        return new AcquisitionSource(event.id().toString(), AcquisitionSource.Kind.FARMING, stage,
                event.count(), true, false, 0, event.confidence(), event.requirements().stream()
                .map(requirement -> BuiltInRegistries.ITEM.getKey(requirement.item()).toString()).toList(),
                event.reason() + "; producer " + event.producerId());
    }

    static boolean renewableGrowth(Block block) {
        return block instanceof CropBlock || block instanceof CocoaBlock || block instanceof NetherWartBlock
                || block instanceof StemBlock || block instanceof SweetBerryBushBlock
                || block instanceof GrowingPlantBlock || block instanceof SugarCaneBlock || block instanceof CactusBlock;
    }
}
