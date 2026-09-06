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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Data-driven natural-spawn rarity signal. Entities absent from every loaded
 * biome spawn list are treated as event/structure/summon gated rather than as
 * ordinary ambient mobs. This is intentionally a soft acquisition modifier;
 * it does not attempt to model farms or exact spawn-cap mechanics.
 */
final class ShadowMobSpawnIndex {

    private final Map<ResourceLocation, MutableSpawnStats> stats;
    private final int biomeCount;
    private final ShadowStructureIndex structureIndex;

    private ShadowMobSpawnIndex(
            Map<ResourceLocation, MutableSpawnStats> stats,
            int biomeCount,
            ShadowStructureIndex structureIndex
    ) {
        this.stats = Map.copyOf(stats);
        this.biomeCount = biomeCount;
        this.structureIndex = structureIndex;
    }

    static ShadowMobSpawnIndex build(
            MinecraftServer server,
            ShadowStructureIndex structureIndex
    ) {
        Map<ResourceLocation, Resource> resources;
        try {
            resources = server.getResourceManager().listResources(
                    "worldgen/biome",
                    id -> id.getPath().endsWith(".json")
            );
        } catch (RuntimeException exception) {
            EssenceAscendance.LOGGER.debug(
                    "Procedural valuation could not enumerate biome spawn data: {}",
                    exception.getMessage()
            );
            return new ShadowMobSpawnIndex(Map.of(), 0, structureIndex);
        }

        Map<ResourceLocation, MutableSpawnStats> stats = new LinkedHashMap<>();
        int biomes = 0;
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed == null || !parsed.isJsonObject()) {
                    continue;
                }
                JsonObject root = parsed.getAsJsonObject();
                JsonElement spawnersElement = root.get("spawners");
                if (spawnersElement == null || !spawnersElement.isJsonObject()) {
                    biomes++;
                    continue;
                }

