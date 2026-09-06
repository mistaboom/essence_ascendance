package com.mistaboom.essence_ascendance.mapping;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceFamily;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.network.ItemEssenceTooltipSyncService;
import com.mistaboom.essence_ascendance.valuation.GeneratedYieldEligibility;
import com.mistaboom.essence_ascendance.valuation.ShadowValuationEngine;
import com.mistaboom.essence_ascendance.valuation.ShadowValuationResult;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.platform.Platform;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Stages generated defaults + explicit global overrides, then publishes ONE
 * complete server-authoritative generation. Never reads the retired bundled
 * essence_mappings directory, and never mutates reservoir/player balances.
 */
public final class ItemEssenceMappingManager {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final String SETTINGS_FILE = "_settings.json";
    private static final String README_FILE = "README.txt";
    private static boolean initialized;
    private static MinecraftServer activeServer;
    private static Object observedResources, observedRecipes;
    private static volatile ConfigSettings activeSettings = ConfigSettings.defaults();

    private ItemEssenceMappingManager() { }
    public static synchronized void init() {
        if (initialized) return;
        ensureConfigScaffold();
        LifecycleEvent.SERVER_STARTED.register(server -> {
            activeServer = server;
            observedResources = server.getResourceManager();
            observedRecipes = server.getRecipeManager();
            reload();
        });
        LifecycleEvent.SERVER_STOPPED.register(server -> {
            if (activeServer == server) {
                activeServer = null;
                observedResources = null;
                observedRecipes = null;
                activeSettings = ConfigSettings.defaults();
                ItemEssenceMappingRegistry.clear();
                ShadowValuationEngine.clear();
            }
        });
        // A successful vanilla /reload swaps the server's resource/recipe holders.
        // Check identity in O(1), then re-resolve the saved baseline and explicit rules
        // at the next server tick, AFTER apply. Only an absent cache calculates.
        // Failed reloads retain the holders. Record new identity even on rejection
        // so a bad config does not trigger an expensive retry every tick.
        TickEvent.SERVER_POST.register(server -> {
            if (server != activeServer) return;
            if (observedResources != server.getResourceManager() || observedRecipes != server.getRecipeManager()) {
                observedResources = server.getResourceManager();
                observedRecipes = server.getRecipeManager();
                // Explicit diagnostics must not reuse analysis of the old data.
                ShadowValuationEngine.clear();
                reload();
            }
        });
        initialized = true;
    }

    public static Path configDirectory() {
        return Platform.getConfigFolder().resolve(EssenceAscendance.MOD_ID).resolve("item_mappings");
    }
    public static Path settingsPath() { return configDirectory().resolve(SETTINGS_FILE); }
    public static Path generatedCachePath() {
        // Outside item_mappings/: cached candidates must never become explicit rules.
        return Platform.getConfigFolder().resolve(EssenceAscendance.MOD_ID).resolve("generated_mappings.json");
    }
    public static boolean proceduralDefaultsEnabled() {
        return activeSettings.proceduralDefaults() && !activeSettings.removeAllDefaults();
    }

