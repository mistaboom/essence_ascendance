package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.balance.generated.BalancePerformance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/*
 * Immutable runtime indexes over the currently loaded recipes plus block,
 * entity, container/chest, and fishing loot tables. Building this once keeps individual
 * /essence debug balance item calls cheap enough to use interactively while tuning
 * the procedural model.
 */
final class ProceduralValuationIndex {

    private static final String LOOT_TABLE_PREFIX = "loot_table/";

    private final Map<Item, List<RecipeModel>> recipesByOutput;
    private final Map<Item, List<RecipeUse>> recipesByIngredient;
    private final Map<Item, List<DropSource>> dropSourcesByItem;
    private final Map<Item, List<BlockDropSource>> blockDropSourcesByItem;
    private final Map<Item, List<ContainerLootSource>> containerLootSourcesByItem;
    private final Map<Item, List<FishingLootSource>> fishingLootSourcesByItem;
    private final Map<Item, List<BiologicalSource>> biologicalSourcesByItem;
    private final Map<Item, List<Item>> conservationGroups;
    private final Map<Item, List<ConservationEdge>> conservationEdges;
    private final ProceduralNaturalBlockIndex naturalBlockIndex;
    private final ProceduralTradeIndex tradeIndex;
    private final ProceduralProgressionIndex progressionIndex;
    private final Summary summary;

    private ProceduralValuationIndex(
            Map<Item, List<RecipeModel>> recipesByOutput,
            Map<Item, List<RecipeUse>> recipesByIngredient,
            Map<Item, List<DropSource>> dropSourcesByItem,
            Map<Item, List<BlockDropSource>> blockDropSourcesByItem,
            Map<Item, List<ContainerLootSource>> containerLootSourcesByItem,
            Map<Item, List<FishingLootSource>> fishingLootSourcesByItem,
            Map<Item, List<BiologicalSource>> biologicalSourcesByItem,
            Map<Item, List<Item>> conservationGroups,
            Map<Item, List<ConservationEdge>> conservationEdges,
            ProceduralNaturalBlockIndex naturalBlockIndex,
            ProceduralTradeIndex tradeIndex,
            ProceduralProgressionIndex progressionIndex,
            Summary summary
    ) {
        this.recipesByOutput = freezeLists(recipesByOutput);
        this.recipesByIngredient = freezeLists(recipesByIngredient);
        this.dropSourcesByItem = freezeLists(dropSourcesByItem);
        this.blockDropSourcesByItem = freezeLists(blockDropSourcesByItem);
        this.containerLootSourcesByItem = freezeLists(containerLootSourcesByItem);
        this.fishingLootSourcesByItem = freezeLists(fishingLootSourcesByItem);
        this.biologicalSourcesByItem = freezeLists(biologicalSourcesByItem);
        this.conservationGroups = freezeLists(conservationGroups);
        this.conservationEdges = freezeLists(conservationEdges);
        this.naturalBlockIndex = naturalBlockIndex;
        this.tradeIndex = tradeIndex;
        this.progressionIndex = progressionIndex == null ? ProceduralProgressionIndex.empty() : progressionIndex;
        this.summary = summary;
    }

    static ProceduralValuationIndex build(MinecraftServer server, GenerationDataSnapshot data) {
        Map<Item, List<RecipeModel>> byOutput = new IdentityHashMap<>();
        Map<Item, List<RecipeUse>> byIngredient = new IdentityHashMap<>();
        Map<Item, List<DropSource>> drops = new IdentityHashMap<>();
        Map<Item, List<BlockDropSource>> blockDrops = new IdentityHashMap<>();
        Map<Item, List<ContainerLootSource>> containerLoot = new IdentityHashMap<>();
        Map<Item, List<FishingLootSource>> fishingLoot = new IdentityHashMap<>();

        int recipeCount = 0;
        int skippedRecipes = 0;
        int ingredientLinks = 0;
        Set<ResourceLocation> indexedRecipeIds = new HashSet<>();

        try (var phase = BalancePerformance.phase("shared_production_recipe_projection")) {
            for (var process : data.production().processes()) {
                if (process.metadata().containsKey("quest_id")) continue;
                ResourceLocation familyId = ResourceLocation.tryParse(process.family());
                RecipeType<?> type = familyId != null && BuiltInRegistries.RECIPE_TYPE.containsKey(familyId)
                        ? BuiltInRegistries.RECIPE_TYPE.get(familyId) : null;
                List<RecipeModel> models = productionModels(process, type);
                if (models.isEmpty()) { skippedRecipes++; continue; }
                for (RecipeModel model : models) {
                    byOutput.computeIfAbsent(model.outputItem(), ignored -> new ArrayList<>()).add(model);
                    for (IngredientChoice ingredient : model.ingredients()) for (Item candidate : ingredient.alternatives()) {
                        byIngredient.computeIfAbsent(candidate, ignored -> new ArrayList<>()).add(new RecipeUse(model));
                        ingredientLinks++;
                    }
                    recipeCount++;
                }
            }
        }

        ProceduralNaturalBlockIndex naturalBlockIndex;
        try (var phase = BalancePerformance.phase("natural_block_index")) {
            naturalBlockIndex = ProceduralNaturalBlockIndex.build(data);
        }
        // Positive runtime interaction relationships (no hand-authored item prices).
        try (var phase = BalancePerformance.phase("interaction_recipe_discovery")) {
        for (RecipeModel model : ProceduralInteractionRecipes.discover(server, naturalBlockIndex)) {
            if (!ValuationGenerationInputs.recipeAllowed(model.id(), model.type()) || !ValuationGenerationInputs.itemAllowed(model.outputItem())) continue;
            byOutput.computeIfAbsent(model.outputItem(), ignored -> new ArrayList<>()).add(model);
            for (IngredientChoice ingredient : model.ingredients()) {
                for (Item candidate : ingredient.alternatives()) {
                    byIngredient.computeIfAbsent(candidate, ignored -> new ArrayList<>()).add(new RecipeUse(model));
                    ingredientLinks++;
                }
            }
            recipeCount++;
        }
        }

        Map<Item, List<ConservationEdge>> conservationEdges;
        Map<Item, List<Item>> conservationGroups;
        try (var phase = BalancePerformance.phase("conversion_family_graph")) {
            conservationEdges = buildConservationEdges(byOutput);
            conservationGroups = buildConservationGroups(conservationEdges);
        }
        ProceduralStructureIndex structureIndex;
        try (var phase = BalancePerformance.phase("structure_index")) {
            structureIndex = ProceduralStructureIndex.build(server, data);
        }
        ProceduralMobSpawnIndex mobSpawnIndex;
        try (var phase = BalancePerformance.phase("mob_spawn_index")) {
            mobSpawnIndex = ProceduralMobSpawnIndex.build(data, structureIndex);
        }
        Map<Item, List<BiologicalSource>> biologicalSources = biologicalSources(mobSpawnIndex);
        ProceduralTradeIndex tradeIndex;
        try (var phase = BalancePerformance.phase("trade_index")) {
            tradeIndex = ProceduralTradeIndex.build(server);
        }

        int lootTablesScanned;
        try (var phase = BalancePerformance.phase("entity_loot_index")) {
            lootTablesScanned = scanEntityLootTables(server, drops, mobSpawnIndex);
            addVanillaHardcodedEntitySources(drops, mobSpawnIndex);
        }
        int dropLinks = drops.values().stream().mapToInt(List::size).sum();
        int blockLootTablesScanned;
        try (var phase = BalancePerformance.phase("block_loot_index")) {
            blockLootTablesScanned = scanBlockLootTables(server, blockDrops);
        }
        int blockDropLinks = blockDrops.values().stream().mapToInt(List::size).sum();
        int containerLootTablesScanned;
        try (var phase = BalancePerformance.phase("container_loot_index")) {
            containerLootTablesScanned = scanContainerLootTables(server, containerLoot, structureIndex);
            if (data.structureEligible(ResourceLocation.parse("minecraft:end_city")))
                addVanillaFixedStructureSources(containerLoot, structureIndex, data);
        }
        int containerLootLinks = containerLoot.values().stream().mapToInt(List::size).sum();
        int fishingLootTablesScanned;
        try (var phase = BalancePerformance.phase("fishing_loot_index")) {
            fishingLootTablesScanned = scanFishingLootTables(server, fishingLoot);
        }
        int fishingLootLinks = fishingLoot.values().stream().mapToInt(List::size).sum();
        ProceduralProgressionIndex progressionIndex;
        try (var phase = BalancePerformance.phase("advancement_progression_index")) {
            progressionIndex = ProceduralProgressionIndex.build(server);
        }
        ProceduralProgressionIndex.Summary progressionSummary = progressionIndex.summary();

        byOutput.values().forEach(list -> list.sort(Comparator.comparing(model -> model.id().toString())));
        byIngredient.values().forEach(list -> list.sort(Comparator.comparing(use -> use.recipe().id().toString())));
        drops.values().forEach(list -> list.sort(Comparator.comparing(source -> source.entityId().toString())));
        blockDrops.values().forEach(list -> list.sort(Comparator.comparing(source -> source.blockId().toString())));
        containerLoot.values().forEach(list -> list.sort(Comparator.comparing(source -> source.lootTableId().toString())));
        fishingLoot.values().forEach(list -> list.sort(Comparator.comparing(source -> source.lootTableId().toString())));

        Summary summary = new Summary(
                recipeCount,
                skippedRecipes,
                byOutput.size(),
                ingredientLinks,
                lootTablesScanned,
                dropLinks,
                blockLootTablesScanned,
                blockDropLinks,
                containerLootTablesScanned,
                containerLootLinks,
                fishingLootTablesScanned,
                fishingLootLinks,
                tradeIndex.professionTableCount(),
                tradeIndex.listingCount(),
                tradeIndex.offerCount(),
                progressionSummary.advancementCount(),
                progressionSummary.consideredAdvancementCount(),
                progressionSummary.treeCount(),
                progressionSummary.referenceCount(),
                progressionSummary.itemReferenceCount(),
                progressionSummary.entityReferenceCount(),
                progressionSummary.dimensionReferenceCount()
        );

        EssenceAscendance.LOGGER.info(
                "Procedural valuation index built: {} recipes ({} skipped), {} outputs, {} ingredient links, {} entity loot tables, {} entity-drop links, {} block loot tables, {} block-drop links, {} container loot tables, {} container item-source links, {} fishing roots, {} fishing item-source links, {} trade listings / {} sampled offers",
                summary.recipeCount(),
                summary.skippedRecipeCount(),
                summary.outputItemCount(),
                summary.ingredientLinkCount(),
                summary.entityLootTableCount(),
                summary.dropSourceLinkCount(),
                summary.blockLootTableCount(),
                summary.blockDropSourceLinkCount(),
                summary.containerLootTableCount(),
                summary.containerLootSourceLinkCount(),
                summary.fishingLootTableCount(),
                summary.fishingLootSourceLinkCount(),
                summary.tradeListingCount(),
                summary.tradeOfferCount()
        );

        return new ProceduralValuationIndex(
                byOutput,
                byIngredient,
                drops,
                blockDrops,
                containerLoot,
                fishingLoot,
                biologicalSources,
                conservationGroups,
                conservationEdges,
                naturalBlockIndex,
                tradeIndex,
                progressionIndex,
                summary
        );
    }

