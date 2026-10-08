package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Best-effort structure/container occurrence metadata built from the final
 * loaded datapack resources.
 *
 * <p>Structure-set placement gives a useful rarity proxy (spacing, weight and
 * placement frequency). Structure-template NBT is scanned for LootTable strings
 * so container tables can also see how many template chest references exist.
 * The NBT scan intentionally uses only standard Java gzip/string handling; no
 * loader-specific or unstable structure-template APIs are required.</p>
 */
final class ProceduralStructureIndex {

    private static final Set<String> STOP_TOKENS = Set.of(
            "chest", "chests", "container", "containers", "archaeology", "treasure", "loot",
            "common", "rare", "reward", "rewards", "ominous", "supply",
            "intersection", "corridor", "crossing", "room", "house", "map",
            "stable", "other", "bridge"
    );

    private final Map<ResourceLocation, StructureOccurrence> structures;
    private final Map<ResourceLocation, Integer> templateLootReferences;
    private final Map<ResourceLocation, List<String>> templateExamples;
    private final Set<ResourceLocation> unlockedContainerTables;
    private final Map<ResourceLocation, StructureSpawnOccurrence> structureSpawns;
    private final int structureSetCount;
    private final int structureTemplateCount;
    private final int structureDefinitionCount;

    private ProceduralStructureIndex(
            Map<ResourceLocation, StructureOccurrence> structures,
            Map<ResourceLocation, Integer> templateLootReferences,
            Map<ResourceLocation, List<String>> templateExamples,
            Set<ResourceLocation> unlockedContainerTables,
            Map<ResourceLocation, StructureSpawnOccurrence> structureSpawns,
            int structureSetCount,
            int structureTemplateCount,
            int structureDefinitionCount
    ) {
        this.structures = Map.copyOf(structures);
        this.templateLootReferences = Map.copyOf(templateLootReferences);
        this.templateExamples = Map.copyOf(templateExamples);
        this.unlockedContainerTables = Set.copyOf(unlockedContainerTables);
        this.structureSpawns = Map.copyOf(structureSpawns);
        this.structureSetCount = structureSetCount;
        this.structureTemplateCount = structureTemplateCount;
        this.structureDefinitionCount = structureDefinitionCount;
    }

    static ProceduralStructureIndex build(MinecraftServer server, GenerationDataSnapshot data) {
        Map<ResourceLocation, StructureOccurrence> structures = new LinkedHashMap<>();
        int structureSets = data.structuresEnabled() ? scanStructureSets(data, structures) : 0;

        Map<ResourceLocation, StructureSpawnOccurrence> structureSpawns = new LinkedHashMap<>();
        int structureDefinitions = data.structuresEnabled() ? scanStructureSpawnOverrides(data, structures, structureSpawns) : 0;

        Map<ResourceLocation, Integer> templateReferences = new LinkedHashMap<>();
        Map<ResourceLocation, List<String>> templateExamples = new LinkedHashMap<>();
        Set<ResourceLocation> unlockedContainerTables = new HashSet<>();
        int templates = data.structuresEnabled() ? scanStructureTemplates(server, templateReferences, templateExamples, unlockedContainerTables) : 0;

        EssenceAscendance.LOGGER.info(
                "Procedural structure index built: {} structure sets, {} placed structures, {} structure definitions, {} structure-spawn entity types, {} structure templates, {} referenced container loot tables",
                structureSets,
                structures.size(),
                structureDefinitions,
                structureSpawns.size(),
                templates,
                templateReferences.size()
        );

        return new ProceduralStructureIndex(
                structures,
                templateReferences,
                templateExamples,
                unlockedContainerTables,
                structureSpawns,
                structureSets,
                templates,
                structureDefinitions
        );
    }