    public static ResourceLocation generatedId(ResourceLocation itemId) {
        return ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID,
                "generated/" + itemId.getNamespace() + "/" + itemId.getPath());
    }

    public static GeneratedYieldEligibility.Decision generatedDecision(ShadowValuationResult result) {
        Item item = BuiltInRegistries.ITEM.getOptional(result.itemId()).orElse(null);
        if (item == null) return new GeneratedYieldEligibility.Decision(GeneratedYieldEligibility.Status.EXCLUDED, "unregistered");
        ItemStack stack = new ItemStack(item);
        return GeneratedYieldEligibility.decide(stack, result,
                matchesAny(stack, activeSettings.allowGenerated()), matchesAny(stack, activeSettings.denyGenerated()));
    }

    public static synchronized ItemEssenceMappingRegistry.ReloadReport reload() {
        return reload(false);
    }

    /** Explicit operator request: bypass and replace the saved generated baseline. */
    public static synchronized ItemEssenceMappingRegistry.ReloadReport rebuild() {
        return reload(true);
    }

    private static ItemEssenceMappingRegistry.ReloadReport reload(boolean forceRegeneration) {
        ensureConfigScaffold();
        List<String> errors = new ArrayList<>(), warnings = new ArrayList<>();
        ConfigSettings settings = loadSettings(errors);
        Map<ResourceLocation, ItemEssenceMappingDefinition> explicit = loadConfigMappings(errors);
        int configFiles = countConfigMappingFiles();
        int generatedCount = 0, removed = 0;
        if (activeServer == null) errors.add("A running server is required to stage procedural defaults");
        List<String> removedSelectors = new ArrayList<>();
        for (ResourceLocation id : settings.removeDefaults()) {
            List<String> selectors = removalSelectors(id);
            if (selectors.isEmpty()) errors.add("remove_defaults references an unknown legacy/generated mapping or item: " + id);
            else removedSelectors.addAll(selectors);
        }
        // Legacy replacement-by-ID intent remains meaningful even when a custom
        // replacement intentionally changes selector. No old numeric values kept.
        for (ResourceLocation id : explicit.keySet()) removedSelectors.addAll(removalSelectors(id));
        Map<ResourceLocation, ItemEssenceMappingDefinition> generated = new LinkedHashMap<>();
        if (errors.isEmpty()) {
            try {
                if (!activeServer.isSameThread()) throw new IllegalStateException("Mapping reload must run on the server thread");
                GeneratedMappingCache.Loaded cache = null;
                List<GeneratedMappingCache.Entry> values;
                if (settings.proceduralDefaults() && !settings.removeAllDefaults()) {
                    cache = GeneratedMappingCache.loadOrGenerate(generatedCachePath(),
                            SharedConstants.getCurrentVersion().getName(), forceRegeneration, () -> {
                                EssenceAscendance.LOGGER.info("Calculating procedural mappings for {}; saved baseline will be replaced only after validation",
                                        generatedCachePath());
                                ShadowValuationEngine.rebuild(activeServer);
                                return ShadowValuationEngine.evaluateAll(activeServer).stream().map(value -> {
                                    Map<String, Long> outputs = new LinkedHashMap<>();
                                    value.routedEssence().forEach((essence, amount) -> outputs.put(essence.id().toString(), amount));
                                    return new GeneratedMappingCache.Entry(value.itemId().toString(), value.totalValue(),
                                            value.modeledAcquisition(), value.conservationStatus(), outputs);
                                }).toList();
                            });
                    values = cache.snapshot().entries();
                } else {
                    // The emergency off switch must work even with a bad cache.
                    // Do not read, generate, overwrite or delete the saved baseline.
                    ShadowValuationEngine.clear();
                    values = List.of();
                }
                Set<ResourceLocation> cachedIds = new LinkedHashSet<>();
                int missingItems = 0;
                for (GeneratedMappingCache.Entry value : values) {
                    ResourceLocation itemId = ResourceLocation.parse(value.itemId());
                    cachedIds.add(itemId);
                    Map<EssenceDefinition, Long> outputs = new LinkedHashMap<>();
                    for (var output : value.outputs().entrySet()) {
                        ResourceLocation essenceId = ResourceLocation.parse(output.getKey());
                        EssenceDefinition essence = EssenceRegistry.get(essenceId).orElseThrow(
                                () -> new IllegalArgumentException("Unknown cached Essence: " + essenceId));
                        if (essence.family() != EssenceFamily.ATTRIBUTE)
                            throw new IllegalArgumentException("Cached outputs must use Attribute Essences: " + essenceId);
                        outputs.put(essence, output.getValue());
                    }
                    Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
                    if (item == null || item == Items.AIR) { missingItems++; continue; }
                    ItemStack stack = new ItemStack(item);
                    GeneratedYieldEligibility.Decision decision = GeneratedYieldEligibility.decide(stack, itemId,
                            value.totalValue(), value.modeledAcquisition(), value.conservationStatus(),
                            matchesAny(stack, settings.allowGenerated()), matchesAny(stack, settings.denyGenerated()));
                    if (!decision.eligible()) continue;
                    generatedCount++;
                    if (matchesAny(stack, removedSelectors)) { removed++; continue; }
                    generated.put(itemId, new ItemEssenceMappingDefinition(generatedId(itemId), 0,
                            ItemEssenceMappingDefinition.SelectorType.ITEM, itemId, item, null, outputs));
                }
                if (cache != null) {
                    long newItems = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR
                            && !cachedIds.contains(BuiltInRegistries.ITEM.getKey(item))).count();
                    if (missingItems > 0 || newItems > 0) warnings.add("Saved procedural baseline: " + missingItems
                            + " cached items are no longer registered; " + newItems
                            + " registered items have no cached value. No automatic recalculation; use /essence debug valuation rebuild after pack changes.");
                }
                ItemEssenceMappingRegistry.LoadSummary summary = new ItemEssenceMappingRegistry.LoadSummary(
                        generatedCount, removed, 0, configFiles, explicit.size(), warnings);
                GeneratedMappingCache.Loaded stagedCache = cache;
                ItemEssenceMappingRegistry.install(List.copyOf(explicit.values()), generated, summary, () -> {
                    if (stagedCache != null && stagedCache.generated())
                        GeneratedMappingCache.writeAtomically(generatedCachePath(), stagedCache.snapshot());
                });
                if (cache != null) {
                    if (cache.generated()) EssenceAscendance.LOGGER.info(
                            "Saved procedural mapping cache to {}: {} valued items; installed mapping generation {}",
                            generatedCachePath(), values.size(), ItemEssenceMappingRegistry.generation());
                    else EssenceAscendance.LOGGER.info(
                            "Loaded saved procedural mapping cache from {}: {} valued items; valuation calculation skipped",
                            generatedCachePath(), values.size());
                }
                activeSettings = settings;
                if (!settings.proceduralDefaults() || settings.removeAllDefaults()) {
                    EssenceAscendance.LOGGER.info(
                            "Installed mapping generation {} with procedural defaults disabled; only explicit mapping rules are active",
                            ItemEssenceMappingRegistry.generation());
                }
            } catch (IOException | RuntimeException exception) {
                errors.add("Procedural cache/load/rebuild rejected: " + message(exception)
                        + ". The existing cache was not replaced. Repair it or use /essence debug valuation rebuild.");
            }
        }
        if (!errors.isEmpty()) {
            ItemEssenceMappingRegistry.rejectReload(new ItemEssenceMappingRegistry.LoadSummary(
                    generatedCount, removed, 0, configFiles, explicit.size(), warnings), errors);
        } else {
            try { ItemEssenceTooltipSyncService.syncAll(activeServer); }
            catch (RuntimeException exception) {
                // Installed generation remains valid. The existing per-player
                // generation retry resends if negotiation was not ready yet.
                EssenceAscendance.LOGGER.warn("Mapping generation installed; tooltip sync will retry: {}", message(exception));
            }
        }
        return ItemEssenceMappingRegistry.lastReload();
    }

    private static List<String> removalSelectors(ResourceLocation id) {
        List<String> legacy = LegacyDefaultSelectors.selectors(id);
        if (!legacy.isEmpty()) return legacy;
        if (id.getNamespace().equals(EssenceAscendance.MOD_ID) && id.getPath().startsWith("generated/")) {
            String tail = id.getPath().substring("generated/".length());
            int slash = tail.indexOf('/');
            if (slash > 0) {
                ResourceLocation itemId = ResourceLocation.tryParse(tail.substring(0, slash) + ":" + tail.substring(slash + 1));
                if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) return List.of(itemId.toString());
            }
        }
        if (BuiltInRegistries.ITEM.containsKey(id)) return List.of(id.toString());
        return List.of();
    }

    private static boolean matchesAny(ItemStack stack, Iterable<String> selectors) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (String selector : selectors) {
            if (selector.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(selector.substring(1));
                if (tag != null && stack.is(TagKey.create(Registries.ITEM, tag))) return true;
            } else if (id.toString().equals(selector)) return true;
        }
        return false;
    }

    private static ConfigSettings loadSettings(List<String> errors) {
        if (!Files.isRegularFile(settingsPath())) return ConfigSettings.defaults();
        try {
            JsonElement parsed = readJson(settingsPath());
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("root must be an object");
            JsonObject root = parsed.getAsJsonObject();
            Set<String> allowed = Set.of("remove_all_defaults", "remove_defaults", "procedural_defaults", "allow_generated", "deny_generated");
            for (String key : root.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("unknown field '" + key + "'");
            Set<ResourceLocation> removals = new LinkedHashSet<>();
            if (root.has("remove_defaults")) {
                if (!root.get("remove_defaults").isJsonArray()) throw new IllegalArgumentException("remove_defaults must be an array");
                for (JsonElement entry : root.getAsJsonArray("remove_defaults"))
                    removals.add(ItemEssenceMappingJson.readResourceLocation(entry, "remove_defaults[]"));
            }
            return new ConfigSettings(bool(root, "remove_all_defaults", false), Set.copyOf(removals),
                    bool(root, "procedural_defaults", true), selectors(root, "allow_generated"), selectors(root, "deny_generated"));
        } catch (IOException | RuntimeException exception) {
            errors.add(SETTINGS_FILE + ": " + message(exception));
            return ConfigSettings.defaults();
        }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException(key + " must be a boolean");
        return value.getAsBoolean();
    }
    private static Set<String> selectors(JsonObject root, String key) {
        if (!root.has(key)) return Set.of();
        if (!root.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array of item IDs or #tags");
        Set<String> result = new LinkedHashSet<>();
        for (JsonElement element : root.getAsJsonArray(key)) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException(key + " entries must be strings");
            String text = element.getAsString();
            ResourceLocation id = ResourceLocation.tryParse(text.startsWith("#") ? text.substring(1) : text);
            if (id == null || (!text.startsWith("#") && !BuiltInRegistries.ITEM.containsKey(id)))
                throw new IllegalArgumentException("Invalid/unknown selector in " + key + ": " + text);
            result.add((text.startsWith("#") ? "#" : "") + id);
        }
        return Set.copyOf(result);
    }

    private static Map<ResourceLocation, ItemEssenceMappingDefinition> loadConfigMappings(
            List<String> errors
    ) {
        Path root =
                configDirectory();

        Map<ResourceLocation, ItemEssenceMappingDefinition> result =
                new LinkedHashMap<>();

        for (Path file :
                jsonFiles(
                        root,
                        errors,
                        "config mappings"
                )) {

            if (file.getFileName()
                    .toString()
                    .equalsIgnoreCase(
                            SETTINGS_FILE
                    )) {
                continue;
            }

            String relative =
                    normalizeRelative(
                            root,
                            file
                    );

            ResourceLocation fallbackId =
                    idFromRelativePath(
                            EssenceAscendance.MOD_ID,
                            relative,
                            true
                    );

            if (fallbackId == null) {
                errors.add(
                        relative
                                + ": file path cannot become a default config mapping ID; "
                                + "rename the file/folders or add an explicit valid 'id' field"
                );

                continue;
            }

            try {
                ItemEssenceMappingDefinition definition =
                        ItemEssenceMappingJson.parseConfig(
                                fallbackId,
                                readJson(
                                        file
                                )
                        );

                ItemEssenceMappingDefinition previous =
                        result.putIfAbsent(
                                definition.id(),
                                definition
                        );

                if (previous != null) {
                    errors.add(
                            relative
                                    + ": duplicate config mapping ID "
                                    + definition.id()
                    );
                }

            } catch (RuntimeException
                     | IOException exception) {

                errors.add(
                        relative
                                + ": "
                                + message(
                                        exception
                                )
                );
            }
        }

        return result;
    }

    private static List<Path> jsonFiles(
            Path root,
            List<String> errors,
            String label
    ) {
        if (!Files.exists(
                root
        )) {
            return List.of();
        }

        try (Stream<Path> stream =
                     Files.walk(
                             root
                     )) {

            return stream
                    .filter(
                            Files::isRegularFile
                    )
                    .filter(
                            path ->
                                    path.getFileName()
                                            .toString()
                                            .toLowerCase(
                                                    java.util.Locale.ROOT
                                            )
                                            .endsWith(
                                                    ".json"
                                            )
                    )
                    .sorted(
                            Comparator.comparing(
                                    Path::toString
                            )
                    )
                    .toList();

        } catch (IOException exception) {
            errors.add(
                    "Unable to enumerate "
                            + label
                            + " at "
                            + root
                            + ": "
                            + message(
                                    exception
                            )
            );

            return List.of();
        }
    }

    private static int countConfigMappingFiles() {
        List<String> ignoredErrors =
                new ArrayList<>();

        int count =
                0;

        for (Path file :
                jsonFiles(
                        configDirectory(),
                        ignoredErrors,
                        "config mappings"
                )) {

            if (!file.getFileName()
                    .toString()
                    .equalsIgnoreCase(
                            SETTINGS_FILE
                    )) {
                count++;
            }
        }

        return count;
    }

    private static JsonElement readJson(
            Path path
    ) throws IOException {
        try (Reader reader =
                     Files.newBufferedReader(
                             path,
                             StandardCharsets.UTF_8
                     )) {

            return readJson(
                    reader
            );
        }
    }


    private static JsonElement readJson(
            Reader reader
    ) {
        JsonElement element =
                GSON.fromJson(
                        reader,
                        JsonElement.class
                );

        if (element == null) {
            throw new IllegalArgumentException(
                    "file is empty"
            );
        }

        return element;
    }

    private static ResourceLocation idFromRelativePath(
            String namespace,
            String relative,
            boolean config
    ) {
        String path =
                relative;

        if (path.toLowerCase(
                        java.util.Locale.ROOT
                )
                .endsWith(
                        ".json"
                )) {
            path =
                    path.substring(
                            0,
                            path.length()
                                    - ".json".length()
                    );
        }

        if (config) {
            path =
                    "config/"
                            + path;
        }

        return ResourceLocation.tryParse(
                namespace
                        + ":"
                        + path
        );
    }

    private static String normalizeRelative(
            Path root,
            Path file
    ) {
        return root.relativize(
                        file
                )
                .toString()
                .replace(
                        '\\',
                        '/'
                );
    }

    private static void ensureConfigScaffold() {
        try {
            Files.createDirectories(configDirectory());
            if (!Files.exists(settingsPath())) Files.writeString(settingsPath(), "{}\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            Path readme = configDirectory().resolve(README_FILE);
            if (!Files.exists(readme)) Files.writeString(readme, README_TEXT, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (IOException exception) {
            EssenceAscendance.LOGGER.error("Unable to scaffold item mapping config", exception);
        }
    }
    private static String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
    private record ConfigSettings(boolean removeAllDefaults, Set<ResourceLocation> removeDefaults,
                                  boolean proceduralDefaults, Set<String> allowGenerated, Set<String> denyGenerated) {
        static ConfigSettings defaults() { return new ConfigSettings(false, Set.of(), true, Set.of(), Set.of()); }
    }
    private static final String README_TEXT = """
            ESSENCE ASCENDANCE - PROCEDURAL DEFAULTS AND EXPLICIT OVERRIDES
            ============================================================
            The bundled hand-authored yield folder is retired and is not read.
            The first enabled server/world start generates a baseline from loaded data.
            It is saved beside this folder as ../generated_mappings.json.
            Later starts load that file without indexing recipes/loot/trades again.
            The cache includes internal/nonpayable candidates; eligibility still applies.
            All worlds sharing this config directory share the same saved baseline.
            Explicit JSON rules here are the final authority, even below priority 0.
            Highest-priority explicit rules win; tied explicit rules add together.
            An empty outputs object blocks lower-priority rules AND the generated default.

            _settings.json is sparse. Missing keys use these defaults:
              procedural_defaults: true
              remove_all_defaults: false
              remove_defaults: []
              allow_generated: []
              deny_generated: []
            allow_generated/deny_generated accept item IDs and #item_tags.
            Deny wins over allow. Explicit mappings still override the policy.
            Allow changes eligibility ONLY: it does not invent acquisition evidence.
            To price an unsupported custom acquisition mechanic, write an explicit mapping.
            remove_defaults accepts generated IDs, item IDs and historical bundled mapping IDs.
            Stable generated ID: essence_ascendance:generated/<namespace>/<item_path>
            Historical aliases contain selectors only, never old yield numbers.
            procedural_defaults:false or remove_all_defaults:true leaves explicit rules only;
            it does NOT restore the deleted legacy dataset.

            Example explicit rule (put in its own JSON file):
            {"item":"minecraft:pig_spawn_egg","outputs":{"essence_ascendance:vitality":100}}
            This number is an EXAMPLE override, not a built-in default.
            Example block: {"item":"minecraft:diamond","outputs":{}}
            Optional mapping fields: id, priority. Use exactly one of item or tag.

            Own-mod items remain internally valued but are not automatic yield sources.
            Spawn eggs need a modeled source in this pack, or an explicit mapping.
            Unmodeled does not mean creative-only; unsupported items stay in CSV diagnostics.
            Essentium uses its existing exact component recovery, never a generated base bonus.

            /essence admin mappings reload reads the saved baseline plus these overrides.
            Successful vanilla /reload re-resolves the saved baseline/settings/tags as well.
            Neither recalculates an existing cache. An absent cache generates once.
            /essence debug valuation rebuild forces recalculation and atomically replaces
            the cache only after the entire merged mapping generation validates.
            Run rebuild after mod, recipe, loot, trade, world-data or valuation-code changes;
            freshness is MANUAL, even when the item IDs did not change.
            Unknown/removed items are skipped; new items need rebuild or explicit rules.
            Invalid existing caches reject loading rather than silently recalculating.
            Disabled defaults leave the cache untouched, including during rebuild.
            Diagnostic /valuation and CSV export may compute current analysis on demand,
            but neither saves nor installs it; analysis may differ from the cached baseline.
            Errors keep the complete last-known-good generation; first-start errors fail closed.
            CSV includes eligibility and the installed live mapping source/total.
            """;
}
