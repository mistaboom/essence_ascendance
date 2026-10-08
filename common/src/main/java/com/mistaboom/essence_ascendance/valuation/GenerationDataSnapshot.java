package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mistaboom.essence_ascendance.balance.generated.BalancePerformance;
import dev.architectury.platform.Platform;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.storage.loot.LootTable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * One synchronous generation's effective data. Native holders are read-only for this operation;
 * normalized JSON stays private and is shared by the existing indexes. Adapter JSON access returns
 * detached copies. No chunk, dimension creation, player, inventory or asynchronous event access.
 */
public final class GenerationDataSnapshot implements AutoCloseable {
    private MinecraftServer server;
    private final GenerationEpoch epoch;
    private List<Item> items;
    private List<RecipeHolder<?>> recipeHolders;
    private Map<String, String> mods;
    private Map<String, List<String>> itemTags;
    private final Map<String, Map<ResourceLocation, JsonObject>> definitions = new TreeMap<>();
    private JsonObject routingDiagnostics;
    private JsonObject competitiveCapabilities;
    public void competitiveCapabilities(JsonObject evidence) {
        requireWritable();
        if (competitiveCapabilities != null) throw new IllegalStateException("Capability census already collected");
        competitiveCapabilities = evidence;
    }
    private com.mistaboom.essence_ascendance.balance.economy.ProductionGraph production;
    private final Map<String, Long> workloads = new TreeMap<>();
    private Map<ResourceLocation, JsonObject> naturalBiomes = Map.of(), terrain = Map.of();
    private final List<DimensionEvidence> dimensions = new ArrayList<>();
    private final List<String> limitations = new ArrayList<>();
    private boolean integrationReadOnly;
    /** Adapters may read/lazily normalize shared inputs, but may publish only their staged outputs. */
    public <T> T readOnlyIntegration(java.util.function.Supplier<T> action) {
        requireOpen(); boolean previous = integrationReadOnly; integrationReadOnly = true;
        try { return action.get(); } finally { integrationReadOnly = previous; }
    }
    private void requireWritable() {
        requireOpen();
        if (integrationReadOnly) throw new IllegalStateException("Optional integration must publish detached output through its coordinator");
    }
    private boolean structuresEnabled;
    private final Set<ResourceLocation> eligibleStructures = new java.util.TreeSet<>(), eligibleStructureSets = new java.util.TreeSet<>();
    private final Map<String, Long> unsupportedRecipeFamilies = new TreeMap<>();
    private final Map<String, Long> unsupportedLootFunctions = new TreeMap<>();
    private LootrPolicy lootr = LootrPolicy.ABSENT;
    private final Map<ResourceLocation, List<String>> structureDimensions = new TreeMap<>();
    private final Map<String, com.mistaboom.essence_ascendance.balance.engine.SourceAvailability> lootAvailability = new TreeMap<>();
    private final Map<String, List<String>> lootSemantics = new TreeMap<>();
    private List<RuntimeLootAudit.Modifier> runtimeLootModifiers = List.of();
    List<RuntimeLootAudit.Modifier> runtimeLootModifiers() { requireOpen(); return runtimeLootModifiers; }
    private final Map<ResourceLocation, Map<Item, ProceduralValuationIndex.ContainerEstimate>> lootEstimates = new TreeMap<>();
    Map<ResourceLocation, Map<Item, ProceduralValuationIndex.ContainerEstimate>> lootEstimates() { requireOpen(); return lootEstimates; }
    public void lootr(LootrPolicy policy) { requireWritable(); lootr = policy; }
    LootrPolicy lootr() { requireOpen(); return lootr; }
    public List<String> structureDimensions(ResourceLocation structure) { requireOpen(); return List.copyOf(structureDimensions.getOrDefault(structure, List.of())); }
    void lootAvailability(String key, com.mistaboom.essence_ascendance.balance.engine.SourceAvailability value) {
        requireOpen(); lootAvailability.putIfAbsent(key, value);
    }
    void retainLootSemantics(ResourceLocation table) {
        if (lootSemantics.containsKey(table.toString())) return;
        var definition = json("loot_evidence").get(table);
        if (definition == null) return;
        lootSemantics.put(table.toString(), LootSemanticsAudit.facts(definition));
        retainNestedLootSemantics(definition);
    }
    private void retainNestedLootSemantics(com.google.gson.JsonElement element) {
        if (element == null || element.isJsonPrimitive() || element.isJsonNull()) return;
        if (element.isJsonArray()) { element.getAsJsonArray().forEach(this::retainNestedLootSemantics); return; }
        var object = element.getAsJsonObject();
        if (object.has("type") && "minecraft:loot_table".equals(object.get("type").getAsString())) {
            var reference = object.has("value") ? object.get("value") : object.get("name");
            if (reference != null && reference.isJsonPrimitive()) {
                var id = ResourceLocation.tryParse(reference.getAsString()); if (id != null) retainLootSemantics(id);
            }
        }
        object.entrySet().forEach(field -> retainNestedLootSemantics(field.getValue()));
    }