    ContainerOccurrence forLootTable(ResourceLocation lootTableId) {
        int templateRefs = templateLootReferences.getOrDefault(lootTableId, 0);
        double densityMultiplier = templateRefs <= 1
                ? 1.0
                : Math.max(
                        ProceduralValuationSettings.CONTAINER_TEMPLATE_DENSITY_MIN_MULTIPLIER,
                        1.0 / Math.pow(templateRefs, 0.18)
                );

        StructureOccurrence matched = bestStructureMatch(lootTableId);
        List<String> signals = new ArrayList<>();
        if (matched != null) {
            signals.add("matched structure " + matched.structureId());
            signals.addAll(matched.signals());
        }
        if (templateRefs > 0) {
            signals.add("structure-template loot references: " + templateRefs);
            List<String> examples = templateExamples.getOrDefault(lootTableId, List.of());
            for (String example : examples) {
                signals.add("template: " + example);
            }
        }

        return new ContainerOccurrence(
                matched == null ? null : matched.structureId(),
                matched == null ? 1.0 : matched.frequencyMultiplier(),
                densityMultiplier,
                matched != null && matched.frequencyKnown(),
                templateRefs,
                List.copyOf(signals)
        );
    }
    boolean unlockedNativeContainer(ResourceLocation table) { return unlockedContainerTables.contains(table); }

    int structureSetCount() {
        return structureSetCount;
    }

    int structureTemplateCount() {
        return structureTemplateCount;
    }

    int structureDefinitionCount() {
        return structureDefinitionCount;
    }

    StructureSpawnOccurrence forEntitySpawn(ResourceLocation entityId) {
        return structureSpawns.get(entityId);
    }

    private StructureOccurrence bestStructureMatch(ResourceLocation lootTableId) {
        return bestStructureMatch(lootTableId, structures.values());
    }
    static StructureOccurrence bestStructureMatch(ResourceLocation lootTableId, java.util.Collection<StructureOccurrence> structures) {
        Set<String> tableTokens = normalizedTokens(lootTableId.getPath());
        if (tableTokens.isEmpty()) {
            return null;
        }

        StructureOccurrence best = null;
        double bestScore = 0.0;
        for (StructureOccurrence candidate : structures.stream().sorted(java.util.Comparator.comparing(StructureOccurrence::structureId)).toList()) {
            if (!candidate.structureId().getNamespace().equals(lootTableId.getNamespace())
                    && !lootTableId.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
                continue;
            }

            Set<String> structureTokens = normalizedTokens(candidate.structureId().getPath());
            if (structureTokens.isEmpty()) {
                continue;
            }

            int overlap = 0;
            for (String token : tableTokens) {
                if (structureTokens.contains(token)) {
                    overlap++;
                }
            }
            if (overlap == 0) {
                continue;
            }

            double score = overlap * 2.0
                    / Math.max(1.0, Math.min(tableTokens.size(), structureTokens.size()));
            if (candidate.structureId().getNamespace().equals(lootTableId.getNamespace())) {
                score += 0.20;
            }

            if (score > bestScore + 0.000001
                    || (Math.abs(score - bestScore) < 0.000001
                    && best != null
                    && candidate.frequencyMultiplier() < best.frequencyMultiplier())) {
                bestScore = score;
                best = candidate;
            }
        }

        return bestScore >= 0.95 ? best : null;
    }