                JsonObject spawners = spawnersElement.getAsJsonObject();
                Map<ResourceLocation, BiomeSpawnSample> biomeSamples = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> categoryEntry : spawners.entrySet()) {
                    if (!categoryEntry.getValue().isJsonArray()) {
                        continue;
                    }
                    for (JsonElement spawnElement : categoryEntry.getValue().getAsJsonArray()) {
                        if (!spawnElement.isJsonObject()) {
                            continue;
                        }
                        JsonObject spawn = spawnElement.getAsJsonObject();
                        String rawType = readString(spawn, "type");
                        ResourceLocation entityId = rawType == null ? null : ResourceLocation.tryParse(rawType);
                        if (entityId == null) {
                            continue;
                        }
                        double weight = Math.max(0.0, readDouble(spawn, "weight", 1.0));
                        double minCount = Math.max(1.0, readDouble(spawn, "minCount", readDouble(spawn, "min_count", 1.0)));
                        double maxCount = Math.max(minCount, readDouble(spawn, "maxCount", readDouble(spawn, "max_count", minCount)));
                        biomeSamples.computeIfAbsent(entityId, ignored -> new BiomeSpawnSample())
                                .add(weight, (minCount + maxCount) / 2.0);
                    }
                }
                biomeSamples.forEach((entityId, sample) ->
                        stats.computeIfAbsent(entityId, ignored -> new MutableSpawnStats())
                                .addBiome(sample.totalWeight, sample.averagePack())
                );
                biomes++;
            } catch (IOException | RuntimeException exception) {
                EssenceAscendance.LOGGER.debug(
                        "Procedural valuation skipped biome {} while indexing mob spawns: {}",
                        entry.getKey(),
                        exception.getMessage()
                );
            }
        }

        EssenceAscendance.LOGGER.info(
                "Procedural mob-spawn index built: {} biomes, {} naturally listed entity types",
                biomes,
                stats.size()
        );
        return new ShadowMobSpawnIndex(stats, biomes, structureIndex);
    }

    SpawnAvailability forEntity(ResourceLocation entityId) {
        ShadowStructureIndex.StructureSpawnOccurrence structureSpawn =
                structureIndex == null ? null : structureIndex.forEntitySpawn(entityId);

        if (biomeCount <= 0) {
            if (structureSpawn != null) {
                return fromStructure(structureSpawn);
            }
            return SpawnAvailability.UNKNOWN;
        }

        MutableSpawnStats value = stats.get(entityId);
        if (value == null || value.biomeCount <= 0) {
            if (structureSpawn != null) {
                return fromStructure(structureSpawn);
            }
            return new SpawnAvailability(
                    ShadowValuationSettings.NON_BIOME_SPAWN_MULTIPLIER,
                    true,
                    0,
                    0.0,
                    0.0,
                    List.of("not listed in biome or data-driven structure spawn tables; likely event/summon/special-structure gated")
            );
        }

        double coverage = Math.max(0.0001, (double) value.biomeCount / biomeCount);
        double averageWeight = value.totalWeight / value.biomeCount;
        double averagePack = value.totalPack / value.biomeCount;

        double biomeMultiplier = Math.pow(1.0 / coverage, 0.20)
                * Math.pow(100.0 / Math.max(1.0, averageWeight), 0.10)
                / Math.pow(Math.max(1.0, averagePack), 0.08);
        biomeMultiplier = Math.max(
                ShadowValuationSettings.MOB_SPAWN_RARITY_MIN_MULTIPLIER,
                Math.min(ShadowValuationSettings.MOB_SPAWN_RARITY_MAX_MULTIPLIER, biomeMultiplier)
        );

        List<String> signals = new ArrayList<>();
        signals.add("natural biome coverage " + value.biomeCount + "/" + biomeCount);
        signals.add("average spawn weight " + String.format(Locale.ROOT, "%.1f", averageWeight));
        signals.add("average pack " + String.format(Locale.ROOT, "%.1f", averagePack));

        if (structureSpawn != null && structureSpawn.multiplier() < biomeMultiplier) {
            List<String> structureSignals = new ArrayList<>(structureSpawn.signals());
            structureSignals.add("structure path easier than biome-spawn estimate");
            return new SpawnAvailability(
                    Math.max(
                            ShadowValuationSettings.MOB_SPAWN_RARITY_MIN_MULTIPLIER,
                            Math.min(
                                    ShadowValuationSettings.MOB_SPAWN_RARITY_MAX_MULTIPLIER,
                                    structureSpawn.multiplier()
                            )
                    ),
                    true,
                    value.biomeCount,
                    averageWeight,
                    averagePack,
                    List.copyOf(structureSignals)
            );
        }

        if (structureSpawn != null) {
            signals.add("structure-spawn alternative also detected: " + structureSpawn.structureId());
        }

        return new SpawnAvailability(
                biomeMultiplier,
                true,
                value.biomeCount,
                averageWeight,
                averagePack,
                List.copyOf(signals)
        );
    }

    private static SpawnAvailability fromStructure(
            ShadowStructureIndex.StructureSpawnOccurrence structureSpawn
    ) {
        double multiplier = Math.max(
                ShadowValuationSettings.MOB_SPAWN_RARITY_MIN_MULTIPLIER,
                Math.min(
                        ShadowValuationSettings.MOB_SPAWN_RARITY_MAX_MULTIPLIER,
                        structureSpawn.multiplier()
                )
        );
        return new SpawnAvailability(
                multiplier,
                true,
                0,
                0.0,
                structureSpawn.averagePack(),
                structureSpawn.signals()
        );
    }

    private static String readString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }

    private static double readDouble(JsonObject object, String key, double fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                ? value.getAsDouble()
                : fallback;
    }

    record SpawnAvailability(
            double multiplier,
            boolean known,
            int biomeCount,
            double averageWeight,
            double averagePack,
            List<String> signals
    ) {
        static final SpawnAvailability UNKNOWN = new SpawnAvailability(
                1.0, false, 0, 0.0, 0.0, List.of()
        );

        SpawnAvailability {
            signals = List.copyOf(signals);
        }
    }

    private static final class MutableSpawnStats {
        private int biomeCount;
        private double totalWeight;
        private double totalPack;

        void addBiome(double weight, double pack) {
            biomeCount++;
            totalWeight += Math.max(0.0, weight);
            totalPack += Math.max(1.0, pack);
        }
    }

    private static final class BiomeSpawnSample {
        private double totalWeight;
        private double weightedPack;

        void add(double weight, double pack) {
            double resolvedWeight = Math.max(0.0, weight);
            totalWeight += resolvedWeight;
            weightedPack += Math.max(1.0, pack) * Math.max(1.0, resolvedWeight);
        }

        double averagePack() {
            return weightedPack / Math.max(1.0, totalWeight);
        }
    }
}