    public record DimensionEvidence(String id, boolean levelAvailable, String generator, String support,
                                    List<String> biomes, String geology) {
        public DimensionEvidence { biomes = biomes.stream().sorted().distinct().toList(); }
    }

    private GenerationDataSnapshot(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Generation snapshot requires the server thread");
        this.server = server;
        epoch = new GenerationEpoch(server, server.getResourceManager(), server.getRecipeManager(), server.registryAccess(), server.reloadableRegistries());
        items = BuiltInRegistries.ITEM.stream().sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).toList();
        var excluded = com.mistaboom.essence_ascendance.balance.generated.GenerationRecipeReadiness.excludedRecipeNamespaces(server);
        var effectiveRecipes = server.getRecipeManager().getRecipes();
        recipeHolders = effectiveRecipes.stream().filter(holder -> !excluded.contains(holder.id().getNamespace()))
                .sorted(Comparator.comparing(holder -> holder.id().toString())).toList();
        if (!excluded.isEmpty()) limitations.add("Optional recipe integration unavailable: excluded namespaces " + excluded.stream().sorted().toList()
                + "; excluded " + (effectiveRecipes.size() - recipeHolders.size()) + " recipes; balance accuracy may be reduced");
        Map<String, String> installed = new TreeMap<>();
        Platform.getMods().forEach(mod -> installed.put(mod.getModId(), mod.getVersion()));
        mods = Collections.unmodifiableMap(installed);
        workloads.put("items", (long) items.size()); workloads.put("effective_recipes", (long) recipeHolders.size());
        structuresEnabled = server.getWorldData().worldGenOptions().generateStructures();
        captureDimensions();
    }

    public static GenerationDataSnapshot capture(MinecraftServer server) {
        BalancePerformance.increment("generation_snapshot_captures");
        try (var phase = BalancePerformance.phase("effective_generation_snapshot")) { return new GenerationDataSnapshot(server); }
    }

    public void requireCurrent(MinecraftServer expected) {
        if (server == null || server != expected || !expected.isSameThread())
            throw new IllegalStateException("Generation snapshot requires its owning server thread");
        epoch.require(expected, expected.getResourceManager(), expected.getRecipeManager(), expected.registryAccess(), expected.reloadableRegistries());
    }
    private com.mistaboom.essence_ascendance.balance.quest.QuestEvidence quests = com.mistaboom.essence_ascendance.balance.quest.QuestEvidence.EMPTY;
    private com.google.gson.JsonObject questProgression;
    private java.util.Set<String> configurationConstrainedItems = java.util.Set.of();
    public MinecraftServer server() { requireOpen(); return server; }
    public com.mistaboom.essence_ascendance.balance.quest.QuestEvidence quests() { requireOpen(); return quests; }
    public void quests(com.mistaboom.essence_ascendance.balance.quest.QuestEvidence evidence) { requireWritable(); quests = evidence; }
    public void questProgression(com.google.gson.JsonObject diagnostics) { requireWritable(); questProgression = diagnostics; }
    public void configurationConstrainedItems(java.util.Set<String> items) { requireWritable(); configurationConstrainedItems = java.util.Set.copyOf(items); }
    public java.util.Set<String> configurationConstrainedItems() { requireOpen(); return configurationConstrainedItems; }
    public List<Item> items() { requireOpen(); return items; }
    public List<RecipeHolder<?>> recipes() { requireOpen(); return recipeHolders; }
    public com.mistaboom.essence_ascendance.balance.economy.ProductionGraph production() {
        requireOpen();
        if (production == null) production = EffectiveProduction.collect(this, server.registryAccess());
        return production;
    }
    public void production(com.mistaboom.essence_ascendance.balance.economy.ProductionGraph resolved) {
        requireWritable(); production = java.util.Objects.requireNonNull(resolved);
        workloads.put("normalized_production_nodes", (long) production.processes().size());
        workloads.put("normalized_production_edges", production.processes().stream().mapToLong(p -> p.inputs().size() + p.outputs().size()).sum());
    }
    /** Installation alone supplies no readiness, access or power claim. */
    public String installedVersion(String modId) { requireOpen(); return mods.get(modId); }
    public List<DimensionEvidence> dimensions() { requireOpen(); return List.copyOf(dimensions); }
    public List<String> limitations() { requireOpen(); return limitations.stream().sorted().distinct().toList(); }
    public boolean structuresEnabled() { requireOpen(); return structuresEnabled; }
    boolean structureEligible(ResourceLocation id) { requireOpen(); return eligibleStructures.contains(id); }
    public boolean structureSetEligible(ResourceLocation id) { requireOpen(); return eligibleStructureSets.contains(id); }
    public Set<ResourceLocation> definitionIds(String family) { return json(family).keySet(); }
    public JsonObject definition(String family, ResourceLocation id) {
        JsonObject value = json(family).get(id); return value == null ? null : value.deepCopy();
    }
    public List<String> itemTag(ResourceLocation tag) {
        return itemTags().getOrDefault(tag.toString(), List.of());
    }
    public Map<String, List<String>> itemTags() {
        requireOpen();
        if (itemTags == null) {
            Map<String, List<String>> captured = new TreeMap<>();
            items.forEach(item -> item.builtInRegistryHolder().tags().forEach(tag -> captured.computeIfAbsent(tag.location().toString(),
                    ignored -> new ArrayList<>()).add(BuiltInRegistries.ITEM.getKey(item).toString())));
            captured.replaceAll((id, values) -> values.stream().sorted().distinct().toList());
            itemTags = Collections.unmodifiableMap(captured); workloads.put("effective_item_tags", (long) itemTags.size());
            BalancePerformance.increment("generation_item_tag_snapshots");
        }
        return itemTags;
    }
    void unsupportedRecipe(String family, String reason) { unsupportedRecipeFamilies.merge(family + ": " + reason, 1L, Long::sum); }
    Map<ResourceLocation, JsonObject> naturalBiomes() { requireOpen(); return naturalBiomes; }
    Map<ResourceLocation, JsonObject> terrain() { requireOpen(); return terrain; }

    /** Package consumers promise not to mutate these shared normalized values. */
    Map<ResourceLocation, JsonObject> json(String family) {
        requireOpen();
        Map<ResourceLocation, JsonObject> existing = definitions.get(family);
        if (existing != null) { BalancePerformance.increment("generation_definition_cache_hits/" + family); return existing; }
        Map<ResourceLocation, JsonObject> result;
        try (var phase = BalancePerformance.phase("normalize_definitions/" + family)) {
            result = switch (family) {
                case "worldgen/biome" -> encodeRegistry(server.registryAccess().registryOrThrow(Registries.BIOME), Biome.DIRECT_CODEC);
                case "worldgen/placed_feature" -> encodeRegistry(server.registryAccess().registryOrThrow(Registries.PLACED_FEATURE), PlacedFeature.DIRECT_CODEC);
                case "worldgen/configured_feature" -> encodeRegistry(server.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE), ConfiguredFeature.DIRECT_CODEC);
                case "worldgen/structure" -> encodeRegistry(server.registryAccess().registryOrThrow(Registries.STRUCTURE), Structure.DIRECT_CODEC);
                case "worldgen/structure_settings" -> structureSettings();
                case "worldgen/structure_set" -> encodeRegistry(server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET), StructureSet.DIRECT_CODEC);
                case "worldgen/template_pool" -> encodeRegistry(server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL), net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.DIRECT_CODEC);
                case "worldgen/processor_list" -> encodeRegistry(server.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST), processorDefinitionCodec());
                case "loot_table" -> encodeRegistry(server.reloadableRegistries().get().registryOrThrow(Registries.LOOT_TABLE), LootTable.DIRECT_CODEC);
                case "loot_evidence" -> {
                    var tables = encodeRegistry(server.reloadableRegistries().get().registryOrThrow(Registries.LOOT_TABLE), LootTable.DIRECT_CODEC, true);
                    try (var audit = BalancePerformance.phase("effective_runtime_loot_audit")) {
                        runtimeLootModifiers = RuntimeLootAudit.capture(this);
                        RuntimeLootAudit.mark(tables, runtimeLootModifiers, lootr.unsupported());
                        LootSemanticsAudit.auditTables(tables);
                    }
                    BalancePerformance.count("loot_tables_inspected", tables.size());
                    BalancePerformance.count("loot_unsupported_runtime_modifiers", runtimeLootModifiers.size() + lootr.unsupported().size());
                    yield tables;
                }
                default -> readResources(family);
            };
        }
        Map<ResourceLocation, JsonObject> frozen = Collections.unmodifiableMap(new TreeMap<>(result));
        definitions.put(family, frozen); workloads.put("normalized/" + family, (long) frozen.size());
        BalancePerformance.count("normalized/" + family, frozen.size());
        return frozen;
    }

    private <T> Map<ResourceLocation, JsonObject> encodeRegistry(Registry<T> registry, Codec<T> codec) {
        return encodeRegistry(registry, codec, false);
    }
    private <T> Map<ResourceLocation, JsonObject> encodeRegistry(Registry<T> registry, Codec<T> codec, boolean lootEvidence) {
        Map<ResourceLocation, JsonObject> result = new TreeMap<>();
        try (var scope = GenerationRegistrySerialization.open(server.registries().compositeAccess(), lootEvidence)) {
            try {
                registry.keySet().stream().sorted().forEach(id -> {
                    scope.definition(registry.key().location() + "/" + id);
                    var attempt = com.mistaboom.essence_ascendance.balance.engine.OptionalIntegration.attempt(
                            registry.key().location().toString(), id.toString(),
                            () -> encodeDefinition(codec, registry.get(id), scope.ops(), "effective registry " + registry.key().location() + "/" + id));
                    attempt.value().ifPresent(value -> result.put(id, value));
                    if (!attempt.succeeded()) limitations.add("Excluded effective definition " + registry.key().location() + "/" + id + ": " + attempt.failure());
                });
            } finally {
                scope.unsupportedLootFunctions().forEach((failure, count) -> unsupportedLootFunctions.merge(failure, count, Long::sum));
            }
        }
        return result;
    }

    /** Native processor-list encoding is an array; retain the named field expected by definition readers. */
    static Codec<net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList> processorDefinitionCodec() {
        return net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType.LIST_OBJECT_CODEC.fieldOf("processors").codec();
    }

    /** Normalizes one definition; the caller's optional boundary excludes failed custom codecs. */
    static <T> JsonObject encodeDefinition(Codec<T> codec, T value, RegistryOps<com.google.gson.JsonElement> ops, String provenance) {
        try {
            var encoded = codec.encodeStart(ops, value).getOrThrow(error -> new IllegalStateException(error));
            if (!encoded.isJsonObject()) throw new IllegalStateException("Expected object");
            return encoded.getAsJsonObject();
        } catch (RuntimeException | LinkageError failure) {
            throw new IllegalStateException("Cannot normalize " + provenance + ": " + failure, failure);
        }
    }

    private Map<ResourceLocation, JsonObject> structureSettings() {
        Map<ResourceLocation, JsonObject> result = new TreeMap<>();
        var registry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        registry.keySet().stream().sorted().forEach(id -> {
            var attempt = com.mistaboom.essence_ascendance.balance.engine.OptionalIntegration.attempt("structure_settings", id.toString(),
                    () -> GenerationStructureData.settings(id, registry.get(id), server.registries().compositeAccess()));
            attempt.value().ifPresent(value -> result.put(id, value));
            if (!attempt.succeeded()) limitations.add("Excluded structure settings " + id + ": " + attempt.failure());
        });
        return result;
    }

    private Map<ResourceLocation, JsonObject> readResources(String prefix) {
        Map<ResourceLocation, JsonObject> result = new TreeMap<>();
        server.getResourceManager().listResources(prefix, id -> id.getPath().endsWith(".json")).entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    String path = entry.getKey().getPath();
                    if (!path.startsWith(prefix + "/")) return;
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath(entry.getKey().getNamespace(), path.substring(prefix.length() + 1, path.length() - 5));
                    try (var reader = entry.getValue().openAsReader()) {
                        var value = JsonParser.parseReader(reader);
                        if (!value.isJsonObject()) throw new IllegalStateException("Expected object");
                        result.put(id, value.getAsJsonObject());
                    } catch (IOException | RuntimeException failure) {
                        throw new IllegalStateException("Cannot normalize winning resource " + entry.getKey() + " from " + entry.getValue().sourcePackId(), failure);
                    }
                });
        return result;
    }

    private void captureDimensions() {
        var stems = server.registries().getLayer(RegistryLayer.DIMENSIONS).registryOrThrow(Registries.LEVEL_STEM);
        Map<ResourceLocation, JsonObject> selectedBiomes = new TreeMap<>(), natural = new TreeMap<>(), selectedTerrain = new TreeMap<>();
        stems.keySet().stream().sorted().forEach(dimension -> {
            var attempt = com.mistaboom.essence_ascendance.balance.engine.OptionalIntegration.attempt("dimension", dimension.toString(), () -> {
            // The installed Overworld instance is authoritative; other configured stems are data, not forced levels.
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            ChunkGenerator generator = level == null ? stems.get(dimension).generator() : level.getChunkSource().getGenerator();
            var captured = GenerationDimensionData.capture(dimension, generator, level != null, server.registries().compositeAccess(), new TreeMap<>(selectedBiomes));
            var structureData = GenerationStructureData.capture(generator, server.registries().compositeAccess(), structuresEnabled);
            return new DimensionCapture(captured, structureData);
            });
            if (!attempt.succeeded()) {
                dimensions.add(new DimensionEvidence(dimension.toString(), server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension)) != null,
                        "unavailable", "FAILED", List.of(), "Unknown; failed dimension projection excluded"));
                limitations.add("Dimension " + dimension + " compatibility read excluded: " + attempt.failure());
                return;
            }
            var captured = attempt.value().orElseThrow().dimension();
            var structureData = attempt.value().orElseThrow().structures();
            captured.biomes().forEach(selectedBiomes::putIfAbsent);
            natural.putAll(captured.naturalBiomes()); selectedTerrain.putAll(captured.terrain());
            dimensions.add(captured.evidence()); limitations.addAll(captured.limitations());
            eligibleStructures.addAll(structureData.structures()); eligibleStructureSets.addAll(structureData.sets());
            structureData.structures().forEach(structure -> structureDimensions.computeIfAbsent(structure, ignored -> new ArrayList<>()).add(dimension.toString()));
        });
        definitions.put("worldgen/biome", Collections.unmodifiableMap(selectedBiomes));
        workloads.put("normalized/worldgen/biome", (long) selectedBiomes.size()); workloads.put("configured_dimensions", (long) dimensions.size());
        naturalBiomes = Collections.unmodifiableMap(natural); terrain = Collections.unmodifiableMap(selectedTerrain);
        workloads.put("eligible_structure_sets", (long) eligibleStructureSets.size()); workloads.put("eligible_structures", (long) eligibleStructures.size());
    }
    private record DimensionCapture(GenerationDimensionData dimension, GenerationStructureData structures) { }

    public void recordRoutingDiagnostics(List<ProceduralValuationResult> values) {
        requireWritable();
        if (routingDiagnostics != null) throw new IllegalStateException("Routing diagnostics already collected");
        routingDiagnostics = RoutingGenerationDiagnostics.collect(values);
    }

    public JsonObject diagnostics() {
        requireOpen(); JsonObject result = new JsonObject(), counts = new JsonObject();
        workloads.forEach(counts::addProperty); result.add("workloads", counts);
        if (routingDiagnostics != null) result.add("routing", routingDiagnostics.deepCopy());
        if (competitiveCapabilities != null) result.add("competitiveCapabilities", competitiveCapabilities);
        if (quests != com.mistaboom.essence_ascendance.balance.quest.QuestEvidence.EMPTY) {
            result.add("quests", quests.diagnostics());
            if (questProgression != null) result.getAsJsonObject("quests").add("progression", questProgression.deepCopy());
        }
        JsonArray levels = new JsonArray(); dimensions.stream().sorted(Comparator.comparing(DimensionEvidence::id)).forEach(dimension -> {
            JsonObject row = new JsonObject(); row.addProperty("id", dimension.id()); row.addProperty("levelAvailable", dimension.levelAvailable());
            row.addProperty("generator", dimension.generator()); row.addProperty("support", dimension.support()); row.addProperty("geology", dimension.geology());
            JsonArray biomes = new JsonArray(); dimension.biomes().forEach(biomes::add); row.add("biomes", biomes); levels.add(row);
        });
        JsonObject unsupported = new JsonObject(); unsupportedRecipeFamilies.forEach(unsupported::addProperty);
        result.add("unsupportedRecipeFamilies", unsupported);
        if (production != null) {
            JsonObject productionCounts = new JsonObject();
            Map<String, Long> families = new TreeMap<>();
            production.processes().forEach(p -> families.merge(p.family(), 1L, Long::sum));
            families.forEach(productionCounts::addProperty);
            result.add("productionFamilies", productionCounts);
            result.addProperty("productionModel", "effective-production-1: shared acquisition/economy graph; native resources/predicates/setup/operating evidence in process metadata; unknown is not zero");
            JsonObject versions = new JsonObject();
            for (String mod : List.of("kubejs", "create", "modern_industrialization", "mekanism", "occultism"))
                if (installedVersion(mod) != null) versions.addProperty(mod, installedVersion(mod));
            result.add("productionApiVersions", versions);
        }
        JsonObject unsupportedLoot = new JsonObject(); unsupportedLootFunctions.forEach(unsupportedLoot::addProperty);
        result.add("unsupportedLootFunctions", unsupportedLoot);
        JsonObject loot = new JsonObject();
        loot.add("lootr", new com.google.gson.Gson().toJsonTree(lootr));
        loot.add("availability", new com.google.gson.Gson().toJsonTree(lootAvailability));
        loot.add("tableSemantics", new com.google.gson.Gson().toJsonTree(lootSemantics));
        loot.add("runtimeModifiers", new com.google.gson.Gson().toJsonTree(runtimeLootModifiers));
        loot.addProperty("effectiveScope", "Effective reloadable registry includes supported reload-time table changes. Runtime callbacks are captured, never executed; affected paths remain unresolved. Unexposed event/mixin behavior is unsupported.");
        loot.addProperty("availabilityScope", "One underlying table/source opportunity; personal/shared alternatives do not multiply supply. Per-event chance/count are approximate, zero luck. Position, container eligibility, access and rates are never inferred from player/container history.");
        result.add("loot", loot);
        result.addProperty("lootEvidenceScope", "Unrepresentable function parameters remain explicit unresolved markers. Affected acquisition paths are diagnostic-only; full loot_table access still requires complete encoding.");
        result.add("dimensions", levels); result.addProperty("structuresEnabled", structuresEnabled);
        result.addProperty("provenance", "Effective server registries, recipe holders and configured dimension generators captured for this generation only");
        result.addProperty("structureSettingsScope", "Public effective biome, spawn override, decoration step and terrain adjustment settings; custom placement codecs are not required by generic spawn evidence");
        JsonArray unknown = new JsonArray(); limitations().forEach(unknown::add); result.add("limitations", unknown);
        result.addProperty("laterState", "Levels/services that require later events are not awaited. Player, inventory and chunk-dependent behavior is unsupported.");
        return result;
    }

    private void requireOpen() { if (server == null) throw new IllegalStateException("Generation snapshot released"); requireCurrent(server); }
    @Override public void close() {
        if (integrationReadOnly) throw new IllegalStateException("Optional integration cannot release shared generation inputs");
        server = null; epoch.clear();
        routingDiagnostics = null;
        competitiveCapabilities = null;
        quests = com.mistaboom.essence_ascendance.balance.quest.QuestEvidence.EMPTY; questProgression = null;
        configurationConstrainedItems = java.util.Set.of();
        production = null;
        items = List.of(); recipeHolders = List.of(); mods = Map.of(); itemTags = null; definitions.clear(); naturalBiomes = Map.of(); terrain = Map.of();
        dimensions.clear(); limitations.clear(); workloads.clear(); unsupportedRecipeFamilies.clear(); unsupportedLootFunctions.clear();
        eligibleStructures.clear(); eligibleStructureSets.clear();
        lootr = LootrPolicy.ABSENT; structureDimensions.clear(); lootAvailability.clear(); lootSemantics.clear(); runtimeLootModifiers = List.of(); lootEstimates.clear();
    }
}
