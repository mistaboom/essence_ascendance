package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Generic, dependency-free progression inference for the procedural valuation model.
 *
 * <p>The index consumes the final loaded advancement JSON, so mod/datapack
 * overrides participate automatically. Evidence is deliberately trigger-aware:
 * an item is progression evidence only when a criterion indicates acquisition
 * or possession, not merely because the item is used while completing a late
 * advancement. Likewise, entity progression requires a specific kill/summon
 * target instead of a giant checklist such as "kill every mob".</p>
 */
final class ProceduralProgressionIndex {

    private static final String ADVANCEMENT_PREFIX = "advancement/";
    private static final int MAX_ENTITY_REFERENCES_PER_ADVANCEMENT = 4;

    private final Map<Item, ProgressionEvidence> itemEvidence;
    private final Map<Block, ProgressionEvidence> blockEvidence;
    private final Map<ResourceLocation, ProgressionEvidence> entityEvidence;
    private final Map<ResourceLocation, ProgressionEvidence> dimensionEvidence;
    private final Map<ResourceLocation, ProgressionEvidence> recipeEvidence;
    private final Map<ResourceLocation, ProgressionEvidence> lootTableEvidence;
    private final Summary summary;

    private ProceduralProgressionIndex(
            Map<Item, ProgressionEvidence> itemEvidence,
            Map<Block, ProgressionEvidence> blockEvidence,
            Map<ResourceLocation, ProgressionEvidence> entityEvidence,
            Map<ResourceLocation, ProgressionEvidence> dimensionEvidence,
            Map<ResourceLocation, ProgressionEvidence> recipeEvidence,
            Map<ResourceLocation, ProgressionEvidence> lootTableEvidence,
            Summary summary
    ) {
        this.itemEvidence = Map.copyOf(itemEvidence);
        this.blockEvidence = Map.copyOf(blockEvidence);
        this.entityEvidence = Map.copyOf(entityEvidence);
        this.dimensionEvidence = Map.copyOf(dimensionEvidence);
        this.recipeEvidence = Map.copyOf(recipeEvidence);
        this.lootTableEvidence = Map.copyOf(lootTableEvidence);
        this.summary = summary;
    }