    List<RecipeModel> recipesProducing(Item item) {
        return recipesByOutput.getOrDefault(item, List.of());
    }

    List<RecipeUse> recipesUsing(Item item) {
        return recipesByIngredient.getOrDefault(item, List.of());
    }

    List<DropSource> dropSources(Item item) {
        return dropSourcesByItem.getOrDefault(item, List.of()).stream()
                .filter(source -> ValuationGenerationInputs.sourceAllowed(source.entityId().toString())).toList();
    }

    List<BiologicalSource> biologicalSources(Item item) {
        return biologicalSourcesByItem.getOrDefault(item, List.of()).stream()
                .filter(source -> ValuationGenerationInputs.sourceAllowed(source.event().id().toString())
                        && ValuationGenerationInputs.sourceAllowed(source.event().producerId().toString())).toList();
    }

    static Map<Item, List<BiologicalSource>> biologicalSources(ProceduralMobSpawnIndex spawnIndex) {
        Map<Item, List<BiologicalSource>> result = new IdentityHashMap<>();
        for (BiologicalAcquisitionSources.Source event : BiologicalAcquisitionSources.all()) {
            if (!ValuationGenerationInputs.itemAllowed(event.output())) continue;
            var spawn = spawnIndex == null ? ProceduralMobSpawnIndex.SpawnAvailability.UNKNOWN
                    : spawnIndex.forEntity(event.producerId());
            result.computeIfAbsent(event.output(), ignored -> new ArrayList<>())
                    .add(new BiologicalSource(event, spawn.multiplier(), spawn.signals()));
        }
        return freezeLists(result);
    }

    List<BlockDropSource> blockDropSources(Item item) {
        return blockDropSourcesByItem.getOrDefault(item, List.of()).stream()
                .filter(source -> {
                    if (!ValuationGenerationInputs.sourceAllowed(source.blockId().toString())) return false;
                    Block sourceBlock = BuiltInRegistries.BLOCK.getOptional(source.blockId()).orElse(null);
                    if (sourceBlock == null) return false;
                    return sourceBlock.asItem() != item
                            || (naturalBlockIndex != null && naturalBlockIndex.contains(source.blockId()));
                })
                .toList();
    }

    List<String> naturalBlockEvidence(ResourceLocation blockId) {
        return naturalBlockIndex == null ? List.of() : naturalBlockIndex.signals(blockId);
    }

    ProceduralConservationMath.Plan<Item> conservationPlan(Item item) {
        List<Item> members = conservationGroup(item);
        List<ProceduralConservationMath.Edge<Item>> edges = new ArrayList<>();
        for (Item member : members) {
            for (ConservationEdge edge : conservationEdges.getOrDefault(member, List.of())) {
                edges.add(new ProceduralConservationMath.Edge<>(member, edge.target(), edge.numerator(), edge.denominator()));
            }
        }
        return ProceduralConservationMath.plan(members, edges, ProceduralValuationSettings.MAX_VALUE);
    }

    List<ProceduralTradeIndex.TradeSource> tradeSources(Item item) {
        return tradeIndex == null ? List.of() : tradeIndex.sources(item).stream()
                .filter(source -> ValuationGenerationInputs.sourceAllowed(source.traderId().toString())).toList();
    }

    List<ContainerLootSource> containerLootSources(Item item) {
        return containerLootSourcesByItem.getOrDefault(item, List.of()).stream()
                .filter(source -> ValuationGenerationInputs.sourceAllowed(source.lootTableId().toString())).toList();
    }

    List<FishingLootSource> fishingLootSources(Item item) {
        return fishingLootSourcesByItem.getOrDefault(item, List.of()).stream()
                .filter(source -> ValuationGenerationInputs.sourceAllowed(source.lootTableId().toString())).toList();
    }

    List<Item> conservationGroup(Item item) {
        return conservationGroups.getOrDefault(item, List.of(item));
    }


    double conservationFactor(Item source, Item target) {
        if (source == target) {
            return 1.0;
        }
        if (!conservationGroups.getOrDefault(source, List.of(source)).contains(target)) {
            return Double.NaN;
        }

        Map<Item, Double> factors = new IdentityHashMap<>();
        List<Item> queue = new ArrayList<>();
        factors.put(source, 1.0);
        queue.add(source);
        for (int i = 0; i < queue.size(); i++) {
            Item current = queue.get(i);
            double currentFactor = factors.get(current);
            for (ConservationEdge edge : conservationEdges.getOrDefault(current, List.of())) {
                if (factors.containsKey(edge.target())) {
                    continue;
                }
                double factor = currentFactor * edge.targetValuePerSourceValue();
                factors.put(edge.target(), factor);
                if (edge.target() == target) {
                    return factor;
                }
                queue.add(edge.target());
            }
        }
        return Double.NaN;
    }

    ProceduralProgressionIndex.ProgressionEvidence progressionForItem(Item item) {
        return progressionIndex.forItem(item);
    }

    ProceduralProgressionIndex.ProgressionEvidence progressionForBlock(Block block) {
        return progressionIndex.forBlock(block);
    }

    ProceduralProgressionIndex.ProgressionEvidence progressionForEntity(ResourceLocation entityId) {
        return progressionIndex.forEntity(entityId);
    }

    ProceduralProgressionIndex.ProgressionEvidence progressionForRecipe(ResourceLocation recipeId) {
        return progressionIndex.forRecipe(recipeId);
    }

    ProceduralProgressionIndex.ProgressionEvidence progressionForLootTable(ResourceLocation lootTableId) {
        return progressionIndex.forLootTable(lootTableId);
    }

    ProceduralProgressionIndex.ProgressionEvidence progressionForDimension(ResourceLocation dimensionId) {
        return progressionIndex.forDimension(dimensionId);
    }

    Summary summary() {
        return summary;
    }

    boolean isReversibleTransform(RecipeModel model) {
        return isReversibleTransform(model, recipesByOutput);
    }

    private static boolean isReversibleTransform(RecipeModel model, Map<Item, List<RecipeModel>> recipesByOutput) {
        if (model.production() != null && (!model.production().conservationComplete()
                || model.ingredients().stream().anyMatch(input -> !input.consumed() || input.count() != 1)
                || model.production().outputs().size() != 1)) return false;
        Item input = model.singleIngredientIdentity();
        if (input == null || input == model.outputItem()) {
            return false;
        }

        int forwardInputCount = model.ingredients().size();
        int forwardOutputCount = model.outputCount();

        for (RecipeModel reverse : recipesByOutput.getOrDefault(input, List.of())) {
            if (reverse.production() != null && (!reverse.production().conservationComplete()
                    || reverse.ingredients().stream().anyMatch(ingredient -> !ingredient.consumed() || ingredient.count() != 1)
                    || reverse.production().outputs().size() != 1)) continue;
            Item reverseInput = reverse.singleIngredientIdentity();
            if (reverseInput != model.outputItem()) {
                continue;
            }

            int reverseInputCount = reverse.ingredients().size();
            int reverseOutputCount = reverse.outputCount();

            long left = (long) forwardInputCount * reverseInputCount;
            long right = (long) forwardOutputCount * reverseOutputCount;
            if (left == right) {
                return true;
            }
        }

        return false;
    }