    private static int scanStructureSets(
            GenerationDataSnapshot data,
            Map<ResourceLocation, StructureOccurrence> output
    ) {
        Map<ResourceLocation, JsonObject> resources = data.json("worldgen/structure_set");
        int scanned = 0;

        for (Map.Entry<ResourceLocation, JsonObject> entry : resources.entrySet()) {
            if (!data.structureSetEligible(entry.getKey())) continue;
            try {
                JsonElement parsed = entry.getValue();
                if (parsed == null || !parsed.isJsonObject()) {
                    continue;
                }
                JsonObject root = parsed.getAsJsonObject();
                JsonElement structuresElement = root.get("structures");
                if (structuresElement == null || !structuresElement.isJsonArray()) {
                    continue;
                }

                JsonObject placement = root.has("placement") && root.get("placement").isJsonObject()
                        ? root.getAsJsonObject("placement")
                        : null;

                List<WeightedStructure> weighted = new ArrayList<>();
                double totalWeight = 0.0;
                for (JsonElement structureElement : structuresElement.getAsJsonArray()) {
                    ResourceLocation structureId = null;
                    double weight = 1.0;
                    if (structureElement.isJsonPrimitive()
                            && structureElement.getAsJsonPrimitive().isString()) {
                        structureId = ResourceLocation.tryParse(structureElement.getAsString());
                    } else if (structureElement.isJsonObject()) {
                        JsonObject object = structureElement.getAsJsonObject();
                        String raw = readString(object, "structure");
                        structureId = raw == null ? null : ResourceLocation.tryParse(raw);
                        weight = Math.max(0.000001, readDouble(object, "weight", 1.0));
                    }
                    if (structureId != null) {
                        weighted.add(new WeightedStructure(structureId, weight));
                        totalWeight += weight;
                    }
                }

                if (weighted.isEmpty()) {
                    continue;
                }

                for (WeightedStructure structure : weighted) {
                    if (!data.structureEligible(structure.structureId())) continue;
                    double share = structure.weight() / Math.max(0.000001, totalWeight);
                    PlacementEstimate estimate = estimatePlacement(placement, share);
                    StructureOccurrence occurrence = new StructureOccurrence(
                            structure.structureId(),
                            estimate.multiplier(),
                            estimate.known(),
                            estimate.signals()
                    );
                    output.merge(
                            structure.structureId(),
                            occurrence,
                            ProceduralStructureIndex::easierOccurrence
                    );
                }
                scanned++;
            } catch (RuntimeException exception) {
                EssenceAscendance.LOGGER.debug(
                        "Procedural valuation skipped structure set {}: {}",
                        entry.getKey(),
                        exception.getMessage()
                );
            }
        }
        return scanned;
    }

    private static PlacementEstimate estimatePlacement(JsonObject placement, double structureShare) {
        if (placement == null) {
            return PlacementEstimate.UNKNOWN;
        }
        String type = readString(placement, "type");
        if (type == null) {
            return PlacementEstimate.UNKNOWN;
        }

        List<String> signals = new ArrayList<>();
        if (type.endsWith(":random_spread")) {
            double spacing = Math.max(1.0, readDouble(placement, "spacing", 32.0));
            double separation = Math.max(0.0, readDouble(placement, "separation", 0.0));
            double frequency = Math.max(0.000001, readDouble(placement, "frequency", 1.0));
            double effectiveArea = spacing * spacing
                    / Math.max(0.000001, structureShare * frequency);
            double referenceArea = ProceduralValuationSettings.STRUCTURE_REFERENCE_SPACING
                    * ProceduralValuationSettings.STRUCTURE_REFERENCE_SPACING;
            double multiplier = Math.sqrt(effectiveArea / referenceArea);
            multiplier = clamp(
                    multiplier,
                    ProceduralValuationSettings.STRUCTURE_FREQUENCY_MIN_MULTIPLIER,
                    ProceduralValuationSettings.STRUCTURE_FREQUENCY_MAX_MULTIPLIER
            );
            String reduction = readString(placement, "frequency_reduction_method");
            signals.add("random-spread spacing " + format(spacing)
                    + ", separation " + format(separation)
                    + ", placement frequency " + formatPercent(frequency)
                    + ", selection " + formatPercent(structureShare)
                    + (reduction == null ? "" : ", reduction " + reduction));
            return new PlacementEstimate(multiplier, true, List.copyOf(signals));
        }

        if (type.endsWith(":concentric_rings")) {
            double distance = Math.max(1.0, readDouble(placement, "distance", 32.0));
            double count = Math.max(1.0, readDouble(placement, "count", 128.0));
            double multiplier = (distance / 24.0)
                    * Math.sqrt(128.0 / count)
                    / Math.sqrt(Math.max(0.000001, structureShare));
            multiplier = clamp(
                    multiplier,
                    ProceduralValuationSettings.STRUCTURE_FREQUENCY_MIN_MULTIPLIER,
                    ProceduralValuationSettings.STRUCTURE_FREQUENCY_MAX_MULTIPLIER
            );
            signals.add("concentric-rings distance " + format(distance)
                    + ", count " + format(count)
                    + ", selection " + formatPercent(structureShare));
            return new PlacementEstimate(multiplier, true, List.copyOf(signals));
        }

        signals.add("unmodeled structure placement " + type);
        return new PlacementEstimate(1.0, false, List.copyOf(signals));
    }


