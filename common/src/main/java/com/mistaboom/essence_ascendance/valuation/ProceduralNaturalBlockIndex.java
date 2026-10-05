package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Positive natural-placement evidence, not a worldgen-density model. A missing
 * recipe is NOT evidence that a machine or technical block occurs naturally.
 * Follows loaded biome -> placed -> configured feature data, plus terrain
 * surface/default states. The matching block must ALSO have an actual loot
 * relationship before this index can admit a self-drop acquisition path.
 * Loader-only features with no data representation remain unknown, not free.
 */
final class ProceduralNaturalBlockIndex {
    private static final int MAX_DEPTH = 48;
    private final Map<ResourceLocation, List<String>> evidence;

    private ProceduralNaturalBlockIndex(Map<ResourceLocation, List<String>> evidence) {
        Map<ResourceLocation, List<String>> copy = new LinkedHashMap<>();
        evidence.forEach((id, signals) -> copy.put(id, List.copyOf(signals)));
        this.evidence = Map.copyOf(copy);
    }

    static ProceduralNaturalBlockIndex build(GenerationDataSnapshot data) {
        return fromData(data.naturalBiomes(), data.json("worldgen/placed_feature"),
                data.json("worldgen/configured_feature"), data.terrain());
    }

    // Package-visible for deterministic fixture tests; no world access or generation.
    static ProceduralNaturalBlockIndex fromData(
            Map<ResourceLocation, JsonObject> biomes,
            Map<ResourceLocation, JsonObject> placed,
            Map<ResourceLocation, JsonObject> configured,
            Map<ResourceLocation, JsonObject> terrain
    ) {
        Scanner scan = new Scanner(placed, configured);
        biomes.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                scan.placed(entry.getValue().get("features"), "biome " + entry.getKey(), 0));
        terrain.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String source = "terrain definition " + entry.getKey();
            scan.states(entry.getValue().get("default_block"), source, 0);
            scan.states(entry.getValue().get("default_fluid"), source, 0);
            scan.states(entry.getValue().get("surface_rule"), source, 0);
        });
        return new ProceduralNaturalBlockIndex(scan.evidence);
    }

    boolean contains(ResourceLocation blockId) { return evidence.containsKey(blockId); }
    List<String> signals(ResourceLocation blockId) { return evidence.getOrDefault(blockId, List.of()); }

    private static Map<ResourceLocation, JsonObject> load(MinecraftServer server, String prefix) {
        Map<ResourceLocation, JsonObject> result = new LinkedHashMap<>();
        try {
            Map<ResourceLocation, Resource> resources = server.getResourceManager().listResources(
                    prefix.substring(0, prefix.length() - 1), id -> id.getPath().endsWith(".json"));
            for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
                String path = entry.getKey().getPath();
                if (!path.startsWith(prefix)) continue;
                ResourceLocation id = ResourceLocation.tryBuild(entry.getKey().getNamespace(),
                        path.substring(prefix.length(), path.length() - 5));
                if (id == null) continue;
                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed != null && parsed.isJsonObject()) result.put(id, parsed.getAsJsonObject());
                } catch (IOException | RuntimeException exception) {
                    EssenceAscendance.LOGGER.debug("Procedural natural-source data skipped {}: {}", id, exception.getMessage());
                }
            }
        } catch (RuntimeException exception) {
            EssenceAscendance.LOGGER.debug("Procedural natural-source data unavailable for {}: {}", prefix, exception.getMessage());
        }
        return result;
    }

    private static final class Scanner {
        private final Map<ResourceLocation, JsonObject> placed;
        private final Map<ResourceLocation, JsonObject> configured;
        private final Set<ResourceLocation> seenPlaced = new HashSet<>();
        private final Set<ResourceLocation> seenConfigured = new HashSet<>();
        private final Map<ResourceLocation, List<String>> evidence = new LinkedHashMap<>();

        Scanner(Map<ResourceLocation, JsonObject> placed, Map<ResourceLocation, JsonObject> configured) {
            this.placed = placed;
            this.configured = configured;
        }

        void placed(JsonElement element, String source, int depth) {
            if (element == null || element.isJsonNull() || depth > MAX_DEPTH) return;
            if (element.isJsonArray()) {
                for (JsonElement child : element.getAsJsonArray()) placed(child, source, depth + 1);
            } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
                if (id != null && seenPlaced.add(id)) placed(placed.get(id), "placed feature " + id, depth + 1);
            } else if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (zeroOccurrence(object)) return;
                JsonElement modifiers = object.get("placement");
                if (modifiers != null && modifiers.isJsonArray()) {
                    for (JsonElement modifier : modifiers.getAsJsonArray()) {
                        if (!modifier.isJsonObject()) continue;
                        JsonObject rule = modifier.getAsJsonObject();
                        JsonElement type = rule.get("type");
                        if (type != null && type.isJsonPrimitive()
                                && "minecraft:count".equals(type.getAsString())
                                && nonpositiveNumber(rule.get("count"))) return;
                    }
                }
                configured(object.get("feature"), source, depth + 1);
            }
        }

        void configured(JsonElement element, String source, int depth) {
            if (element == null || element.isJsonNull() || depth > MAX_DEPTH) return;
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
                if (id != null && seenConfigured.add(id)) configured(configured.get(id), "configured feature " + id, depth + 1);
            } else if (element.isJsonObject()) {
                JsonObject feature = element.getAsJsonObject();
                if (zeroOccurrence(feature)) return;
                JsonElement config = feature.get("config");
                if (config != null && config.isJsonObject() && !zeroOccurrence(config.getAsJsonObject())
                        && isType(feature, "minecraft:tree")) {
                    treeDecorators(config.getAsJsonObject().get("decorators"), source);
                }
                states(config, source, depth + 1);
            }
        }

        private void treeDecorators(JsonElement element, String source) {
            if (element == null || !element.isJsonArray()) return;
            for (JsonElement child : element.getAsJsonArray()) {
                if (!child.isJsonObject()) continue;
                JsonObject decorator = child.getAsJsonObject();
                // CocoaDecorator's codec contains only probability; its placement
                // implementation supplies Blocks.COCOA rather than a serialized
                // block state. Interpret this known feature/decorator combination,
                // never an arbitrary decorator with a similar name or field.
                if (!zeroOccurrence(decorator) && isType(decorator, "minecraft:cocoa")
                        && positiveProbability(decorator.get("probability"))) {
                    addEvidence(ResourceLocation.parse("minecraft:cocoa"),
                            source + " via minecraft:cocoa tree decorator");
                }
            }
        }

        private boolean isType(JsonObject object, String expected) {
            JsonElement type = object.get("type");
            return type != null && type.isJsonPrimitive() && type.getAsJsonPrimitive().isString()
                    && ResourceLocation.parse(expected).equals(ResourceLocation.tryParse(type.getAsString()));
        }

        private boolean positiveProbability(JsonElement value) {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return false;
            double probability = value.getAsDouble();
            return Double.isFinite(probability) && probability > 0.0 && probability <= 1.0;
        }

        private void addEvidence(ResourceLocation id, String source) {
            List<String> signals = evidence.computeIfAbsent(id, ignored -> new ArrayList<>());
            if (signals.size() < 3 && !signals.contains(source)) signals.add(source);
        }

        private boolean zeroOccurrence(JsonObject object) {
            return nonpositiveNumber(object.get("chance")) || nonpositiveNumber(object.get("weight"));
        }

        private boolean nonpositiveNumber(JsonElement value) {
            if (value == null || !value.isJsonPrimitive()) return false;
            try { return value.getAsDouble() <= 0.0; }
            catch (RuntimeException ignored) { return false; }
        }

        void states(JsonElement element, String source, int depth) {
            if (element == null || element.isJsonNull() || depth > MAX_DEPTH) return;
            if (element.isJsonArray()) {
                for (JsonElement child : element.getAsJsonArray()) states(child, source, depth + 1);
                return;
            }
            if (!element.isJsonObject()) return;
            JsonObject object = element.getAsJsonObject();
            if (zeroOccurrence(object)) return;
            JsonElement name = object.get("Name");
            if (name != null && name.isJsonPrimitive() && name.getAsJsonPrimitive().isString()) {
                ResourceLocation id = ResourceLocation.tryParse(name.getAsString());
                if (id != null) addEvidence(id, source);
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String key = entry.getKey();
                // Replacement predicates describe what is tested, NOT what is placed.
                if (key.equals("target") || key.equals("predicate") || key.equals("if_true")
                        || key.equals("condition") || key.equals("conditions")) continue;
                if (key.equals("feature") || key.equals("default")) placed(entry.getValue(), source, depth + 1);
                if (key.equals("features") && entry.getValue().isJsonArray())
                    placed(entry.getValue(), source, depth + 1);
                states(entry.getValue(), source, depth + 1);
            }
        }
    }
}
