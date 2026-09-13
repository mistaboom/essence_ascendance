package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.EssenceAscendance;
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
 * /essence debug valuation calls cheap enough to use interactively while tuning
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

    static ProceduralValuationIndex build(MinecraftServer server) {
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

        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (!ValuationGenerationInputs.recipeAllowed(holder.id(), recipe.getType())) continue;

            try {
                ItemStack result = recipe.getResultItem(server.registryAccess());
                if (result == null || result.isEmpty() || result.is(Items.AIR) || !ValuationGenerationInputs.itemAllowed(result.getItem())) {
                    skippedRecipes++;
                    continue;
                }

                List<IngredientChoice> ingredients = new ArrayList<>();
                boolean unresolvedIngredient = false;
                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient == Ingredient.EMPTY) continue;
                    if (ingredient == null) { unresolvedIngredient = true; continue; }

                    LinkedHashSet<Item> alternatives = new LinkedHashSet<>();
                    for (ItemStack candidate : ingredient.getItems()) {
                        if (candidate != null && !candidate.isEmpty() && !candidate.is(Items.AIR) && ValuationGenerationInputs.itemAllowed(candidate.getItem())) {
                            alternatives.add(candidate.getItem());
                        }
                    }

                    if (!alternatives.isEmpty()) {
                        ingredients.add(new IngredientChoice(List.copyOf(alternatives)));
                    } else { unresolvedIngredient = true; }
                }

                if (ingredients.isEmpty() || unresolvedIngredient) {
                    skippedRecipes++;
                    continue;
                }

                RecipeModel model = new RecipeModel(
                        holder.id(),
                        recipe.getType(),
                        result.getItem(),
                        ValuationGenerationInputs.outputCount(holder.id(), recipe.getType(), Math.max(1, result.getCount())),
                        List.copyOf(ingredients)
                );

                byOutput.computeIfAbsent(result.getItem(), ignored -> new ArrayList<>())
                        .add(model);

                for (IngredientChoice ingredient : ingredients) {
                    for (Item candidate : ingredient.alternatives()) {
                        byIngredient.computeIfAbsent(candidate, ignored -> new ArrayList<>())
                                .add(new RecipeUse(model));
                        ingredientLinks++;
                    }
                }

                indexedRecipeIds.add(holder.id());
                recipeCount++;

            } catch (RuntimeException exception) {
                skippedRecipes++;
                EssenceAscendance.LOGGER.debug(
                        "Procedural valuation skipped recipe {}: {}",
                        holder.id(),
                        exception.getMessage()
                );
            }
        }

        FallbackRecipeStats smithingFallbacks = scanSmithingTransformRecipeFallbacks(
                server,
                byOutput,
                byIngredient,
                indexedRecipeIds
        );
        recipeCount += smithingFallbacks.recipeCount();
        ingredientLinks += smithingFallbacks.ingredientLinks();

        ProceduralNaturalBlockIndex naturalBlockIndex = ProceduralNaturalBlockIndex.build(server);
        // Positive runtime interaction relationships (no hand-authored item prices).
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

        Map<Item, List<ConservationEdge>> conservationEdges = buildConservationEdges(byOutput);
        Map<Item, List<Item>> conservationGroups = buildConservationGroups(conservationEdges);
        ProceduralStructureIndex structureIndex = ProceduralStructureIndex.build(server);
        ProceduralMobSpawnIndex mobSpawnIndex = ProceduralMobSpawnIndex.build(server, structureIndex);
        Map<Item, List<BiologicalSource>> biologicalSources = biologicalSources(mobSpawnIndex);
        ProceduralTradeIndex tradeIndex = ProceduralTradeIndex.build(server);

        int lootTablesScanned = scanEntityLootTables(server, drops, mobSpawnIndex);
        addVanillaHardcodedEntitySources(drops, mobSpawnIndex);
        int dropLinks = drops.values().stream().mapToInt(List::size).sum();
        int blockLootTablesScanned = scanBlockLootTables(server, blockDrops);
        int blockDropLinks = blockDrops.values().stream().mapToInt(List::size).sum();
        int containerLootTablesScanned = scanContainerLootTables(server, containerLoot, structureIndex);
        addVanillaFixedStructureSources(containerLoot, structureIndex);
        int containerLootLinks = containerLoot.values().stream().mapToInt(List::size).sum();
        int fishingLootTablesScanned = scanFishingLootTables(server, fishingLoot);
        int fishingLootLinks = fishingLoot.values().stream().mapToInt(List::size).sum();
        ProceduralProgressionIndex progressionIndex = ProceduralProgressionIndex.build(server);
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
        Item input = model.singleIngredientIdentity();
        if (input == null || input == model.outputItem()) {
            return false;
        }

        int forwardInputCount = model.ingredients().size();
        int forwardOutputCount = model.outputCount();

        for (RecipeModel reverse : recipesByOutput.getOrDefault(input, List.of())) {
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

    private static FallbackRecipeStats scanSmithingTransformRecipeFallbacks(
            MinecraftServer server,
            Map<Item, List<RecipeModel>> byOutput,
            Map<Item, List<RecipeUse>> byIngredient,
            Set<ResourceLocation> indexedRecipeIds
    ) {
        Map<ResourceLocation, Resource> resources;
        try {
            resources = server.getResourceManager().listResources(
                    "recipe",
                    id -> id.getPath().endsWith(".json")
            );
        } catch (RuntimeException exception) {
            EssenceAscendance.LOGGER.debug(
                    "Procedural valuation could not enumerate data recipe fallbacks: {}",
                    exception.getMessage()
            );
            return FallbackRecipeStats.EMPTY;
        }

        int recipes = 0;
        int links = 0;
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation recipeId = recipeIdFromResource(entry.getKey());
            if (recipeId == null || indexedRecipeIds.contains(recipeId)) {
                continue;
            }
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed == null || !parsed.isJsonObject()) {
                    continue;
                }
                JsonObject root = parsed.getAsJsonObject();
                String type = readString(root, "type");
                if (type == null || !type.endsWith("smithing_transform")) {
                    continue;
                }
                if (!ValuationGenerationInputs.recipeAllowed(recipeId, RecipeType.SMITHING)) continue;

                Item outputItem = readRecipeResultItem(root.get("result"));
                if (outputItem == null || outputItem == Items.AIR || !ValuationGenerationInputs.itemAllowed(outputItem)) {
                    continue;
                }
                int outputCount = readRecipeResultCount(root.get("result"));

                List<IngredientChoice> ingredients = new ArrayList<>();
                for (String key : List.of("template", "base", "addition")) {
                    IngredientChoice ingredient = readDataIngredient(root.get(key));
                    if (ingredient != null && !ingredient.alternatives().isEmpty()) {
                        ingredients.add(ingredient);
                    }
                }
                if (ingredients.isEmpty()) {
                    continue;
                }

                RecipeModel model = new RecipeModel(
                        recipeId,
                        RecipeType.SMITHING,
                        outputItem,
                        ValuationGenerationInputs.outputCount(recipeId, RecipeType.SMITHING, Math.max(1, outputCount)),
                        List.copyOf(ingredients)
                );
                byOutput.computeIfAbsent(outputItem, ignored -> new ArrayList<>()).add(model);
                for (IngredientChoice ingredient : ingredients) {
                    for (Item candidate : ingredient.alternatives()) {
                        byIngredient.computeIfAbsent(candidate, ignored -> new ArrayList<>())
                                .add(new RecipeUse(model));
                        links++;
                    }
                }
                indexedRecipeIds.add(recipeId);
                recipes++;
            } catch (IOException | RuntimeException exception) {
                EssenceAscendance.LOGGER.debug(
                        "Procedural valuation skipped smithing recipe fallback {}: {}",
                        entry.getKey(),
                        exception.getMessage()
                );
            }
        }

        if (recipes > 0) {
            EssenceAscendance.LOGGER.info(
                    "Procedural valuation recovered {} smithing-transform recipes from final data resources",
                    recipes
            );
        }
        return new FallbackRecipeStats(recipes, links);
    }

    private static ResourceLocation recipeIdFromResource(ResourceLocation resourceId) {
        String path = resourceId.getPath();
        String prefix = "recipe/";
        if (!path.startsWith(prefix) || !path.endsWith(".json")) {
            return null;
        }
        String recipePath = path.substring(prefix.length(), path.length() - ".json".length());
        return recipePath.isBlank()
                ? null
                : ResourceLocation.tryBuild(resourceId.getNamespace(), recipePath);
    }

    private static Item readRecipeResultItem(JsonElement resultElement) {
        if (resultElement == null || resultElement.isJsonNull()) {
            return null;
        }
        String raw = null;
        if (resultElement.isJsonPrimitive() && resultElement.getAsJsonPrimitive().isString()) {
            raw = resultElement.getAsString();
        } else if (resultElement.isJsonObject()) {
            JsonObject object = resultElement.getAsJsonObject();
            raw = readString(object, "id");
            if (raw == null) {
                raw = readString(object, "item");
            }
        }
        ResourceLocation id = raw == null ? null : ResourceLocation.tryParse(raw);
        return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    private static int readRecipeResultCount(JsonElement resultElement) {
        if (resultElement != null && resultElement.isJsonObject()) {
            return Math.max(1, (int) Math.round(readDouble(resultElement.getAsJsonObject(), "count", 1.0)));
        }
        return 1;
    }

    private static IngredientChoice readDataIngredient(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        String raw = null;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            raw = element.getAsString();
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            raw = readString(object, "item");
            if (raw == null) {
                raw = readString(object, "id");
            }
        }
        if (raw == null || raw.startsWith("#")) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return item == null ? null : new IngredientChoice(List.of(item));
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
            ProceduralStructureIndex structureIndex
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
                false
        ));
    }

    private static int scanContainerLootTables(
            MinecraftServer server,
            Map<Item, List<ContainerLootSource>> output,
            ProceduralStructureIndex structureIndex
    ) {
        Map<ResourceLocation, Resource> resources;
        try {
            resources = server.getResourceManager().listResources(
                    "loot_table",
                    id -> id.getPath().endsWith(".json")
            );
        } catch (RuntimeException exception) {
            EssenceAscendance.LOGGER.warn(
                    "Procedural valuation could not enumerate container loot tables: {}",
                    exception.getMessage()
            );
            return 0;
        }

        Map<ResourceLocation, JsonObject> tables = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation tableId = lootTableIdFromResource(entry.getKey());
            if (tableId == null) {
                continue;
            }
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonElement root = JsonParser.parseReader(reader);
                if (root != null && root.isJsonObject()) {
                    tables.put(tableId, root.getAsJsonObject());
                }
            } catch (IOException | RuntimeException exception) {
                EssenceAscendance.LOGGER.debug(
                        "Procedural valuation skipped loot table {} while indexing containers: {}",
                        entry.getKey(),
                        exception.getMessage()
                );
            }
        }

        Map<ResourceLocation, Map<Item, ContainerEstimate>> memo = new HashMap<>();
        int scanned = 0;
        for (Map.Entry<ResourceLocation, JsonObject> entry : tables.entrySet()) {
            ResourceLocation tableId = entry.getKey();
            JsonObject root = entry.getValue();
            if (!isContainerLootTable(tableId, root) && !isArchaeologyLootTable(tableId, root)) {
                continue;
            }

            boolean archaeology = isArchaeologyLootTable(tableId, root);
            ContainerContext context = containerContext(tableId, structureIndex);
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

            for (Map.Entry<Item, ContainerEstimate> estimateEntry : estimates.entrySet()) {
                ContainerEstimate estimate = estimateEntry.getValue();
                if (estimate.occurrenceChance() <= 0.0 || estimate.expectedCount() <= 0.0) continue;
                ContainerLootSource source = new ContainerLootSource(
                        tableId,
                        clampProbability(estimate.occurrenceChance()),
                        Math.max(0.01, estimate.expectedCount()),
                        estimate.complexConditionCount(),
                        context.tierLabel(),
                        context.progressionBand(),
                        context.contextMultiplier(),
                        context.structureFrequencyKnown(),
                        context.structureId(),
                        context.templateReferenceCount(),
                        context.signals(),
                        archaeology
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
        Map<ResourceLocation, Resource> resources;
        try {
            resources = server.getResourceManager().listResources(
                    "loot_table",
                    id -> id.getPath().endsWith(".json")
            );
        } catch (RuntimeException exception) {
            EssenceAscendance.LOGGER.warn(
                    "Procedural valuation could not enumerate fishing loot tables: {}",
                    exception.getMessage()
            );
            return 0;
        }

        Map<ResourceLocation, JsonObject> tables = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation tableId = lootTableIdFromResource(entry.getKey());
            if (tableId == null) {
                continue;
            }
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonElement root = JsonParser.parseReader(reader);
                if (root != null && root.isJsonObject()) {
                    tables.put(tableId, root.getAsJsonObject());
                }
            } catch (IOException | RuntimeException exception) {
                EssenceAscendance.LOGGER.debug(
                        "Procedural valuation skipped loot table {} while indexing fishing: {}",
                        entry.getKey(),
                        exception.getMessage()
                );
            }
        }

        Map<ResourceLocation, Map<Item, ContainerEstimate>> memo = new HashMap<>();
        int scanned = 0;
        for (Map.Entry<ResourceLocation, JsonObject> entry : tables.entrySet()) {
            ResourceLocation tableId = entry.getKey();
            JsonObject root = entry.getValue();
            if (!isFishingRootLootTable(tableId, root)) {
                continue;
            }

            FishingContext context = fishingContext(tableId);
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

    private static Map<Item, ContainerEstimate> estimateContainerTable(
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
        if (visiting.isEmpty()) memo.put(tableId, result);
        return result;
    }

    private static void estimateContainerPool(
            JsonObject pool,
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
        int poolComplex = poolChance.complexConditions();
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
            int complex,
            Map<ResourceLocation, JsonObject> tables,
            Map<ResourceLocation, Map<Item, ContainerEstimate>> memo,
            java.util.Set<ResourceLocation> visiting,
            Map<Item, MutableContainerEstimate> output
    ) {
        if (perRollSelection <= 0.0) {
            return;
        }

        String type = readString(entry, "type");
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

        if (type != null && type.endsWith(":loot_table")) {
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

        boolean alternatives = type != null && type.endsWith(":alternatives");
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
        Map<ResourceLocation, JsonObject> tables = new LinkedHashMap<>();
        try {
            Map<ResourceLocation, Resource> resources = server.getResourceManager().listResources(
                    "loot_table", id -> id.getPath().endsWith(".json"));
            for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
                ResourceLocation tableId = lootTableIdFromResource(entry.getKey());
                if (tableId == null) continue;
                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    JsonElement root = JsonParser.parseReader(reader);
                    if (root != null && root.isJsonObject()) tables.put(tableId, root.getAsJsonObject());
                } catch (IOException | RuntimeException exception) {
                    EssenceAscendance.LOGGER.debug("Procedural entity loot skipped {}: {}", tableId, exception.getMessage());
                }
            }
        } catch (RuntimeException exception) {
            EssenceAscendance.LOGGER.warn("Procedural entity loot enumeration failed: {}", exception.getMessage());
            return 0;
        }
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
            for (ProceduralBlockHarvest.HarvestDrop drop : entry.getValue()) {
                List<String> signals = new ArrayList<>(stats.signals());
                signals.addAll(drop.signals());
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
        int complexConditions = complexConditionCount + conditionInfo.complexConditions();
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

            if (conditionType.endsWith("random_chance") && condition.has("chance")) {
                multiplier *= estimateNumberProvider(condition.get("chance"), 1.0);
                continue;
            }
            if (conditionType.endsWith("random_chance_with_enchanted_bonus")
                    && condition.has("unenchanted_chance")) {
                multiplier *= estimateNumberProvider(condition.get("unenchanted_chance"), 1.0);
                continue;
            }
            if (conditionType.endsWith("table_bonus")
                    && condition.has("chances")
                    && condition.get("chances").isJsonArray()
                    && !condition.getAsJsonArray("chances").isEmpty()) {
                multiplier *= estimateNumberProvider(
                        condition.getAsJsonArray("chances").get(0),
                        1.0
                );
                continue;
            }
            if (conditionType.endsWith("killed_by_player")
                    || conditionType.endsWith("survives_explosion")) {
                continue;
            }
            if (conditionType.endsWith("entity_properties")) {
                MobStats variant = slimeVariantStats(condition, entityId, stats);
                if (variant != null) {
                    stats = variant;
                    signals.add("entity loot variant: small slime size requirement modeled");
                    continue;
                }
            }
            if (conditionType.endsWith("inverted")
                    && condition.has("term")
                    && condition.get("term").isJsonObject()
                    && isFrogDamageSourceCondition(condition.getAsJsonObject("term"))) {
                // This is the ordinary non-frog branch of the slime loot table.
                // A player kill satisfies it deterministically.
                signals.add("ordinary non-frog kill branch modeled");
                continue;
            }
            if (conditionType.endsWith("damage_source_properties")
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
        if (type == null || !type.endsWith("slime") || !typeSpecific.has("size")) {
            return null;
        }

        double size = Math.max(1.0, estimateNumberProvider(typeSpecific.get("size"), 1.0));
        double health = Math.max(1.0, size * size);
        double attack = size <= 1.0 ? 0.0 : size;
        return new MobStats(health, attack, baseStats.armor(), false);
    }

    private static boolean isFrogDamageSourceCondition(JsonObject condition) {
        String conditionType = readString(condition, "condition");
        if (conditionType == null || !conditionType.endsWith("damage_source_properties")) {
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

            if (conditionType.endsWith("random_chance") && condition.has("chance")) {
                multiplier *= estimateNumberProvider(condition.get("chance"), 1.0);
            } else if (conditionType.endsWith("random_chance_with_enchanted_bonus")
                    && condition.has("unenchanted_chance")) {
                // Use the deterministic no-Looting baseline. Looting can only
                // make the real acquisition easier, so this remains a safe
                // baseline without making the source conditional/unknown.
                multiplier *= estimateNumberProvider(condition.get("unenchanted_chance"), 1.0);
            } else if (conditionType.endsWith("table_bonus")
                    && condition.has("chances")
                    && condition.get("chances").isJsonArray()
                    && !condition.getAsJsonArray("chances").isEmpty()) {
                // Baseline enchantment level 0 is the first table-bonus chance.
                multiplier *= estimateNumberProvider(
                        condition.getAsJsonArray("chances").get(0),
                        1.0
                );
            } else if (conditionType.endsWith("killed_by_player")
                    || conditionType.endsWith("survives_explosion")) {
                // These are ordinary acquisition-context conditions. They do
                // not make the source ambiguous for a player-centric valuation.
            } else if (conditionType.endsWith("entity_properties")
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
                && type.endsWith("fishing_hook")
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
            String functionType = readString(function, "function");
            if (functionType != null
                    && functionType.endsWith("set_count")
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

    private record FallbackRecipeStats(int recipeCount, int ingredientLinks) {
        private static final FallbackRecipeStats EMPTY = new FallbackRecipeStats(0, 0);
    }

    record RecipeModel(
            ResourceLocation id,
            RecipeType<?> type,
            Item outputItem,
            int outputCount,
            List<IngredientChoice> ingredients
    ) {
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

    record IngredientChoice(List<Item> alternatives) {
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
            boolean archaeology
    ) {
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

    private record ContainerEstimate(
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