    private static int scanStructureSpawnOverrides(
            GenerationDataSnapshot data,
            Map<ResourceLocation, StructureOccurrence> structures,
            Map<ResourceLocation, StructureSpawnOccurrence> output
    ) {
        Map<ResourceLocation, JsonObject> resources = data.json("worldgen/structure_settings");
        int scanned = 0;

        for (Map.Entry<ResourceLocation, JsonObject> entry : resources.entrySet()) {
            try {
                JsonElement parsed = entry.getValue();
                if (parsed == null || !parsed.isJsonObject()) {
                    continue;
                }

                ResourceLocation structureId = entry.getKey();
                if (!data.structureEligible(structureId)) {
                    continue;
                }

                JsonObject root = parsed.getAsJsonObject();
                JsonElement overridesElement = root.get("spawn_overrides");
                if (overridesElement == null || !overridesElement.isJsonObject()) {
                    scanned++;
                    continue;
                }

                StructureOccurrence occurrence = structures.get(structureId);
                double structureMultiplier = occurrence == null
                        ? ProceduralValuationSettings.UNKNOWN_STRUCTURE_FREQUENCY_MULTIPLIER
                        : occurrence.frequencyMultiplier();
                boolean frequencyKnown = occurrence != null && occurrence.frequencyKnown();

                for (Map.Entry<String, JsonElement> categoryEntry
                        : overridesElement.getAsJsonObject().entrySet()) {
                    if (!categoryEntry.getValue().isJsonObject()) {
                        continue;
                    }
                    JsonObject override = categoryEntry.getValue().getAsJsonObject();
                    JsonElement spawnsElement = override.get("spawns");
                    if (spawnsElement == null || !spawnsElement.isJsonArray()) {
                        continue;
                    }

                    double totalWeight = 0.0;
                    List<StructureSpawnEntry> entries = new ArrayList<>();
                    for (JsonElement spawnElement : spawnsElement.getAsJsonArray()) {
                        if (!spawnElement.isJsonObject()) {
                            continue;
                        }
                        JsonObject spawn = spawnElement.getAsJsonObject();
                        String rawType = readString(spawn, "type");
                        ResourceLocation entityId = rawType == null
                                ? null
                                : ResourceLocation.tryParse(rawType);
                        if (entityId == null) {
                            continue;
                        }

                        double weight = Math.max(0.000001, readDouble(spawn, "weight", 1.0));
                        double minCount = Math.max(
                                1.0,
                                readDouble(spawn, "minCount", readDouble(spawn, "min_count", 1.0))
                        );
                        double maxCount = Math.max(
                                minCount,
                                readDouble(spawn, "maxCount", readDouble(spawn, "max_count", minCount))
                        );
                        entries.add(new StructureSpawnEntry(
                                entityId,
                                weight,
                                (minCount + maxCount) / 2.0
                        ));
                        totalWeight += weight;
                    }

                    for (StructureSpawnEntry spawn : entries) {
                        double share = spawn.weight() / Math.max(0.000001, totalWeight);
                        double spawnChoiceMultiplier = Math.pow(
                                1.0 / Math.max(0.000001, share),
                                0.12
                        ) / Math.pow(Math.max(1.0, spawn.averagePack()), 0.06);
                        spawnChoiceMultiplier = clamp(
                                spawnChoiceMultiplier,
                                0.90,
                                2.25
                        );

                        List<String> signals = new ArrayList<>();
                        signals.add("structure spawn override " + structureId);
                        signals.add("category " + categoryEntry.getKey());
                        signals.add("spawn selection " + formatPercent(share));
                        signals.add("average pack " + format(spawn.averagePack()));
                        if (occurrence != null) {
                            signals.addAll(occurrence.signals());
                        } else {
                            signals.add("structure placement frequency unresolved");
                        }

                        StructureSpawnOccurrence candidate = new StructureSpawnOccurrence(
                                structureId,
                                structureMultiplier * spawnChoiceMultiplier,
                                frequencyKnown,
                                share,
                                spawn.averagePack(),
                                List.copyOf(signals)
                        );
                        output.merge(
                                spawn.entityId(),
                                candidate,
                                ProceduralStructureIndex::easierStructureSpawn
                        );
                    }
                }
                scanned++;
            } catch (RuntimeException exception) {
                EssenceAscendance.LOGGER.debug(
                        "Procedural valuation skipped structure definition {}: {}",
                        entry.getKey(),
                        exception.getMessage()
                );
            }
        }

        return scanned;
    }