    /** Shared item projection. Unsupported predicates/resources remain evidence, never known acquisition seeds. */
    static List<RecipeModel> productionModels(com.mistaboom.essence_ascendance.balance.economy.ProductionGraph.Process process, RecipeType<?> type) {
        // Quest completion is a conditional source, not a material recipe. Its typed
        // prerequisite graph is evaluated once in the shared availability projection.
        if (process.metadata().containsKey("quest_id")) return List.of();
        List<IngredientChoice> inputs = new ArrayList<>();
        for (var input : process.inputs()) {
            List<Item> alternatives = input.alternatives().stream().map(ResourceLocation::tryParse).filter(java.util.Objects::nonNull)
                    .filter(BuiltInRegistries.ITEM::containsKey).map(BuiltInRegistries.ITEM::get)
                    .filter(item -> item != Items.AIR && ValuationGenerationInputs.itemAllowed(item)).toList();
            if (alternatives.isEmpty()) return List.of();
            inputs.add(new IngredientChoice(alternatives, input.count(), input.consumed()));
        }
        List<RecipeModel> result = new ArrayList<>();
        for (var output : process.outputs()) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(output.itemId()));
            if (item == Items.AIR || !ValuationGenerationInputs.itemAllowed(item)) continue;
            result.add(new RecipeModel(ResourceLocation.parse(process.id()), type, item,
                    (int) Math.max(1, Math.min(Integer.MAX_VALUE, output.count())), inputs, process, output.expectedCount()));
        }
        return List.copyOf(result);
    }

    private static Map<Item, List<ConservationEdge>> buildConservationEdges(
            Map<Item, List<RecipeModel>> recipesByOutput
    ) {
        Map<Item, List<ConservationEdge>> edges = new IdentityHashMap<>();
        for (List<RecipeModel> recipes : recipesByOutput.values()) {
            for (RecipeModel recipe : recipes) {
                if (!isReversibleTransform(recipe, recipesByOutput)) {
                    continue;
                }
                Item input = recipe.singleIngredientIdentity();
                if (input == null || input == recipe.outputItem()) {
                    continue;
                }
                double outputPerInputValue = (double) recipe.ingredients().size()
                        / Math.max(1, recipe.outputCount());
                if (!Double.isFinite(outputPerInputValue) || outputPerInputValue <= 0.0) {
                    continue;
                }
                edges.computeIfAbsent(input, ignored -> new ArrayList<>()).add(
                        new ConservationEdge(recipe.outputItem(), recipe.ingredients().size(), Math.max(1, recipe.outputCount()))
                );
                edges.computeIfAbsent(recipe.outputItem(), ignored -> new ArrayList<>()).add(
                        new ConservationEdge(input, Math.max(1, recipe.outputCount()), recipe.ingredients().size())
                );
            }
        }
        return edges;
    }

    private static Map<Item, List<Item>> buildConservationGroups(
            Map<Item, List<ConservationEdge>> edges
    ) {
        Map<Item, List<Item>> result = new IdentityHashMap<>();
        Set<Item> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Item start : edges.keySet()) {
            if (!visited.add(start)) {
                continue;
            }
            List<Item> group = new ArrayList<>();
            List<Item> queue = new ArrayList<>();
            queue.add(start);
            for (int i = 0; i < queue.size(); i++) {
                Item current = queue.get(i);
                group.add(current);
                for (ConservationEdge edge : edges.getOrDefault(current, List.of())) {
                    if (visited.add(edge.target())) {
                        queue.add(edge.target());
                    }
                }
            }
            group.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
            List<Item> frozen = List.copyOf(group);
            for (Item member : group) {
                result.put(member, frozen);
            }
        }
        return result;
    }

    private static void addVanillaHardcodedEntitySources(
            Map<Item, List<DropSource>> output,
            ProceduralMobSpawnIndex mobSpawnIndex
    ) {
        addHardcodedEntityDrop(
                output,
                mobSpawnIndex,
                ResourceLocation.fromNamespaceAndPath("minecraft", "nether_star"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "wither"),
                1.0,
                1.0,
                ProceduralValuationSettings.PLAYER_SUMMONED_BOSS_MULTIPLIER,
                true,
                false,
                List.of(
                        "vanilla hardcoded Wither reward (not represented by a normal entity loot table)",
                        "player-summoned boss acquisition"
                )
        );
        addHardcodedEntityDrop(
                output,
                mobSpawnIndex,
                ResourceLocation.fromNamespaceAndPath("minecraft", "dragon_egg"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "ender_dragon"),
                1.0,
                1.0,
                ProceduralValuationSettings.UNIQUE_FIRST_KILL_MULTIPLIER,
                true,
                false,
                List.of(
                        "vanilla first Ender Dragon kill places the Dragon Egg outside a normal loot table",
                        "unique first-kill progression reward"
                )
        );
        addHardcodedEntityDrop(
                output,
                mobSpawnIndex,
                ResourceLocation.fromNamespaceAndPath("minecraft", "nautilus_shell"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "drowned"),
                0.03,
                1.0,
                1.0,
                false,
                true,
                List.of(
                        "vanilla naturally-spawned Drowned equipment source",
                        "3% natural-spawn offhand shell; shell drops when carried"
                )
        );
    }

    private static void addHardcodedEntityDrop(
            Map<Item, List<DropSource>> output,
            ProceduralMobSpawnIndex mobSpawnIndex,
            ResourceLocation itemId,
            ResourceLocation entityId,
            double chance,
            double count,
            double specialMultiplier,
            boolean bossScale,
            boolean repeatableSpawn,
            List<String> extraSignals
    ) {
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null) {
            return;
        }
        MobStats stats = mobStats(entityId);
        ProceduralMobSpawnIndex.SpawnAvailability spawnAvailability = mobSpawnIndex == null
                ? ProceduralMobSpawnIndex.SpawnAvailability.UNKNOWN
                : mobSpawnIndex.forEntity(entityId);
        List<String> signals = new ArrayList<>(spawnAvailability.signals());
        signals.addAll(extraSignals);
        output.computeIfAbsent(item, ignored -> new ArrayList<>()).add(new DropSource(
                entityId,
                clampProbability(chance),
                Math.max(0.01, count),
                0,
                stats.maxHealth(),
                stats.attackDamage(),
                stats.armor(),
                bossScale,
                spawnAvailability.multiplier(),
                specialMultiplier,
                repeatableSpawn,
                List.copyOf(signals)
        ));
    }

    private static void addVanillaFixedStructureSources(
            Map<Item, List<ContainerLootSource>> output,
            ProceduralStructureIndex structureIndex,
            GenerationDataSnapshot data
    ) {
        Item elytra = BuiltInRegistries.ITEM.getOptional(
                ResourceLocation.fromNamespaceAndPath("minecraft", "elytra")
        ).orElse(null);
        if (elytra == null) {
            return;
        }

        ResourceLocation proxyLootTable = ResourceLocation.fromNamespaceAndPath("minecraft", "chests/end_city_treasure");
        ProceduralStructureIndex.ContainerOccurrence occurrence = structureIndex == null
                ? new ProceduralStructureIndex.ContainerOccurrence(null, 1.0, 1.0, false, 0, List.of())
                : structureIndex.forLootTable(proxyLootTable);
        double structureMultiplier = occurrence.structureFrequencyKnown()
                ? occurrence.structureFrequencyMultiplier()
                : ProceduralValuationSettings.UNKNOWN_STRUCTURE_FREQUENCY_MULTIPLIER;
        List<String> signals = new ArrayList<>();
        signals.add("fixed End City ship item-frame treasure rather than random chest loot");
        signals.addAll(occurrence.signals());
        if (!occurrence.structureFrequencyKnown()) {
            signals.add("End City placement frequency not fully derivable; conservative unknown-frequency premium applied");
        }

        output.computeIfAbsent(elytra, ignored -> new ArrayList<>()).add(new ContainerLootSource(
                ResourceLocation.fromNamespaceAndPath("minecraft", "containers/end_city_ship_fixed_elytra"),
                1.0,
                1.0,
                0,
                "FIXED_TREASURE",
                ProceduralValuationResult.ProgressionBand.END,
                ProceduralValuationSettings.END_MULTIPLIER
                        * ProceduralValuationSettings.FIXED_STRUCTURE_TREASURE_MULTIPLIER
                        * structureMultiplier,
                occurrence.structureFrequencyKnown(),
                occurrence.structureId(),
                occurrence.templateReferenceCount(),
                List.copyOf(signals),
                false,
                data.lootr().describe("minecraft:containers/end_city_ship_fixed_elytra", occurrence.structureId() == null ? "" : occurrence.structureId().toString(),
                        occurrence.structureId() == null ? List.of() : data.structureDimensions(occurrence.structureId()), false,
                        occurrence.structureId() != null && data.structureEligible(occurrence.structureId()) && occurrence.structureFrequencyKnown(), 1, 1, 0)
        ));
    }

    private static int scanContainerLootTables(
            MinecraftServer server,
            Map<Item, List<ContainerLootSource>> output,
            ProceduralStructureIndex structureIndex
    ) {
        Map<ResourceLocation, JsonObject> tables = ProceduralValuationEngine.generationData(server).json("loot_evidence");

        Map<ResourceLocation, Map<Item, ContainerEstimate>> memo = ProceduralValuationEngine.generationData(server).lootEstimates();
        int scanned = 0;
        for (Map.Entry<ResourceLocation, JsonObject> entry : tables.entrySet()) {
            ResourceLocation tableId = entry.getKey();
            JsonObject root = entry.getValue();
            if (!isContainerLootTable(tableId, root) && !isArchaeologyLootTable(tableId, root)) {
                continue;
            }

            boolean archaeology = isArchaeologyLootTable(tableId, root);
            ContainerContext context = containerContext(tableId, structureIndex);
            // A registered loot definition is not proof that its container is generated.
            // Retain unknown tables as conditional evidence; providers may prove other access.
            boolean configuredStructure = context.structureId() != null
                    && ProceduralValuationEngine.generationData(server).structureEligible(context.structureId())
                    && context.structureFrequencyKnown();
            if (archaeology) {
                List<String> signals = new ArrayList<>(context.signals());
                signals.add("loaded archaeology loot: brush/excavation access; not renewable chest farming");
                signals.add("brushing context modeled; exact suspicious-block density is not derived");
                context = new ContainerContext(context.progressionBand(),
                        context.contextMultiplier() * ProceduralValuationSettings.ARCHAEOLOGY_SOURCE_MULTIPLIER,
                        context.tierLabel(), context.structureFrequencyKnown(), context.structureId(),
                        context.templateReferenceCount(), List.copyOf(signals));
            }
            Map<Item, ContainerEstimate> estimates = estimateContainerTable(
                    tableId,
                    tables,
                    memo,
                    new HashSet<>()
            );

            var data = ProceduralValuationEngine.generationData(server);
            data.retainLootSemantics(tableId);
            String structure = context.structureId() == null ? "" : context.structureId().toString();
            List<String> dimensions = context.structureId() == null ? List.of() : data.structureDimensions(context.structureId());
            var availability = data.lootr().describe(tableId.toString(), structure, dimensions, false,
                    configuredStructure, 0, 0, 0);
            data.lootAvailability(tableId.toString(), availability);
            if (data.lootr().installed() && availability.scope() != com.mistaboom.essence_ascendance.balance.engine.SourceAvailability.Scope.SHARED)
                BalancePerformance.increment("lootr_supported_source_rules");

            for (Map.Entry<Item, ContainerEstimate> estimateEntry : estimates.entrySet()) {
                ContainerEstimate estimate = estimateEntry.getValue();
                if (estimate.occurrenceChance() <= 0.0 || estimate.expectedCount() <= 0.0) continue;
                ContainerLootSource source = new ContainerLootSource(
                        tableId,
                        clampProbability(estimate.occurrenceChance()),
                        Math.max(0.01, estimate.expectedCount()),
                        estimate.complexConditionCount() + (configuredStructure ? 0 : 1),
                        context.tierLabel(),
                        context.progressionBand(),
                        context.contextMultiplier(),
                        context.structureFrequencyKnown(),
                        context.structureId(),
                        context.templateReferenceCount(),
                        context.signals(),
                        archaeology,
                        availability.event(clampProbability(estimate.occurrenceChance()), Math.max(0.01, estimate.expectedCount()), estimate.complexConditionCount())
                );
                output.computeIfAbsent(estimateEntry.getKey(), ignored -> new ArrayList<>())
                        .add(source);
            }
            scanned++;
        }

        return scanned;
    }

    private static int scanFishingLootTables(
            MinecraftServer server,
            Map<Item, List<FishingLootSource>> output
    ) {
        Map<ResourceLocation, JsonObject> tables = ProceduralValuationEngine.generationData(server).json("loot_evidence");

        Map<ResourceLocation, Map<Item, ContainerEstimate>> memo = ProceduralValuationEngine.generationData(server).lootEstimates();
        int scanned = 0;
        for (Map.Entry<ResourceLocation, JsonObject> entry : tables.entrySet()) {
            ResourceLocation tableId = entry.getKey();
            JsonObject root = entry.getValue();
            if (!isFishingRootLootTable(tableId, root)) {
                continue;
            }

            FishingContext context = fishingContext(tableId);
            ProceduralValuationEngine.generationData(server).retainLootSemantics(tableId);
            Map<Item, ContainerEstimate> estimates = estimateContainerTable(
                    tableId,
                    tables,
                    memo,
                    new HashSet<>()
            );
            for (Map.Entry<Item, ContainerEstimate> estimateEntry : estimates.entrySet()) {
                ContainerEstimate estimate = estimateEntry.getValue();
                output.computeIfAbsent(estimateEntry.getKey(), ignored -> new ArrayList<>())
                        .add(new FishingLootSource(
                                tableId,
                                clampProbability(estimate.occurrenceChance()),
                                Math.max(0.01, estimate.expectedCount()),
                                estimate.complexConditionCount(),
                                context.progressionBand(),
                                context.contextMultiplier(),
                                context.signals()
                        ));
            }
            scanned++;
        }
        return scanned;
    }

    private static boolean isFishingRootLootTable(ResourceLocation tableId, JsonObject root) {
        String type = readString(root, "type");
        if (type == null || !(type.equals("minecraft:fishing") || type.endsWith(":fishing"))) {
            return false;
        }

        String path = tableId.getPath().toLowerCase(Locale.ROOT);
        return path.equals("gameplay/fishing")
                || path.endsWith("/fishing")
                || path.equals("fishing");
    }

    private static FishingContext fishingContext(ResourceLocation tableId) {
        String path = tableId.getPath().toLowerCase(Locale.ROOT);
        ProceduralValuationResult.ProgressionBand progression =
                ProceduralValuationResult.ProgressionBand.OVERWORLD;
        double multiplier = ProceduralValuationSettings.FISHING_SOURCE_MULTIPLIER;
        List<String> signals = new ArrayList<>();
        signals.add("data-driven repeatable fishing loot root");

        if (path.contains("nether") || path.contains("lava")) {
            progression = ProceduralValuationResult.ProgressionBand.NETHER;
            multiplier *= ProceduralValuationSettings.NETHER_MULTIPLIER;
            signals.add("Nether/lava fishing context");
        } else if (path.contains("end/" ) || path.contains("/end/") || path.contains("end_fishing")) {
            progression = ProceduralValuationResult.ProgressionBand.END;
            multiplier *= ProceduralValuationSettings.END_MULTIPLIER;
            signals.add("End fishing context");
        }

        return new FishingContext(progression, multiplier, List.copyOf(signals));
    }

    private static ResourceLocation lootTableIdFromResource(ResourceLocation resourceId) {
        String path = resourceId.getPath();
        if (!path.startsWith(LOOT_TABLE_PREFIX) || !path.endsWith(".json")) {
            return null;
        }
        String tablePath = path.substring(
                LOOT_TABLE_PREFIX.length(),
                path.length() - ".json".length()
        );
        return tablePath.isBlank()
                ? null
                : ResourceLocation.tryBuild(resourceId.getNamespace(), tablePath);
    }

    private static boolean isArchaeologyLootTable(ResourceLocation tableId, JsonObject root) {
        String type = readString(root, "type");
        return "minecraft:archaeology".equals(type)
                || tableId.getPath().startsWith("archaeology/");
    }

    private static boolean isContainerLootTable(ResourceLocation tableId, JsonObject root) {
        String type = readString(root, "type");
        if (type != null && (type.equals("minecraft:chest") || type.endsWith(":chest"))) {
            return true;
        }
        String path = tableId.getPath().toLowerCase(Locale.ROOT);
        return path.startsWith("chests/")
                || path.contains("/chests/")
                || path.startsWith("containers/")
                || path.contains("/containers/");
    }

    static Map<Item, ContainerEstimate> estimateContainerTable(
            ResourceLocation tableId,
            Map<ResourceLocation, JsonObject> tables,
            Map<ResourceLocation, Map<Item, ContainerEstimate>> memo,
            java.util.Set<ResourceLocation> visiting
    ) {
        Map<Item, ContainerEstimate> cached = memo.get(tableId);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(tableId)) {
            return Map.of();
        }

        JsonObject root = tables.get(tableId);
        if (root == null) {
            visiting.remove(tableId);
            return Map.of();
        }

        Map<Item, MutableContainerEstimate> combined = new IdentityHashMap<>();
        JsonElement poolsElement = root.get("pools");
        if (poolsElement != null && poolsElement.isJsonArray()) {
            for (JsonElement poolElement : poolsElement.getAsJsonArray()) {
                if (!poolElement.isJsonObject()) {
                    continue;
                }
                estimateContainerPool(
                        poolElement.getAsJsonObject(),
                        GenerationLootEvidence.unresolvedFunctions(root.get("functions")),
                        tables,
                        memo,
                        visiting,
                        combined
                );
            }
        }

        visiting.remove(tableId);
        Map<Item, ContainerEstimate> frozen = new IdentityHashMap<>();
        combined.forEach((item, estimate) -> frozen.put(item, estimate.freeze()));
        Map<Item, ContainerEstimate> result = Map.copyOf(frozen);
        // Cyclic tables are marked unresolved by the shared audit, so cached expansions never certify a cycle.
        memo.put(tableId, result);
        return result;
    }

    private static void estimateContainerPool(
            JsonObject pool,
            int inheritedUnresolvedFunctions,
            Map<ResourceLocation, JsonObject> tables,
            Map<ResourceLocation, Map<Item, ContainerEstimate>> memo,
            java.util.Set<ResourceLocation> visiting,
            Map<Item, MutableContainerEstimate> output
    ) {
        JsonElement entriesElement = pool.get("entries");
        if (entriesElement == null || !entriesElement.isJsonArray()) {
            return;
        }

        double rolls = Math.max(0.0, estimateNumberProvider(pool.get("rolls"), 1.0));
        if (rolls <= 0.0) {
            return;
        }

        ChanceInfo poolChance = readChance(pool.get("conditions"));
        int poolComplex = poolChance.complexConditions() + inheritedUnresolvedFunctions
                + GenerationLootEvidence.unresolvedFunctions(pool.get("functions"));
        double poolCountMultiplier = readSetCountMultiplier(pool.get("functions"));
        // Baseline valuation assumes zero Luck/Luck-of-the-Sea. Luck-dependent
        // bonus rolls therefore contribute zero without making the source
        // unresolved.

        List<JsonObject> entries = new ArrayList<>();
        double totalWeight = 0.0;
        for (JsonElement entryElement : entriesElement.getAsJsonArray()) {
            if (!entryElement.isJsonObject()) {
                continue;
            }
            JsonObject entry = entryElement.getAsJsonObject();
            entries.add(entry);
            totalWeight += readWeight(entry);
        }
        if (entries.isEmpty() || totalWeight <= 0.0) {
            return;
        }

        for (JsonObject entry : entries) {
            ChanceInfo entryChance = readChance(entry.get("conditions"));
            double perRollSelection = poolChance.multiplier()
                    * entryChance.multiplier()
                    * (readWeight(entry) / totalWeight);
            int complex = poolComplex + entryChance.complexConditions();
            // Entry quality only changes weight when Luck is non-zero. Baseline
            // valuation intentionally uses zero Luck, so the ordinary weight is
            // fully modeled and does not lower confidence.

            estimateContainerEntry(
                    entry,
                    rolls,
                    perRollSelection,
                    poolCountMultiplier,
                    complex,
                    tables,
                    memo,
                    visiting,
                    output
            );
        }
    }

    private static void estimateContainerEntry(
            JsonObject entry,
            double rolls,
            double perRollSelection,
            double inheritedCountMultiplier,
            int inheritedComplex,
            Map<ResourceLocation, JsonObject> tables,
            Map<ResourceLocation, Map<Item, ContainerEstimate>> memo,
            java.util.Set<ResourceLocation> visiting,
            Map<Item, MutableContainerEstimate> output
    ) {
        if (perRollSelection <= 0.0) {
            return;
        }

        String type = readString(entry, "type");
        int complex = inheritedComplex + GenerationLootEvidence.unresolvedFunctions(entry.get("functions"));
        double countMultiplier = inheritedCountMultiplier
                * readSetCountMultiplier(entry.get("functions"));

        if ("minecraft:item".equals(type)) {
            String name = readString(entry, "name");
            ResourceLocation itemId = name == null ? null : ResourceLocation.tryParse(name);
            if (itemId == null) {
                return;
            }
            BuiltInRegistries.ITEM.getOptional(itemId).ifPresent(item -> {
                double occurrence = occurrenceAcrossRolls(perRollSelection, rolls);
                double expectedCount = rolls * perRollSelection * Math.max(0.0, countMultiplier);
                mergeContainerEstimate(output, item, occurrence, expectedCount, complex);
            });
            return;
        }

        if ("minecraft:empty".equals(type)) {
            return;
        }

        if (type != null && type.equals("minecraft:loot_table")) {
            BalancePerformance.increment("loot_nested_references_expanded");
            ResourceLocation referenced = referencedLootTableId(entry);
            if (referenced == null || visiting.contains(referenced)) {
                return;
            }
            Map<Item, ContainerEstimate> nested = estimateContainerTable(
                    referenced,
                    tables,
                    memo,
                    visiting
            );
            nested.forEach((item, estimate) -> {
                double nestedPerRoll = perRollSelection * estimate.occurrenceChance();
                double occurrence = occurrenceAcrossRolls(nestedPerRoll, rolls);
                double expectedCount = rolls
                        * perRollSelection
                        * estimate.expectedCount()
                        * Math.max(0.0, countMultiplier);
                mergeContainerEstimate(
                        output,
                        item,
                        occurrence,
                        expectedCount,
                        complex + estimate.complexConditionCount()
                );
            });
            return;
        }

        JsonElement childrenElement = entry.get("children");
        if (childrenElement == null || !childrenElement.isJsonArray()) {
            return;
        }

        List<JsonObject> children = new ArrayList<>();
        for (JsonElement childElement : childrenElement.getAsJsonArray()) {
            if (childElement.isJsonObject()) {
                children.add(childElement.getAsJsonObject());
            }
        }
        if (children.isEmpty()) {
            return;
        }

        boolean alternatives = type != null && type.equals("minecraft:alternatives");
        double childSelection = alternatives
                ? perRollSelection / children.size()
                : perRollSelection;
        int childComplex = complex + 1;
        for (JsonObject child : children) {
            ChanceInfo childChance = readChance(child.get("conditions"));
            estimateContainerEntry(
                    child,
                    rolls,
                    childSelection * childChance.multiplier(),
                    countMultiplier,
                    childComplex + childChance.complexConditions(),
                    tables,
                    memo,
                    visiting,
                    output
            );
        }
    }

    private static ResourceLocation referencedLootTableId(JsonObject entry) {
        String value = readString(entry, "value");
        if (value == null) {
            value = readString(entry, "name");
        }
        return value == null ? null : ResourceLocation.tryParse(value);
    }

    private static double readWeight(JsonObject entry) {
        return Math.max(0.0, readDouble(entry, "weight", 1.0));
    }

    private static double readDouble(JsonObject object, String key, double fallback) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return value.getAsDouble();
    }

    private static double occurrenceAcrossRolls(double perRollChance, double rolls) {
        double chance = clampProbability(perRollChance);
        return clampProbability(1.0 - Math.pow(1.0 - chance, Math.max(0.0, rolls)));
    }

    private static void mergeContainerEstimate(
            Map<Item, MutableContainerEstimate> output,
            Item item,
            double occurrenceChance,
            double expectedCount,
            int complexConditions
    ) {
        output.computeIfAbsent(item, ignored -> new MutableContainerEstimate())
                .merge(occurrenceChance, expectedCount, complexConditions);
    }

    private static ContainerContext containerContext(ResourceLocation lootTableId, ProceduralStructureIndex structureIndex) {
        String path = lootTableId.getPath().toLowerCase(Locale.ROOT);
        ProceduralValuationResult.ProgressionBand progression =
                ProceduralValuationResult.ProgressionBand.OVERWORLD;
        double tierMultiplier = ProceduralValuationSettings.CONTAINER_STANDARD_MULTIPLIER;
        String tier = "STANDARD";
        List<String> signals = new ArrayList<>();

        if (path.contains("end_city")) {
            progression = ProceduralValuationResult.ProgressionBand.END;
            tierMultiplier = ProceduralValuationSettings.CONTAINER_LATE_GAME_MULTIPLIER;
            tier = "LATE_GAME";
            signals.add("end-city treasure context");
        } else if (path.contains("bastion_treasure")) {
            progression = ProceduralValuationResult.ProgressionBand.NETHER;
            tierMultiplier = ProceduralValuationSettings.CONTAINER_TREASURE_MULTIPLIER;
            tier = "TREASURE";
            signals.add("bastion treasure context");
        } else if (path.contains("bastion")) {
            progression = ProceduralValuationResult.ProgressionBand.NETHER;
            tierMultiplier = ProceduralValuationSettings.CONTAINER_RARE_MULTIPLIER;
            tier = "RARE";
            signals.add("bastion context");
        } else if (path.contains("nether_bridge") || path.contains("fortress")) {
            progression = ProceduralValuationResult.ProgressionBand.NETHER;
            tierMultiplier = ProceduralValuationSettings.CONTAINER_EXPLORATION_MULTIPLIER;
            tier = "EXPLORATION";
            signals.add("Nether fortress context");
        } else if (path.contains("ancient_city")) {
            tierMultiplier = ProceduralValuationSettings.CONTAINER_LATE_GAME_MULTIPLIER;
            tier = "LATE_GAME";
            signals.add("ancient-city context");
        } else if (path.contains("stronghold")
                || path.contains("woodland_mansion")
                || path.contains("trial_chambers")
                || path.contains("trial_chamber")) {
            tierMultiplier = ProceduralValuationSettings.CONTAINER_RARE_MULTIPLIER;
            tier = "RARE";
            signals.add("advanced structure context");
        } else if (path.contains("buried_treasure") || path.contains("rare")) {
            tierMultiplier = ProceduralValuationSettings.CONTAINER_RARE_MULTIPLIER;
            tier = "RARE";
            signals.add("rare/treasure context");
        } else if (path.contains("simple_dungeon")
                || path.contains("mineshaft")
                || path.contains("temple")
                || path.contains("pyramid")
                || path.contains("outpost")
                || path.contains("shipwreck")
                || path.contains("ruined_portal")
                || path.contains("igloo")
                || path.contains("dungeon")) {
            tierMultiplier = ProceduralValuationSettings.CONTAINER_EXPLORATION_MULTIPLIER;
            tier = "EXPLORATION";
            signals.add("exploration structure context");
        } else if (path.contains("village")
                || path.contains("supply")
                || path.contains("starter")
                || path.contains("common")) {
            tierMultiplier = ProceduralValuationSettings.CONTAINER_COMMON_MULTIPLIER;
            tier = "COMMON";
            signals.add("common/supply container context");
        } else if (path.contains("boss") || path.contains("legendary")) {
            tierMultiplier = ProceduralValuationSettings.CONTAINER_TREASURE_MULTIPLIER;
            tier = "TREASURE";
            signals.add("boss/legendary container identity");
        } else if (path.contains("treasure") || path.contains("vault")) {
            tierMultiplier = ProceduralValuationSettings.CONTAINER_RARE_MULTIPLIER;
            tier = "RARE";
            signals.add("treasure/vault container identity");
        }

        if (progression == ProceduralValuationResult.ProgressionBand.OVERWORLD) {
            if (path.contains("nether") || path.contains("bastion")) {
                progression = ProceduralValuationResult.ProgressionBand.NETHER;
                signals.add("Nether identity heuristic");
            } else if (path.contains("end_") || path.startsWith("end/")) {
                progression = ProceduralValuationResult.ProgressionBand.END;
                signals.add("End identity heuristic");
            }
        }

        double progressionMultiplier = switch (progression) {
            case NETHER -> ProceduralValuationSettings.NETHER_MULTIPLIER;
            case END -> ProceduralValuationSettings.END_MULTIPLIER;
            case BOSS_SCALE -> ProceduralValuationSettings.BOSS_SCALE_MULTIPLIER;
            case OVERWORLD -> 1.0;
        };

        if (!lootTableId.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)
                && signals.isEmpty()) {
            signals.add("modded container; neutral tier because progression is not derivable");
        }

        ProceduralStructureIndex.ContainerOccurrence occurrence = structureIndex == null
                ? new ProceduralStructureIndex.ContainerOccurrence(null, 1.0, 1.0, false, 0, List.of())
                : structureIndex.forLootTable(lootTableId);
        signals.addAll(occurrence.signals());

        double occurrenceMultiplier = occurrence.combinedMultiplier();
        if (!occurrence.structureFrequencyKnown()
                && (path.startsWith("chests/") || path.contains("/chests/")
                || path.startsWith("containers/") || path.contains("/containers/")
                || path.startsWith("archaeology/"))) {
            occurrenceMultiplier *= ProceduralValuationSettings.UNKNOWN_STRUCTURE_FREQUENCY_MULTIPLIER;
            signals.add("structure frequency not derivable; conservative unknown-frequency premium applied");
        }

        return new ContainerContext(
                progression,
                tierMultiplier * progressionMultiplier * occurrenceMultiplier,
                tier,
                occurrence.structureFrequencyKnown(),
                occurrence.structureId(),
                occurrence.templateReferenceCount(),
                List.copyOf(signals)
        );
    }

    private static int scanEntityLootTables(
            MinecraftServer server,
            Map<Item, List<DropSource>> output,
            ProceduralMobSpawnIndex mobSpawnIndex
    ) {
        Map<ResourceLocation, JsonObject> tables = ProceduralValuationEngine.generationData(server).json("loot_evidence");
        int scanned = 0;
        for (ResourceLocation tableId : tables.keySet().stream().sorted().toList()) {
            String entityPath = ProceduralLootIdentity.registeredEntityPath(tableId.getPath(), path ->
                    BuiltInRegistries.ENTITY_TYPE.getOptional(
                            ResourceLocation.fromNamespaceAndPath(tableId.getNamespace(), path)).isPresent());
            if (entityPath == null) continue;
            ResourceLocation entityId = ResourceLocation.fromNamespaceAndPath(tableId.getNamespace(), entityPath);
            boolean variant = !tableId.getPath().equals("entities/" + entityPath);
            List<String> signals = new ArrayList<>();
            signals.add("entity loot table " + tableId + " belongs to registered entity " + entityId);
            if (variant) signals.add("nested/variant selection frequency unresolved; parent identity is not variant spawn probability");
            ProceduralMobSpawnIndex.SpawnAvailability availability = mobSpawnIndex == null
                    ? ProceduralMobSpawnIndex.SpawnAvailability.UNKNOWN : mobSpawnIndex.forEntity(entityId);
            Set<ResourceLocation> visiting = new HashSet<>();
            visiting.add(tableId);
            try {
                collectDropItems(tables.get(tableId), entityId, mobStats(entityId), availability,
                        1.0, 1.0, variant ? 1 : 0, signals, output, tables, visiting);
                scanned++;
            } catch (RuntimeException exception) {
                EssenceAscendance.LOGGER.debug("Procedural entity source skipped {}: {}", tableId, exception.getMessage());
            }
        }
        return scanned;
    }

    private static int scanBlockLootTables(
            MinecraftServer server,
            Map<Item, List<BlockDropSource>> output
    ) {
        Map<Block, List<ProceduralBlockHarvest.HarvestDrop>> harvests = ProceduralBlockHarvest.discover(server);
        for (Map.Entry<Block, List<ProceduralBlockHarvest.HarvestDrop>> entry : harvests.entrySet().stream()
                .sorted(Comparator.comparing(e -> BuiltInRegistries.BLOCK.getKey(e.getKey()).toString())).toList()) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(entry.getKey());
            BlockSourceStats stats = blockSourceStats(blockId, entry.getKey());
            Map<List<String>, List<String>> sharedSignals = new HashMap<>();
            for (ProceduralBlockHarvest.HarvestDrop drop : entry.getValue()) {
                List<String> signals = sharedSignals.computeIfAbsent(drop.signals(), dropSignals -> {
                    List<String> combined = new ArrayList<>(stats.signals());
                    combined.addAll(dropSignals);
                    return List.copyOf(combined);
                });
                output.computeIfAbsent(drop.output(), ignored -> new ArrayList<>()).add(new BlockDropSource(
                        blockId, drop.chance(), drop.countWhenPresent(), drop.unresolved(),
                        stats.progressionBand(), stats.sourceMultiplier(), stats.oreLike(), signals,
                        drop.reusableTool(), drop.silkTouch()));
            }
        }
        return harvests.size();
    }

    private static BlockSourceStats blockSourceStats(ResourceLocation blockId, Block block) {
        BlockState state = block.defaultBlockState();
        ProceduralValuationResult.ProgressionBand progression =
                ProceduralValuationResult.ProgressionBand.OVERWORLD;
        double sourceMultiplier = 1.0;
        List<String> signals = new ArrayList<>();

        if (state.is(ProceduralValuationTags.BLOCK_ORES_IN_NETHERRACK)) {
            progression = ProceduralValuationResult.ProgressionBand.NETHER;
            sourceMultiplier *= ProceduralValuationSettings.NETHER_MULTIPLIER;
            signals.add("netherrack ore");
        } else if (state.is(ProceduralValuationTags.BLOCK_ORES_IN_END_STONE)) {
            progression = ProceduralValuationResult.ProgressionBand.END;
            sourceMultiplier *= ProceduralValuationSettings.END_MULTIPLIER;
            signals.add("end-stone ore");
        }

        if (state.is(ProceduralValuationTags.BLOCK_ORES_IN_DEEPSLATE)) {
            sourceMultiplier *= ProceduralValuationSettings.DEEPSLATE_MULTIPLIER;
            signals.add("deepslate");
        }

        if (state.is(ProceduralValuationTags.BLOCK_ORE_RATE_DENSE)) {
            sourceMultiplier *= ProceduralValuationSettings.ORE_RATE_DENSE_MULTIPLIER;
            signals.add("dense ore rate");
        } else if (state.is(ProceduralValuationTags.BLOCK_ORE_RATE_SPARSE)) {
            sourceMultiplier *= ProceduralValuationSettings.ORE_RATE_SPARSE_MULTIPLIER;
            signals.add("sparse ore rate");
        } else if (state.is(ProceduralValuationTags.BLOCK_ORE_RATE_SINGULAR)) {
            signals.add("singular ore rate");
        }

        if (state.is(ProceduralValuationTags.NEEDS_DIAMOND_TOOL)) {
            sourceMultiplier *= ProceduralValuationSettings.NEEDS_DIAMOND_TOOL_MULTIPLIER;
            signals.add("diamond-tier harvest");
        } else if (state.is(ProceduralValuationTags.NEEDS_IRON_TOOL)) {
            sourceMultiplier *= ProceduralValuationSettings.NEEDS_IRON_TOOL_MULTIPLIER;
            signals.add("iron-tier harvest");
        } else if (state.is(ProceduralValuationTags.NEEDS_STONE_TOOL)) {
            sourceMultiplier *= ProceduralValuationSettings.NEEDS_STONE_TOOL_MULTIPLIER;
            signals.add("stone-tier harvest");
        }

        boolean oreLike = state.is(ProceduralValuationTags.BLOCK_ORES)
                || state.is(ProceduralValuationTags.BLOCK_ORES_IN_STONE)
                || state.is(ProceduralValuationTags.BLOCK_ORES_IN_DEEPSLATE)
                || state.is(ProceduralValuationTags.BLOCK_ORES_IN_NETHERRACK)
                || state.is(ProceduralValuationTags.BLOCK_ORES_IN_END_STONE)
                || blockId.getPath().contains("ancient_debris");

        return new BlockSourceStats(
                progression,
                sourceMultiplier,
                oreLike,
                List.copyOf(signals)
        );
    }

    private static void collectDropItems(
            JsonElement element,
            ResourceLocation entityId,
            MobStats stats,
            ProceduralMobSpawnIndex.SpawnAvailability spawnAvailability,
            double inheritedChance,
            double inheritedCount,
            int complexConditionCount,
            List<String> inheritedConditionSignals,
            Map<Item, List<DropSource>> output,
            Map<ResourceLocation, JsonObject> tables,
            Set<ResourceLocation> visitingTables
    ) {
        if (element == null || element.isJsonNull()) {
            return;
        }

        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectDropItems(
                        child,
                        entityId,
                        stats,
                        spawnAvailability,
                        inheritedChance,
                        inheritedCount,
                        complexConditionCount,
                        inheritedConditionSignals,
                        output, tables, visitingTables
                );
            }
            return;
        }

        if (!element.isJsonObject()) {
            return;
        }

        JsonObject object = element.getAsJsonObject();
        EntityConditionInfo conditionInfo = readEntityConditions(
                object.get("conditions"),
                entityId,
                stats
        );
        double chance = inheritedChance * conditionInfo.multiplier();
        int complexConditions = complexConditionCount + conditionInfo.complexConditions()
                + GenerationLootEvidence.unresolvedFunctions(object.get("functions"));
        MobStats resolvedStats = conditionInfo.stats();
        List<String> conditionSignals = new ArrayList<>(inheritedConditionSignals);
        conditionSignals.addAll(conditionInfo.signals());

        double count = inheritedCount;
        if (object.has("rolls")) {
            count *= estimateNumberProvider(object.get("rolls"), 1.0);
        }
        count *= readSetCountMultiplier(object.get("functions"));

        String type = readString(object, "type");
        String name = readString(object, "name");
        if (!Double.isFinite(chance) || !Double.isFinite(count) || chance <= 0.0 || count <= 0.0) return;
        if ("minecraft:loot_table".equals(type)) {
            ResourceLocation reference = referencedLootTableId(object);
            if (reference == null || visitingTables.size() >= 32 || !visitingTables.add(reference)) return;
            try {
                List<String> nestedSignals = new ArrayList<>(conditionSignals);
                nestedSignals.add("referenced loot table " + reference + " retains entity " + entityId);
                collectDropItems(tables.get(reference), entityId, resolvedStats, spawnAvailability,
                        chance, count, complexConditions, nestedSignals, output, tables, visitingTables);
            } finally {
                visitingTables.remove(reference);
            }
            return;
        }

        if ("minecraft:item".equals(type) && name != null) {
            ResourceLocation itemId = ResourceLocation.tryParse(name);
            if (itemId != null) {
                double resolvedCount = Math.max(0.01, count);
                BuiltInRegistries.ITEM.getOptional(itemId).ifPresent(item -> {
                    DropSource source = new DropSource(
                            entityId,
                            clampProbability(chance),
                            resolvedCount,
                            complexConditions,
                            resolvedStats.maxHealth(),
                            resolvedStats.attackDamage(),
                            resolvedStats.armor(),
                            resolvedStats.bossScale(),
                            spawnAvailability.multiplier(),
                            1.0,
                            spawnAvailability.known() && !resolvedStats.bossScale(),
                            mergeSignals(spawnAvailability.signals(), conditionSignals)
                    );
                    output.computeIfAbsent(item, ignored -> new ArrayList<>())
                            .add(source);
                });
            }
            return;
        }

        for (String childKey : List.of("pools", "entries", "children")) {
            JsonElement child = object.get(childKey);
            if (child == null) continue;
            if (childKey.equals("entries") && child.isJsonArray()) {
                double weight = 0.0;
                for (JsonElement entry : child.getAsJsonArray())
                    if (entry.isJsonObject()) weight += readWeight(entry.getAsJsonObject());
                if (weight <= 0.0) continue;
                for (JsonElement entry : child.getAsJsonArray()) {
                    if (!entry.isJsonObject()) continue;
                    collectDropItems(entry, entityId, resolvedStats, spawnAvailability,
                            chance * readWeight(entry.getAsJsonObject()) / weight, count,
                            complexConditions, conditionSignals, output, tables, visitingTables);
                }
            } else {
                int unresolved = complexConditions + (childKey.equals("children") ? 1 : 0);
                collectDropItems(child, entityId, resolvedStats, spawnAvailability, chance, count,
                        unresolved, conditionSignals, output, tables, visitingTables);
            }
        }
    }

    private static EntityConditionInfo readEntityConditions(
            JsonElement conditionsElement,
            ResourceLocation entityId,
            MobStats baseStats
    ) {
        if (conditionsElement == null || !conditionsElement.isJsonArray()) {
            return new EntityConditionInfo(1.0, 0, baseStats, List.of());
        }

        double multiplier = 1.0;
        int complex = 0;
        MobStats stats = baseStats;
        List<String> signals = new ArrayList<>();

        for (JsonElement conditionElement : conditionsElement.getAsJsonArray()) {
            if (!conditionElement.isJsonObject()) {
                complex++;
                continue;
            }
            JsonObject condition = conditionElement.getAsJsonObject();
            String conditionType = readString(condition, "condition");
            if (conditionType == null) {
                complex++;
                continue;
            }

            if (conditionType.equals("minecraft:random_chance") && condition.has("chance") && LootSemanticsAudit.supportedNumber(condition.get("chance"))) {
                multiplier *= estimateNumberProvider(condition.get("chance"), 1.0);
                continue;
            }
            if (conditionType.equals("minecraft:random_chance_with_enchanted_bonus")
                    && condition.has("unenchanted_chance")) {
                multiplier *= estimateNumberProvider(condition.get("unenchanted_chance"), 1.0);
                continue;
            }
            if (conditionType.equals("minecraft:table_bonus")
                    && condition.has("chances")
                    && condition.get("chances").isJsonArray()
                    && !condition.getAsJsonArray("chances").isEmpty()) {
                multiplier *= estimateNumberProvider(
                        condition.getAsJsonArray("chances").get(0),
                        1.0
                );
                continue;
            }
            if (conditionType.equals("minecraft:killed_by_player")
                    || conditionType.equals("minecraft:survives_explosion")) {
                continue;
            }
            if (conditionType.equals("minecraft:entity_properties")) {
                MobStats variant = slimeVariantStats(condition, entityId, stats);
                if (variant != null) {
                    stats = variant;
                    signals.add("entity loot variant: small slime size requirement modeled");
                    continue;
                }
            }
            if (conditionType.equals("minecraft:inverted")
                    && condition.has("term")
                    && condition.get("term").isJsonObject()
                    && isFrogDamageSourceCondition(condition.getAsJsonObject("term"))) {
                // This is the ordinary non-frog branch of the slime loot table.
                // A player kill satisfies it deterministically.
                signals.add("ordinary non-frog kill branch modeled");
                continue;
            }
            if (conditionType.equals("minecraft:damage_source_properties")
                    && isFrogDamageSourceCondition(condition)) {
                // Frog-only branch is a real acquisition path, but it needs a
                // frog interaction model before it can compete with ordinary
                // player kills. Keep it diagnostic rather than unknown overall.
                complex++;
                signals.add("frog-specific kill branch retained as diagnostic fallback");
                continue;
            }

            complex++;
        }

        return new EntityConditionInfo(
                clampProbability(multiplier),
                complex,
                stats,
                List.copyOf(signals)
        );
    }

    private static MobStats slimeVariantStats(
            JsonObject condition,
            ResourceLocation entityId,
            MobStats baseStats
    ) {
        if (entityId == null || !entityId.getPath().equals("slime")) {
            return null;
        }
        String entity = readString(condition, "entity");
        if (!"this".equals(entity)) {
            return null;
        }
        JsonElement predicateElement = condition.get("predicate");
        if (predicateElement == null || !predicateElement.isJsonObject()) {
            return null;
        }
        JsonElement typeSpecificElement = predicateElement.getAsJsonObject().get("type_specific");
        if (typeSpecificElement == null || !typeSpecificElement.isJsonObject()) {
            return null;
        }
        JsonObject typeSpecific = typeSpecificElement.getAsJsonObject();
        String type = readString(typeSpecific, "type");
        if (type == null || !type.equals("minecraft:slime") || !typeSpecific.has("size")) {
            return null;
        }

        double size = Math.max(1.0, estimateNumberProvider(typeSpecific.get("size"), 1.0));
        double health = Math.max(1.0, size * size);
        double attack = size <= 1.0 ? 0.0 : size;
        return new MobStats(health, attack, baseStats.armor(), false);
    }

    private static boolean isFrogDamageSourceCondition(JsonObject condition) {
        String conditionType = readString(condition, "condition");
        if (conditionType == null || !conditionType.equals("minecraft:damage_source_properties")) {
            return false;
        }
        JsonElement predicateElement = condition.get("predicate");
        if (predicateElement == null || !predicateElement.isJsonObject()) {
            return false;
        }
        JsonElement sourceEntityElement = predicateElement.getAsJsonObject().get("source_entity");
        if (sourceEntityElement == null || !sourceEntityElement.isJsonObject()) {
            return false;
        }
        JsonObject sourceEntity = sourceEntityElement.getAsJsonObject();
        String type = readString(sourceEntity, "type");
        return "minecraft:frog".equals(type);
    }

    private static List<String> mergeSignals(List<String> first, List<String> second) {
        List<String> merged = new ArrayList<>(first == null ? List.of() : first);
        if (second != null) {
            merged.addAll(second);
        }
        return List.copyOf(merged);
    }

    private static ChanceInfo readChance(JsonElement conditionsElement) {
        if (conditionsElement == null || !conditionsElement.isJsonArray()) {
            return ChanceInfo.IDENTITY;
        }

        double multiplier = 1.0;
        int complex = 0;

        for (JsonElement conditionElement : conditionsElement.getAsJsonArray()) {
            if (!conditionElement.isJsonObject()) {
                complex++;
                continue;
            }

            JsonObject condition = conditionElement.getAsJsonObject();
            String conditionType = readString(condition, "condition");
            if (conditionType == null) {
                complex++;
                continue;
            }

            if (conditionType.equals("minecraft:random_chance") && condition.has("chance")
                    && LootSemanticsAudit.supportedNumber(condition.get("chance"))) {
                multiplier *= estimateNumberProvider(condition.get("chance"), 1.0);
            } else if (conditionType.equals("minecraft:random_chance_with_enchanted_bonus")
                    && condition.has("unenchanted_chance")) {
                // Use the deterministic no-Looting baseline. Looting can only
                // make the real acquisition easier, so this remains a safe
                // baseline without making the source conditional/unknown.
                multiplier *= estimateNumberProvider(condition.get("unenchanted_chance"), 1.0);
            } else if (conditionType.equals("minecraft:table_bonus")
                    && condition.has("chances")
                    && condition.get("chances").isJsonArray()
                    && !condition.getAsJsonArray("chances").isEmpty()) {
                // Baseline enchantment level 0 is the first table-bonus chance.
                multiplier *= estimateNumberProvider(
                        condition.getAsJsonArray("chances").get(0),
                        1.0
                );
            } else if (conditionType.equals("minecraft:killed_by_player")
                    || conditionType.equals("minecraft:survives_explosion")) {
                // These are ordinary acquisition-context conditions. They do
                // not make the source ambiguous for a player-centric valuation.
            } else if (conditionType.equals("minecraft:entity_properties")
                    && isRecognizedFishingHookCondition(condition)) {
                // Vanilla fishing treasure uses a fishing-hook in_open_water
                // predicate. Open-water fishing is a normal deterministic
                // acquisition requirement, not an unknown probability.
            } else {
                // Tool predicates, block-state predicates, entity-property
                // predicates, alternatives/inversion, weather, time, etc. can
                // fundamentally change whether the source exists. Keep the
                // source for diagnostics, but mark it unresolved so it cannot
                // undercut a fully modeled recipe/source path.
                complex++;
            }
        }

        return new ChanceInfo(clampProbability(multiplier), complex);
    }

    private static boolean isRecognizedFishingHookCondition(JsonObject condition) {
        String entity = readString(condition, "entity");
        if (!"this".equals(entity)) {
            return false;
        }
        JsonElement predicateElement = condition.get("predicate");
        if (predicateElement == null || !predicateElement.isJsonObject()) {
            return false;
        }
        JsonObject predicate = predicateElement.getAsJsonObject();
        JsonElement typeSpecificElement = predicate.get("type_specific");
        if (typeSpecificElement == null || !typeSpecificElement.isJsonObject()) {
            return false;
        }
        JsonObject typeSpecific = typeSpecificElement.getAsJsonObject();
        String type = readString(typeSpecific, "type");
        return type != null
                && type.equals("minecraft:fishing_hook")
                && typeSpecific.has("in_open_water");
    }

    private static double readSetCountMultiplier(JsonElement functionsElement) {
        if (functionsElement == null || !functionsElement.isJsonArray()) {
            return 1.0;
        }

        double multiplier = 1.0;
        for (JsonElement functionElement : functionsElement.getAsJsonArray()) {
            if (!functionElement.isJsonObject()) {
                continue;
            }

            JsonObject function = functionElement.getAsJsonObject();
            if (GenerationLootEvidence.unresolvedFunctions(function) > 0) continue;
            String functionType = readString(function, "function");
            if (functionType != null
                    && functionType.equals("minecraft:set_count")
                    && function.has("count")) {
                multiplier *= estimateNumberProvider(function.get("count"), 1.0);
            }
        }
        return multiplier;
    }

    private static double estimateNumberProvider(JsonElement element, double fallback) {
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return Math.max(0.0, element.getAsDouble());
        }
        if (!element.isJsonObject()) {
            return fallback;
        }

        JsonObject object = element.getAsJsonObject();
        if (object.has("min") && object.has("max")) {
            double min = estimateNumberProvider(object.get("min"), fallback);
            double max = estimateNumberProvider(object.get("max"), fallback);
            return Math.max(0.0, (min + max) / 2.0);
        }
        if (object.has("value")) {
            return estimateNumberProvider(object.get("value"), fallback);
        }
        return fallback;
    }

    private static MobStats mobStats(ResourceLocation entityId) {
        EntityType<?> rawType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
        if (rawType == null || !DefaultAttributes.hasSupplier(rawType)) {
            return MobStats.UNKNOWN;
        }

        try {
            @SuppressWarnings("unchecked")
            EntityType<? extends LivingEntity> livingType =
                    (EntityType<? extends LivingEntity>) rawType;
            AttributeSupplier supplier = DefaultAttributes.getSupplier(livingType);
            if (supplier == null) {
                return MobStats.UNKNOWN;
            }

            double health = supplier.hasAttribute(Attributes.MAX_HEALTH)
                    ? supplier.getBaseValue(Attributes.MAX_HEALTH)
                    : 20.0;
            double attack = supplier.hasAttribute(Attributes.ATTACK_DAMAGE)
                    ? supplier.getBaseValue(Attributes.ATTACK_DAMAGE)
                    : 0.0;
            double armor = supplier.hasAttribute(Attributes.ARMOR)
                    ? supplier.getBaseValue(Attributes.ARMOR)
                    : 0.0;

            String path = entityId.getPath().toLowerCase(Locale.ROOT);
            boolean bossScale = health >= 150.0
                    || path.contains("ender_dragon")
                    || path.equals("wither");

            return new MobStats(health, attack, armor, bossScale);

        } catch (RuntimeException exception) {
            return MobStats.UNKNOWN;
        }
    }

    private static String readString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }

    private static double clampProbability(double value) {
        if (!Double.isFinite(value)) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static <T> Map<Item, List<T>> freezeLists(Map<Item, List<T>> source) {
        Map<Item, List<T>> copy = new IdentityHashMap<>();
        source.forEach((item, list) -> copy.put(item, List.copyOf(list)));
        return copy;
    }

    record RecipeModel(
            ResourceLocation id,
            RecipeType<?> type,
            Item outputItem,
            int outputCount,
            List<IngredientChoice> ingredients,
            com.mistaboom.essence_ascendance.balance.economy.ProductionGraph.Process production,
            double expectedOutputCount
    ) {
        RecipeModel(ResourceLocation id, RecipeType<?> type, Item outputItem, int outputCount, List<IngredientChoice> ingredients) {
            this(id, type, outputItem, outputCount, ingredients, null, outputCount);
        }
        Item singleIngredientIdentity() {
            Item identity = null;
            for (IngredientChoice ingredient : ingredients) {
                if (ingredient.alternatives().size() != 1) {
                    return null;
                }
                Item candidate = ingredient.alternatives().getFirst();
                if (identity == null) {
                    identity = candidate;
                } else if (identity != candidate) {
                    return null;
                }
            }
            return identity;
        }
    }

    record IngredientChoice(List<Item> alternatives, double count, boolean consumed) {
        IngredientChoice(List<Item> alternatives) { this(alternatives, 1, true); }
        IngredientChoice {
            alternatives = List.copyOf(alternatives);
        }
    }

    record RecipeUse(RecipeModel recipe) {
    }

    record ConservationEdge(Item target, long numerator, long denominator) {
        double targetValuePerSourceValue() { return (double) numerator / denominator; }
    }

    record DropSource(
            ResourceLocation entityId,
            double estimatedChance,
            double expectedCount,
            int complexConditionCount,
            double maxHealth,
            double attackDamage,
            double armor,
            boolean bossScale,
            double spawnAvailabilityMultiplier,
            double specialSourceMultiplier,
            boolean repeatableSpawn,
            List<String> signals
    ) {
        DropSource {
            signals = List.copyOf(signals);
        }
        double difficultyScore() {
            double healthFactor = Math.sqrt(Math.max(1.0, maxHealth) / 20.0);
            double offenseFactor = 1.0 + Math.max(0.0, attackDamage) / 20.0;
            double armorFactor = 1.0 + Math.max(0.0, armor) / 40.0;
            return healthFactor * offenseFactor * armorFactor;
        }
    }

    record BiologicalSource(BiologicalAcquisitionSources.Source event, double spawnMultiplier,
                            List<String> spawnSignals) {
        BiologicalSource { spawnSignals = List.copyOf(spawnSignals); }
    }

    record BlockDropSource(
            ResourceLocation blockId,
            double estimatedChance,
            double expectedCount,
            int complexConditionCount,
            ProceduralValuationResult.ProgressionBand progressionBand,
            double sourceMultiplier,
            boolean oreLike,
            List<String> signals,
            Item reusableTool,
            boolean silkTouchRequired
    ) {
        BlockDropSource {
            signals = List.copyOf(signals);
        }
    }

    private record BlockSourceStats(
            ProceduralValuationResult.ProgressionBand progressionBand,
            double sourceMultiplier,
            boolean oreLike,
            List<String> signals
    ) {
    }

    record ContainerLootSource(
            ResourceLocation lootTableId,
            double estimatedChance,
            double expectedCount,
            int complexConditionCount,
            String tierLabel,
            ProceduralValuationResult.ProgressionBand progressionBand,
            double contextMultiplier,
            boolean structureFrequencyKnown,
            ResourceLocation structureId,
            int templateReferenceCount,
            List<String> signals,
            boolean archaeology,
            com.mistaboom.essence_ascendance.balance.engine.SourceAvailability availability
    ) {
        ContainerLootSource(ResourceLocation lootTableId, double estimatedChance, double expectedCount, int complexConditionCount,
                            String tierLabel, ProceduralValuationResult.ProgressionBand progressionBand, double contextMultiplier,
                            boolean structureFrequencyKnown, ResourceLocation structureId, int templateReferenceCount,
                            List<String> signals, boolean archaeology) {
            this(lootTableId, estimatedChance, expectedCount, complexConditionCount, tierLabel, progressionBand, contextMultiplier,
                    structureFrequencyKnown, structureId, templateReferenceCount, signals, archaeology,
                    LootrPolicy.ABSENT.describe(lootTableId.toString(), structureId == null ? "" : structureId.toString(), List.of(),
                            false, structureFrequencyKnown, estimatedChance, expectedCount, complexConditionCount));
        }
        ContainerLootSource {
            signals = List.copyOf(signals);
        }
    }

    record FishingLootSource(
            ResourceLocation lootTableId,
            double estimatedChance,
            double expectedCount,
            int complexConditionCount,
            ProceduralValuationResult.ProgressionBand progressionBand,
            double contextMultiplier,
            List<String> signals
    ) {
        FishingLootSource {
            signals = List.copyOf(signals);
        }
    }

    private record FishingContext(
            ProceduralValuationResult.ProgressionBand progressionBand,
            double contextMultiplier,
            List<String> signals
    ) {
        FishingContext {
            signals = List.copyOf(signals);
        }
    }

    private record ContainerContext(
            ProceduralValuationResult.ProgressionBand progressionBand,
            double contextMultiplier,
            String tierLabel,
            boolean structureFrequencyKnown,
            ResourceLocation structureId,
            int templateReferenceCount,
            List<String> signals
    ) {
    }

    record ContainerEstimate(
            double occurrenceChance,
            double expectedCount,
            int complexConditionCount
    ) {
    }

    private static final class MutableContainerEstimate {
        private double occurrenceChance;
        private double expectedCount;
        private int complexConditionCount;

        void merge(double chance, double count, int complex) {
            double clamped = clampProbability(chance);
            occurrenceChance = 1.0 - (1.0 - occurrenceChance) * (1.0 - clamped);
            expectedCount += Math.max(0.0, count);
            complexConditionCount = Math.max(complexConditionCount, Math.max(0, complex));
        }

        ContainerEstimate freeze() {
            return new ContainerEstimate(
                    clampProbability(occurrenceChance),
                    Math.max(0.0, expectedCount),
                    complexConditionCount
            );
        }
    }

    record Summary(
            int recipeCount,
            int skippedRecipeCount,
            int outputItemCount,
            int ingredientLinkCount,
            int entityLootTableCount,
            int dropSourceLinkCount,
            int blockLootTableCount,
            int blockDropSourceLinkCount,
            int containerLootTableCount,
            int containerLootSourceLinkCount,
            int fishingLootTableCount,
            int fishingLootSourceLinkCount,
            int tradeProfessionTableCount,
            int tradeListingCount,
            int tradeOfferCount,
            int advancementCount,
            int consideredAdvancementCount,
            int advancementTreeCount,
            int advancementReferenceCount,
            int advancementItemReferenceCount,
            int advancementEntityReferenceCount,
            int advancementDimensionReferenceCount
    ) {
    }

    private record EntityConditionInfo(
            double multiplier,
            int complexConditions,
            MobStats stats,
            List<String> signals
    ) {
        EntityConditionInfo {
            signals = List.copyOf(signals);
        }
    }

    private record ChanceInfo(double multiplier, int complexConditions) {
        static final ChanceInfo IDENTITY = new ChanceInfo(1.0, 0);
    }

    private record MobStats(
            double maxHealth,
            double attackDamage,
            double armor,
            boolean bossScale
    ) {
        static final MobStats UNKNOWN = new MobStats(20.0, 0.0, 0.0, false);
    }
}