    static ProceduralProgressionIndex build(MinecraftServer server) {
        Map<ResourceLocation, AdvancementNode> nodes = loadAdvancements(server);
        if (nodes.isEmpty()) {
            return empty();
        }

        Map<ResourceLocation, Integer> depthMemo = new HashMap<>();
        Map<ResourceLocation, ResourceLocation> rootMemo = new HashMap<>();
        Map<ResourceLocation, Integer> maxDepthByRoot = new HashMap<>();

        for (AdvancementNode node : nodes.values()) {
            int depth = depth(node.id(), nodes, depthMemo, new HashSet<>());
            ResourceLocation root = root(node.id(), nodes, rootMemo, new HashSet<>());
            maxDepthByRoot.merge(root, depth, Math::max);
        }

        Map<Item, MutableEvidence> items = new IdentityHashMap<>();
        Map<Block, MutableEvidence> blocks = new IdentityHashMap<>();
        Map<ResourceLocation, MutableEvidence> entities = new LinkedHashMap<>();
        Map<ResourceLocation, MutableEvidence> dimensions = new LinkedHashMap<>();
        Map<ResourceLocation, MutableEvidence> recipes = new LinkedHashMap<>();
        Map<ResourceLocation, MutableEvidence> lootTables = new LinkedHashMap<>();

        int considered = 0;
        int skippedRecipeAdvancements = 0;
        int referenceCount = 0;

        for (AdvancementNode node : nodes.values()) {
            String path = node.id().getPath();
            if (path.startsWith("recipes/") || path.contains("/recipes/")) {
                skippedRecipeAdvancements++;
                continue;
            }

            int depth = depthMemo.getOrDefault(node.id(), 0);
            ResourceLocation root = rootMemo.getOrDefault(node.id(), node.id());
            int treeMaxDepth = Math.max(1, maxDepthByRoot.getOrDefault(root, depth));
            double score = progressionScore(node, depth, treeMaxDepth);
            double confidence = evidenceConfidence(node, depth);
            String descriptor = node.id() + " depth " + depth + "/" + treeMaxDepth;

            ReferenceSet references = collectAdvancementReferences(node.json());

            for (ResourceLocation itemId : references.items) {
                BuiltInRegistries.ITEM.getOptional(itemId).ifPresent(item ->
                        addEvidence(items, item, score, confidence, descriptor + " -> acquired item")
                );
                referenceCount++;
            }
            for (ResourceLocation blockId : references.blocks) {
                BuiltInRegistries.BLOCK.getOptional(blockId).ifPresent(block ->
                        addEvidence(blocks, block, score * 0.90, confidence * 0.92, descriptor + " -> progression block")
                );
                referenceCount++;
            }

            // A specific boss/target advancement is useful progression evidence;
            // "kill every mob" style lists are not evidence that every ordinary
            // mob is late-game.
            if (references.entities.size() <= MAX_ENTITY_REFERENCES_PER_ADVANCEMENT) {
                for (ResourceLocation entityId : references.entities) {
                    if (BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).isPresent()) {
                        addEvidence(entities, entityId, score * 0.92, confidence * 0.94, descriptor + " -> specific entity");
                        referenceCount++;
                    }
                }
            }

            for (ResourceLocation dimensionId : references.dimensions) {
                addEvidence(dimensions, dimensionId, score, confidence, descriptor + " -> entered/required dimension");
                referenceCount++;
            }
            for (ResourceLocation recipeId : references.recipes) {
                // Only explicit recipe-crafted criteria land here. Recipe-book
                // rewards are intentionally ignored because they do not gate
                // crafting in vanilla semantics.
                addEvidence(recipes, recipeId, score * 0.70, confidence * 0.80, descriptor + " -> crafted recipe");
                referenceCount++;
            }
            for (ResourceLocation lootTableId : references.lootTables) {
                // Advancement loot rewards are genuine acquisition paths.
                addEvidence(lootTables, lootTableId, score, confidence, descriptor + " -> advancement loot reward");
                referenceCount++;
            }

            considered++;
        }

        Map<Item, ProgressionEvidence> frozenItems = freezeIdentity(items);
        Map<Block, ProgressionEvidence> frozenBlocks = freezeIdentity(blocks);
        Map<ResourceLocation, ProgressionEvidence> frozenEntities = freeze(entities);
        Map<ResourceLocation, ProgressionEvidence> frozenDimensions = freeze(dimensions);
        Map<ResourceLocation, ProgressionEvidence> frozenRecipes = freeze(recipes);
        Map<ResourceLocation, ProgressionEvidence> frozenLootTables = freeze(lootTables);

        Summary summary = new Summary(
                nodes.size(),
                considered,
                skippedRecipeAdvancements,
                maxDepthByRoot.size(),
                referenceCount,
                frozenItems.size(),
                frozenBlocks.size(),
                frozenEntities.size(),
                frozenDimensions.size(),
                frozenRecipes.size(),
                frozenLootTables.size()
        );

        EssenceAscendance.LOGGER.info(
                "Procedural progression index built: {} advancements ({} considered, {} recipe advancements skipped), {} trees, {} semantic references -> {} items, {} blocks, {} entities, {} dimensions, {} recipes, {} loot tables",
                summary.advancementCount(),
                summary.consideredAdvancementCount(),
                summary.skippedRecipeAdvancementCount(),
                summary.treeCount(),
                summary.referenceCount(),
                summary.itemReferenceCount(),
                summary.blockReferenceCount(),
                summary.entityReferenceCount(),
                summary.dimensionReferenceCount(),
                summary.recipeReferenceCount(),
                summary.lootTableReferenceCount()
        );

