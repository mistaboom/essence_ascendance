package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.engine.AcquisitionSource;
import com.mistaboom.essence_ascendance.balance.engine.ProgressionBand;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Read-only, identifier-based projection of the existing acquisition graph for balance providers. */
public record ValuationEvidenceSnapshot(Map<String, List<AcquisitionSource>> sources,
                                        Map<String, List<List<String>>> recipeInputs,
                                        Map<String, Double> entityProgression,
                                        Map<String, Long> summary) {
    public static ValuationEvidenceSnapshot collect(MinecraftServer server, List<ProceduralValuationResult> valuations) {
        com.mistaboom.essence_ascendance.balance.generated.BalancePerformance.increment("valuation_snapshot_collections");
        ProceduralValuationIndex index = ProceduralValuationEngine.generationIndex(server);
        Map<String, List<AcquisitionSource>> sources = new TreeMap<>();
        Map<String, List<List<String>>> recipes = new TreeMap<>();
        Map<String, Double> entityProgression = new TreeMap<>();
        BlockSourceProjection blockSources = new BlockSourceProjection();
        for (ProceduralValuationResult value : valuations) {
            Item item = BuiltInRegistries.ITEM.get(value.itemId());
            List<AcquisitionSource> entries = new ArrayList<>();
            for (var source : index.blockDropSources(item)) {
                entries.add(blockSources.source(source, BuiltInRegistries.BLOCK.get(source.blockId()),
                        index.naturalBlockEvidence(source.blockId())));
            }
            for (var source : index.biologicalSources(item)) entries.add(biologicalSource(source,
                    ProgressionBand.at((int) Math.floor(index.progressionForEntity(source.event().producerId()).score() * 4))));
            for (var source : index.dropSources(item)) {
                entries.add(mobSource(source,
                        source.bossScale() ? ProgressionBand.APEX : ProgressionBand.EARLY, source.expectedCount(),
                        source.repeatableSpawn()));
            }
            for (var source : index.containerLootSources(item)) {
                entries.add(containerSource(source));
            }
            for (var source : index.fishingLootSources(item)) {
                entries.add(fishingSource(source));
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
                var production = recipe.production();
                entries.add(new AcquisitionSource(recipe.id().toString(), production != null && !"vanilla".equals(production.metadata().get("adapter"))
                        ? AcquisitionSource.Kind.MACHINE : AcquisitionSource.Kind.RECIPE,
                        ProgressionBand.ENTRY, recipe.expectedOutputCount(), production != null && "renewable".equals(production.metadata().get("renewability")), false, 0,
                        production == null ? .82 : production.confidence(),
                        choices.stream().flatMap(List::stream).distinct().sorted().toList(),
                        production == null ? "Loaded interaction; alternative ingredient groups retained in shared graph"
                                : "Shared effective production " + production.family() + "; " + production.provider()
                                    + "; acquisition complete=" + production.acquisitionComplete()
                                    + "; setup=" + production.metadata().getOrDefault("setup", "provider-declared prerequisites")
                                    + "; operating=" + production.metadata().getOrDefault("energy", "unknown")
                                    + "; predicates/resources retained in generated production evidence"));
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

    static AcquisitionSource mobSource(ProceduralValuationIndex.DropSource source, ProgressionBand stage,
                                       double expected, boolean renewable) {
        String id = source.entityId().toString();
        var timer = new com.mistaboom.essence_ascendance.balance.engine.SourceAvailability.Timer(
                com.mistaboom.essence_ascendance.balance.engine.SourceAvailability.Applicability.OFF, 0, List.of());
        var unknown = source.complexConditionCount() == 0 ? List.<String>of()
                : List.of("Unresolved entity loot conditions/functions=" + source.complexConditionCount());
        var availability = new com.mistaboom.essence_ascendance.balance.engine.SourceAvailability(id,
                renewable ? com.mistaboom.essence_ascendance.balance.engine.SourceAvailability.Category.CONDITIONAL_RENEWABLE
                        : com.mistaboom.essence_ascendance.balance.engine.SourceAvailability.Category.ACCESS_LIMITED,
                com.mistaboom.essence_ascendance.balance.engine.SourceAvailability.Scope.SHARED,
                List.of(), List.of(), source.signals(), timer, timer, unknown.isEmpty(), source.estimatedChance(), expected, unknown);
        return new AcquisitionSource(id, AcquisitionSource.Kind.MOB_DROP, stage, expected, renewable, false, 0,
                .68, List.of(), String.join("; ", source.signals()), availability);
    }

    static AcquisitionSource fishingSource(ProceduralValuationIndex.FishingLootSource source) {
        String id = source.lootTableId().toString();
        var timer = new com.mistaboom.essence_ascendance.balance.engine.SourceAvailability.Timer(
                com.mistaboom.essence_ascendance.balance.engine.SourceAvailability.Applicability.OFF, 0, List.of());
        var unknown = source.complexConditionCount() == 0 ? List.<String>of()
                : List.of("Unresolved fishing loot conditions/functions=" + source.complexConditionCount());
        var availability = new com.mistaboom.essence_ascendance.balance.engine.SourceAvailability(id,
                com.mistaboom.essence_ascendance.balance.engine.SourceAvailability.Category.CONDITIONAL_RENEWABLE,
                com.mistaboom.essence_ascendance.balance.engine.SourceAvailability.Scope.SHARED,
                List.of(), List.of(), source.signals(), timer, timer, unknown.isEmpty(), source.estimatedChance(), source.expectedCount(), unknown);
        return new AcquisitionSource(id, AcquisitionSource.Kind.FISHING, band(source.progressionBand()), source.expectedCount(),
                true, false, 0, .72, List.of("minecraft:fishing_rod"), String.join("; ", source.signals()), availability);
    }

    static AcquisitionSource containerSource(ProceduralValuationIndex.ContainerLootSource source) {
        return new AcquisitionSource(source.lootTableId().toString(), AcquisitionSource.Kind.LOOT,
                band(source.progressionBand()), source.expectedCount(), source.availability().provenRenewable(), false, 0,
                source.availability().accessProven() ? .78 : .35, List.of(), String.join("; ", source.signals())
                        + "; source_availability=" + source.availability().category() + "; scope=" + source.availability().scope()
                        + "; no population multiplier or passive throughput", source.availability());
    }

    /** Reuses collected placement evidence; a block's loot alone does not prove a natural source. */
    static AcquisitionSource blockSource(ProceduralValuationIndex.BlockDropSource source, Block block,
                                         List<String> naturalEvidence) {
        return blockSource(source, block, naturalEvidence, blockReason(source, naturalEvidence));
    }

    /** Consecutive tool alternatives share full diagnostics without retaining a pack-wide cache. */
    static final class BlockSourceProjection {
        private ResourceLocation currentBlock;
        private final Map<BlockReasonKey, String> reasons = new HashMap<>();

        AcquisitionSource source(ProceduralValuationIndex.BlockDropSource source, Block block,
                                 List<String> naturalEvidence) {
            if (!source.blockId().equals(currentBlock)) {
                reasons.clear();
                currentBlock = source.blockId();
            }
            var key = new BlockReasonKey(source.signals(), naturalEvidence,
                    source.silkTouchRequired(), source.complexConditionCount());
            String reason = reasons.computeIfAbsent(key, ignored -> blockReason(source, naturalEvidence));
            return blockSource(source, block, naturalEvidence, reason);
        }
    }

    private record BlockReasonKey(List<String> signals, List<String> naturalEvidence,
                                  boolean silkTouch, int unresolved) {
        BlockReasonKey {
            signals = List.copyOf(signals);
            naturalEvidence = List.copyOf(naturalEvidence);
        }
    }

    private static AcquisitionSource blockSource(ProceduralValuationIndex.BlockDropSource source, Block block,
                                                  List<String> naturalEvidence, String reason) {
        boolean natural = !naturalEvidence.isEmpty();
        boolean ungated = !source.silkTouchRequired() && source.complexConditionCount() == 0;
        boolean farming = renewableGrowth(block);
        List<String> dependencies = new ArrayList<>();
        if (source.reusableTool() != null)
            dependencies.add(BuiltInRegistries.ITEM.getKey(source.reusableTool()).toString());
        // An unproven block-break path requires the placed block. Keep its original stage semantics:
        // PLAYER_ACTION remains an acquisition observation, not a fabricated loaded crafting recipe.
        if (!natural && block.asItem() != Items.AIR)
            dependencies.add(BuiltInRegistries.ITEM.getKey(block.asItem()).toString());
        return new AcquisitionSource(source.blockId().toString(), natural && ungated
                ? farming ? AcquisitionSource.Kind.FARMING : AcquisitionSource.Kind.WORLD_GENERATION
                : AcquisitionSource.Kind.PLAYER_ACTION,
                band(source.progressionBand()), source.expectedCount(), farming, false, 0, 0.76,
                dependencies, reason);
    }

    private static String blockReason(ProceduralValuationIndex.BlockDropSource source, List<String> naturalEvidence) {
        List<String> reasons = new ArrayList<>(source.signals());
        if (!naturalEvidence.isEmpty()) reasons.add("Natural placement evidence: " + String.join("; ", naturalEvidence));
        else reasons.add("No collected natural-placement evidence; breaking a supplied block is a player action, not an independent natural source");
        if (source.silkTouchRequired()) reasons.add("Silk Touch is required; the source snapshot does not establish accessible enchantment prerequisites");
        if (source.complexConditionCount() > 0) reasons.add("Unresolved harvest conditions: " + source.complexConditionCount()
                + "; conditional player action does not establish directly accessible supply");
        return String.join("; ", reasons);
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