    private static int scanStructureTemplates(
            MinecraftServer server,
            Map<ResourceLocation, Integer> lootReferences,
            Map<ResourceLocation, List<String>> examples,
            Set<ResourceLocation> unlockedContainerTables
    ) {
        Map<ResourceLocation, Resource> resources = new LinkedHashMap<>();
        resources.putAll(listBinaryResources(server, "structure"));
        resources.putAll(listBinaryResources(server, "structures"));

        int scanned = 0;
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            try (InputStream input = entry.getValue().open()) {
                byte[] bytes = input.readAllBytes();
                byte[] uncompressed = maybeGunzip(bytes);
                // Binary string matches retain their historical diagnostic scope. Actual access
                // needs a typed, unlocked vanilla container binding, not a table's path/name.
                try {
                    var root = net.minecraft.nbt.NbtIo.read(new java.io.DataInputStream(new ByteArrayInputStream(uncompressed)),
                            net.minecraft.nbt.NbtAccounter.create(8L * 1024 * 1024));
                    unlockedContainerTables.addAll(unlockedNativeLoot(root));
                } catch (IOException | RuntimeException unsupported) {
                    // Failed typed access capture excludes this access witness only.
                    com.mistaboom.essence_ascendance.balance.generated.BalancePerformance.increment("unlocked_container_template_read_failures");
                    EssenceAscendance.LOGGER.debug("Typed container access excluded for template {}: {}", entry.getKey(), unsupported.toString());
                }
                String content = new String(uncompressed, StandardCharsets.ISO_8859_1);
                LootTableScanner matcher = lootTableScanner(content);
                while (matcher.find()) {
                    ResourceLocation lootTable = ResourceLocation.tryBuild(
                            matcher.group(1),
                            matcher.group(2)
                    );
                    if (lootTable == null) {
                        continue;
                    }
                    lootReferences.merge(lootTable, 1, Integer::sum);
                    examples.computeIfAbsent(lootTable, ignored -> new ArrayList<>());
                    List<String> list = examples.get(lootTable);
                    if (list.size() < 3 && !list.contains(entry.getKey().toString())) {
                        list.add(entry.getKey().toString());
                    }
                }
                scanned++;
            } catch (IOException | RuntimeException exception) {
                EssenceAscendance.LOGGER.debug(
                        "Procedural valuation skipped structure template {}: {}",
                        entry.getKey(),
                        exception.getMessage()
                );
            }
        }
        return scanned;
    }

    static Set<ResourceLocation> unlockedNativeLoot(net.minecraft.nbt.CompoundTag root) {
        var result = new HashSet<ResourceLocation>(); if (root == null) return result;
        var palettes = new ArrayList<net.minecraft.nbt.ListTag>();
        if (root.contains("palette", 9)) palettes.add(root.getList("palette", 10));
        else for (var raw : root.getList("palettes", 9)) if (raw instanceof net.minecraft.nbt.ListTag palette) palettes.add(palette);
        if (palettes.isEmpty()) return result;
        for (var raw : root.getList("blocks", 10)) {
            var block = (net.minecraft.nbt.CompoundTag)raw; var nbt = block.getCompound("nbt");
            if (!block.contains("state", 3) || !nbt.contains("LootTable", 8)
                    || nbt.contains("LootTableSeed") && (!nbt.contains("LootTableSeed", 99) || nbt.getLong("LootTableSeed") != 0)
                    || nbt.contains("Lock") && (!nbt.contains("Lock", 8) || !nbt.getString("Lock").isEmpty())) continue;
            int state = block.getInt("state"); boolean nativeContainer = true;
            for (var palette : palettes) {
                String name = state >= 0 && state < palette.size() ? palette.getCompound(state).getString("Name") : "";
                if (!Set.of("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel").contains(name)) nativeContainer = false;
            }
            if (nativeContainer) {
                var id = ResourceLocation.tryParse(nbt.getString("LootTable")); if (id != null) result.add(id);
            }
        }
        return Set.copyOf(result);
    }

    static LootTableScanner lootTableScanner(CharSequence content) {
        return new LootTableScanner(content);
    }

    /** The historical loot-table pattern, scanned from its required colon instead of every NBT byte. */
    static final class LootTableScanner {
        private final CharSequence content;
        private int searchFrom;
        private int previousMatchEnd;
        private int start = -1;
        private int colon;
        private int end;

        private LootTableScanner(CharSequence content) {
            this.content = java.util.Objects.requireNonNull(content);
        }

        boolean find() {
            start = -1;
            int candidate;
            while ((candidate = nextColon(searchFrom)) >= 0) {
                searchFrom = candidate + 1;
                int familyEnd = familyEnd(candidate + 1);
                if (familyEnd < 0 || familyEnd == content.length() || !pathCharacter(content.charAt(familyEnd))) continue;

                // Do not scan left until a valid family and at least one path character exist.
                // The previous end preserves Matcher.find()'s nonoverlapping matches, even
                // when another colon follows immediately after a matched path.
                int namespaceStart = candidate;
                while (namespaceStart > previousMatchEnd && namespaceCharacter(content.charAt(namespaceStart - 1))) namespaceStart--;
                if (namespaceStart == candidate) continue;

                int pathEnd = familyEnd + 1;
                while (pathEnd < content.length() && pathCharacter(content.charAt(pathEnd))) pathEnd++;
                start = namespaceStart;
                colon = candidate;
                end = pathEnd;
                previousMatchEnd = end;
                searchFrom = end;
                return true;
            }
            searchFrom = content.length();
            return false;
        }

        int start() { requireMatch(); return start; }
        int end() { requireMatch(); return end; }
        String group() { return group(0); }
        String group(int group) {
            requireMatch();
            return switch (group) {
                case 0 -> content.subSequence(start, end).toString();
                case 1 -> content.subSequence(start, colon).toString();
                case 2 -> content.subSequence(colon + 1, end).toString();
                default -> throw new IndexOutOfBoundsException("No group " + group);
            };
        }

        private void requireMatch() {
            if (start < 0) throw new IllegalStateException("No match available");
        }

        private int nextColon(int from) {
            if (content instanceof String text) return text.indexOf(':', from);
            for (int index = from; index < content.length(); index++) if (content.charAt(index) == ':') return index;
            return -1;
        }

        private int familyEnd(int from) {
            if (startsWith("chests/", from)) return from + 7;
            if (startsWith("containers/", from)) return from + 11;
            if (startsWith("archaeology/", from)) return from + 12;
            return -1;
        }

        private boolean startsWith(String prefix, int from) {
            if (content instanceof String text) return text.startsWith(prefix, from);
            if (from > content.length() - prefix.length()) return false;
            for (int index = 0; index < prefix.length(); index++) if (content.charAt(from + index) != prefix.charAt(index)) return false;
            return true;
        }

        private static boolean namespaceCharacter(char character) {
            return character >= 'a' && character <= 'z' || character >= '0' && character <= '9'
                    || character == '_' || character == '.' || character == '-';
        }

        private static boolean pathCharacter(char character) {
            return namespaceCharacter(character) || character == '/';
        }
    }

    private static byte[] maybeGunzip(byte[] bytes) throws IOException {
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B) {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                return gzip.readAllBytes();
            }
        }
        return bytes;
    }

    private static Map<ResourceLocation, Resource> listJsonResources(MinecraftServer server, String path) {
        try {
            return server.getResourceManager().listResources(
                    path,
                    id -> id.getPath().endsWith(".json")
            );
        } catch (RuntimeException exception) {
            EssenceAscendance.LOGGER.debug(
                    "Procedural valuation could not enumerate {} JSON resources: {}",
                    path,
                    exception.getMessage()
            );
            return Map.of();
        }
    }

    private static Map<ResourceLocation, Resource> listBinaryResources(MinecraftServer server, String path) {
        try {
            return server.getResourceManager().listResources(
                    path,
                    id -> id.getPath().endsWith(".nbt")
            );
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }

    private static StructureOccurrence easierOccurrence(
            StructureOccurrence first,
            StructureOccurrence second
    ) {
        if (!first.frequencyKnown()) {
            return second;
        }
        if (!second.frequencyKnown()) {
            return first;
        }
        return first.frequencyMultiplier() <= second.frequencyMultiplier() ? first : second;
    }

    private static Set<String> normalizedTokens(String rawPath) {
        String path = rawPath.toLowerCase(Locale.ROOT)
                .replace("abandoned_mineshaft", "mineshaft")
                .replace("woodland_mansion", "mansion")
                .replace("jungle_temple", "jungle_pyramid")
                .replace("nether_bridge", "fortress")
                .replace("end_city_treasure", "end_city")
                .replace("bastion_treasure", "bastion_remnant")
                .replace("bastion_hoglin_stable", "bastion_remnant")
                .replace("bastion_other", "bastion_remnant")
                .replace("bastion_bridge", "bastion_remnant")
                .replace("trial_chambers", "trial_chambers")
                .replace("trial_chamber", "trial_chambers")
                .replace("ruined_portal", "ruined_portal")
                .replace("pillager_outpost", "pillager_outpost");

        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String token : path.split("[/_.-]+")) {
            if (token.isBlank() || token.length() < 3 || STOP_TOKENS.contains(token)) {
                continue;
            }
            result.add(token);
        }
        return result;
    }


    private static ResourceLocation resourceDataId(
            ResourceLocation resourceId,
            String prefix
    ) {
        String path = resourceId.getPath();
        if (!path.startsWith(prefix) || !path.endsWith(".json")) {
            return null;
        }
        String dataPath = path.substring(prefix.length(), path.length() - ".json".length());
        return ResourceLocation.tryBuild(resourceId.getNamespace(), dataPath);
    }

    private static StructureSpawnOccurrence easierStructureSpawn(
            StructureSpawnOccurrence first,
            StructureSpawnOccurrence second
    ) {
        return first.multiplier() <= second.multiplier() ? first : second;
    }

    private static String readString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }

    private static double readDouble(JsonObject object, String key, double fallback) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return value.getAsDouble();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100.0);
    }

    record ContainerOccurrence(
            ResourceLocation structureId,
            double structureFrequencyMultiplier,
            double templateDensityMultiplier,
            boolean structureFrequencyKnown,
            int templateReferenceCount,
            List<String> signals
    ) {
        ContainerOccurrence {
            signals = List.copyOf(signals);
        }

        double combinedMultiplier() {
            return structureFrequencyMultiplier * templateDensityMultiplier;
        }
    }

    record StructureOccurrence(
            ResourceLocation structureId,
            double frequencyMultiplier,
            boolean frequencyKnown,
            List<String> signals
    ) {
        StructureOccurrence {
            signals = List.copyOf(signals);
        }
    }


    record StructureSpawnOccurrence(
            ResourceLocation structureId,
            double multiplier,
            boolean frequencyKnown,
            double selectionShare,
            double averagePack,
            List<String> signals
    ) {
        StructureSpawnOccurrence {
            signals = List.copyOf(signals);
        }
    }

    private record StructureSpawnEntry(
            ResourceLocation entityId,
            double weight,
            double averagePack
    ) {
    }

    private record WeightedStructure(ResourceLocation structureId, double weight) {
    }

    private record PlacementEstimate(double multiplier, boolean known, List<String> signals) {
        static final PlacementEstimate UNKNOWN = new PlacementEstimate(1.0, false, List.of());

        PlacementEstimate {
            signals = List.copyOf(signals);
        }
    }
}