        return new ProceduralProgressionIndex(
                frozenItems,
                frozenBlocks,
                frozenEntities,
                frozenDimensions,
                frozenRecipes,
                frozenLootTables,
                summary
        );
    }

    static ProceduralProgressionIndex empty() {
        return new ProceduralProgressionIndex(
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                new Summary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        );
    }

    ProgressionEvidence forItem(Item item) {
        return itemEvidence.getOrDefault(item, ProgressionEvidence.NONE);
    }

    ProgressionEvidence forBlock(Block block) {
        return blockEvidence.getOrDefault(block, ProgressionEvidence.NONE);
    }

    ProgressionEvidence forEntity(ResourceLocation entityId) {
        return entityEvidence.getOrDefault(entityId, ProgressionEvidence.NONE);
    }

    ProgressionEvidence forDimension(ResourceLocation dimensionId) {
        return dimensionEvidence.getOrDefault(dimensionId, ProgressionEvidence.NONE);
    }

    ProgressionEvidence forRecipe(ResourceLocation recipeId) {
        return recipeEvidence.getOrDefault(recipeId, ProgressionEvidence.NONE);
    }

    ProgressionEvidence forLootTable(ResourceLocation lootTableId) {
        return lootTableEvidence.getOrDefault(lootTableId, ProgressionEvidence.NONE);
    }

    Summary summary() {
        return summary;
    }

    private static Map<ResourceLocation, AdvancementNode> loadAdvancements(MinecraftServer server) {
        Map<ResourceLocation, Resource> resources;
        try {
            resources = server.getResourceManager().listResources(
                    "advancement",
                    id -> id.getPath().endsWith(".json")
            );
        } catch (RuntimeException exception) {
            EssenceAscendance.LOGGER.warn(
                    "Procedural valuation could not enumerate advancements: {}",
                    exception.getMessage()
            );
            return Map.of();
        }

        Map<ResourceLocation, AdvancementNode> nodes = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation advancementId = advancementIdFromResource(entry.getKey());
            if (advancementId == null) {
                continue;
            }

            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed == null || !parsed.isJsonObject()) {
                    continue;
                }

                JsonObject root = parsed.getAsJsonObject();
                ResourceLocation parent = null;
                JsonElement parentElement = root.get("parent");
                if (parentElement != null && parentElement.isJsonPrimitive()) {
                    parent = ResourceLocation.tryParse(parentElement.getAsString());
                }
                nodes.put(advancementId, new AdvancementNode(advancementId, parent, root));
            } catch (IOException | RuntimeException exception) {
                EssenceAscendance.LOGGER.debug(
                        "Procedural valuation skipped advancement {}: {}",
                        entry.getKey(),
                        exception.getMessage()
                );
            }
        }
        return nodes;
    }

    private static ResourceLocation advancementIdFromResource(ResourceLocation resourceId) {
        String path = resourceId.getPath();
        if (!path.startsWith(ADVANCEMENT_PREFIX) || !path.endsWith(".json")) {
            return null;
        }
        String advancementPath = path.substring(
                ADVANCEMENT_PREFIX.length(),
                path.length() - ".json".length()
        );
        return advancementPath.isBlank()
                ? null
                : ResourceLocation.tryBuild(resourceId.getNamespace(), advancementPath);
    }

    private static int depth(
            ResourceLocation id,
            Map<ResourceLocation, AdvancementNode> nodes,
            Map<ResourceLocation, Integer> memo,
            Set<ResourceLocation> visiting
    ) {
        Integer cached = memo.get(id);
        if (cached != null) {
            return cached;
        }
        AdvancementNode node = nodes.get(id);
        if (node == null || node.parent() == null || !visiting.add(id)) {
            memo.put(id, 0);
            return 0;
        }
        int depth = 1 + depth(node.parent(), nodes, memo, visiting);
        visiting.remove(id);
        memo.put(id, depth);
        return depth;
    }

    private static ResourceLocation root(
            ResourceLocation id,
            Map<ResourceLocation, AdvancementNode> nodes,
            Map<ResourceLocation, ResourceLocation> memo,
            Set<ResourceLocation> visiting
    ) {
        ResourceLocation cached = memo.get(id);
        if (cached != null) {
            return cached;
        }
        AdvancementNode node = nodes.get(id);
        if (node == null || node.parent() == null || !nodes.containsKey(node.parent()) || !visiting.add(id)) {
            memo.put(id, id);
            return id;
        }
        ResourceLocation root = root(node.parent(), nodes, memo, visiting);
        visiting.remove(id);
        memo.put(id, root);
        return root;
    }

    private static double progressionScore(AdvancementNode node, int depth, int treeMaxDepth) {
        double relative = Math.min(1.0, (double) depth / Math.max(1, treeMaxDepth));
        double absolute = 1.0 - Math.exp(-Math.max(0, depth) / 4.0);
        double score = (relative * 0.48) + (absolute * 0.52);

        JsonObject display = node.json().has("display") && node.json().get("display").isJsonObject()
                ? node.json().getAsJsonObject("display")
                : null;
        if (display != null) {
            String frame = readString(display, "frame");
            if ("challenge".equals(frame)) {
                score += 0.15;
            } else if ("goal".equals(frame)) {
                score += 0.08;
            }
            if (display.has("hidden") && display.get("hidden").isJsonPrimitive()
                    && display.get("hidden").getAsBoolean()) {
                score += 0.03;
            }
        }

        return clamp01(score);
    }

    private static double evidenceConfidence(AdvancementNode node, int depth) {
        double confidence = depth > 0 ? 0.64 : 0.48;
        if (node.json().has("display")) {
            confidence += 0.08;
        }
        if (node.json().has("criteria")) {
            confidence += 0.08;
        }
        return Math.min(0.84, confidence);
    }

    private static ReferenceSet collectAdvancementReferences(JsonObject advancement) {
        ReferenceSet output = new ReferenceSet();
        JsonElement criteriaElement = advancement.get("criteria");
        if (criteriaElement != null && criteriaElement.isJsonObject()) {
            JsonObject criteria = criteriaElement.getAsJsonObject();
            for (Map.Entry<String, JsonElement> criterionEntry : criteria.entrySet()) {
                if (!criterionEntry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject criterion = criterionEntry.getValue().getAsJsonObject();
                String trigger = readString(criterion, "trigger");
                JsonElement conditions = criterion.get("conditions");
                if (trigger == null) {
                    continue;
                }
                String lowerTrigger = trigger.toLowerCase(Locale.ROOT);

                if (isAcquisitionTrigger(lowerTrigger)) {
                    collectItemReferences(conditions, output.items);
                }

                if (isSpecificEntityProgressionTrigger(lowerTrigger)) {
                    collectEntityReferences(conditions, output.entities);
                }

                if (isDimensionProgressionTrigger(lowerTrigger)) {
                    collectDimensionReferences(conditions, output.dimensions);
                }

                if (isBlockProgressionTrigger(lowerTrigger)) {
                    collectBlockReferences(conditions, output.blocks);
                }

                if (isRecipeProgressionTrigger(lowerTrigger)) {
                    collectRecipeReferences(conditions, output.recipes);
                }
            }
        }

        JsonElement rewards = advancement.get("rewards");
        if (rewards != null && rewards.isJsonObject()) {
            JsonObject rewardObject = rewards.getAsJsonObject();
            // Loot rewards are real item acquisition. Recipe rewards are recipe
            // book visibility and are intentionally not treated as a hard gate.
            collectResourceIds(rewardObject.get("loot"), output.lootTables);
        }
        return output;
    }

    private static boolean isAcquisitionTrigger(String trigger) {
        return trigger.endsWith(":inventory_changed")
                || trigger.contains("obtain")
                || trigger.contains("acquire")
                || trigger.contains("pickup")
                || trigger.contains("picked_up")
                || trigger.contains("item_collected");
    }

    private static boolean isSpecificEntityProgressionTrigger(String trigger) {
        return trigger.endsWith(":player_killed_entity")
                || trigger.endsWith(":summoned_entity")
                || trigger.contains("kill_entity")
                || trigger.contains("killed_entity")
                || trigger.contains("summon_entity");
    }

    private static boolean isDimensionProgressionTrigger(String trigger) {
        return trigger.endsWith(":changed_dimension")
                || trigger.contains("enter_dimension")
                || trigger.contains("entered_dimension")
                || trigger.contains("change_dimension");
    }

    private static boolean isBlockProgressionTrigger(String trigger) {
        return trigger.endsWith(":enter_block")
                || trigger.contains("break_block")
                || trigger.contains("mine_block")
                || trigger.contains("obtain_block");
    }

    private static boolean isRecipeProgressionTrigger(String trigger) {
        return trigger.endsWith(":recipe_crafted")
                || trigger.contains("craft_recipe")
                || trigger.contains("recipe_crafted");
    }

    private static void collectItemReferences(JsonElement element, Set<ResourceLocation> output) {
        collectReferencesByRegistry(element, output, ReferenceKind.ITEM);
    }

    private static void collectBlockReferences(JsonElement element, Set<ResourceLocation> output) {
        collectReferencesByRegistry(element, output, ReferenceKind.BLOCK);
    }

    private static void collectEntityReferences(JsonElement element, Set<ResourceLocation> output) {
        collectReferencesByRegistry(element, output, ReferenceKind.ENTITY);
    }

    private static void collectDimensionReferences(JsonElement element, Set<ResourceLocation> output) {
        collectReferencesByRegistry(element, output, ReferenceKind.DIMENSION);
    }

    private static void collectRecipeReferences(JsonElement element, Set<ResourceLocation> output) {
        collectReferencesByRegistry(element, output, ReferenceKind.RECIPE);
    }

    private static void collectReferencesByRegistry(
            JsonElement element,
            Set<ResourceLocation> output,
            ReferenceKind kind
    ) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectReferencesByRegistry(child, output, kind);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }

        JsonObject object = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            JsonElement value = entry.getValue();

            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                maybeAddReference(key, value.getAsString(), output, kind);
            } else if (value.isJsonArray()) {
                for (JsonElement child : value.getAsJsonArray()) {
                    if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
                        maybeAddReference(key, child.getAsString(), output, kind);
                    } else {
                        collectReferencesByRegistry(child, output, kind);
                    }
                }
            } else {
                collectReferencesByRegistry(value, output, kind);
            }
        }
    }

    private static void maybeAddReference(
            String key,
            String raw,
            Set<ResourceLocation> output,
            ReferenceKind kind
    ) {
        if (raw == null || raw.isBlank() || raw.startsWith("#")) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            return;
        }

        switch (kind) {
            case ITEM -> {
                if ((key.equals("item") || key.equals("items") || key.contains("ingredient") || key.contains("stack"))
                        && BuiltInRegistries.ITEM.getOptional(id).isPresent()) {
                    output.add(id);
                }
            }
            case BLOCK -> {
                if ((key.equals("block") || key.equals("blocks"))
                        && BuiltInRegistries.BLOCK.getOptional(id).isPresent()) {
                    output.add(id);
                }
            }
            case ENTITY -> {
                if ((key.equals("type") || key.equals("entity") || key.equals("entities")
                        || key.equals("entity_type") || key.equals("entity_types"))
                        && BuiltInRegistries.ENTITY_TYPE.getOptional(id).isPresent()) {
                    output.add(id);
                }
            }
            case DIMENSION -> {
                if (key.equals("dimension") || key.equals("dimensions") || key.equals("to")) {
                    output.add(id);
                }
            }
            case RECIPE -> {
                if (key.equals("recipe") || key.equals("recipes") || key.equals("recipe_id")) {
                    output.add(id);
                }
            }
        }
    }

    private static void collectResourceIds(JsonElement element, Set<ResourceLocation> output) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            if (id != null) {
                output.add(id);
            }
            return;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                collectResourceIds(child, output);
            }
        }
    }

    private static String readString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString()
                : null;
    }

    private static <K> void addEvidence(
            Map<K, MutableEvidence> output,
            K key,
            double score,
            double confidence,
            String source
    ) {
        output.computeIfAbsent(key, ignored -> new MutableEvidence())
                .add(score, confidence, source);
    }

    private static <K> Map<K, ProgressionEvidence> freeze(Map<K, MutableEvidence> mutable) {
        Map<K, ProgressionEvidence> result = new LinkedHashMap<>();
        mutable.forEach((key, value) -> result.put(key, value.freeze()));
        return result;
    }

    private static <K> Map<K, ProgressionEvidence> freezeIdentity(Map<K, MutableEvidence> mutable) {
        Map<K, ProgressionEvidence> result = new IdentityHashMap<>();
        mutable.forEach((key, value) -> result.put(key, value.freeze()));
        return result;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    record ProgressionEvidence(
            double score,
            double confidence,
            int evidenceCount,
            List<String> examples
    ) {
        static final ProgressionEvidence NONE = new ProgressionEvidence(0.0, 0.0, 0, List.of());

        ProgressionEvidence {
            score = clamp01(score);
            confidence = clamp01(confidence);
            evidenceCount = Math.max(0, evidenceCount);
            examples = List.copyOf(examples);
        }

        boolean present() {
            return evidenceCount > 0 && score > 0.0;
        }

        double multiplier() {
            if (!present()) {
                return 1.0;
            }
            return 1.0 + score * (ProceduralValuationSettings.ADVANCEMENT_PROGRESSION_MAX_MULTIPLIER - 1.0);
        }

        static ProgressionEvidence max(ProgressionEvidence first, ProgressionEvidence second) {
            if (first == null || !first.present()) {
                return second == null ? NONE : second;
            }
            if (second == null || !second.present()) {
                return first;
            }
            return first.score >= second.score ? first : second;
        }
    }

    record Summary(
            int advancementCount,
            int consideredAdvancementCount,
            int skippedRecipeAdvancementCount,
            int treeCount,
            int referenceCount,
            int itemReferenceCount,
            int blockReferenceCount,
            int entityReferenceCount,
            int dimensionReferenceCount,
            int recipeReferenceCount,
            int lootTableReferenceCount
    ) {
    }

    private record AdvancementNode(ResourceLocation id, ResourceLocation parent, JsonObject json) {
    }

    private enum ReferenceKind {
        ITEM,
        BLOCK,
        ENTITY,
        DIMENSION,
        RECIPE
    }

    private static final class ReferenceSet {
        private final Set<ResourceLocation> items = new LinkedHashSet<>();
        private final Set<ResourceLocation> blocks = new LinkedHashSet<>();
        private final Set<ResourceLocation> entities = new LinkedHashSet<>();
        private final Set<ResourceLocation> dimensions = new LinkedHashSet<>();
        private final Set<ResourceLocation> recipes = new LinkedHashSet<>();
        private final Set<ResourceLocation> lootTables = new LinkedHashSet<>();
    }

    private static final class MutableEvidence {
        private double score;
        private double confidence;
        private int count;
        private final List<String> examples = new ArrayList<>();

        void add(double candidateScore, double candidateConfidence, String source) {
            score = Math.max(score, clamp01(candidateScore));
            confidence = Math.max(confidence, clamp01(candidateConfidence));
            count++;
            if (source != null && !source.isBlank()
                    && examples.size() < ProceduralValuationSettings.MAX_PROGRESSION_EVIDENCE_EXAMPLES
                    && !examples.contains(source)) {
                examples.add(source);
            }
        }

        ProgressionEvidence freeze() {
            return new ProgressionEvidence(score, confidence, count, List.copyOf(examples));
        }
    }
}
