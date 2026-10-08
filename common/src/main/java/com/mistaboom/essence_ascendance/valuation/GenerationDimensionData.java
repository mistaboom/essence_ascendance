package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pure loaded-generator projection, also exercised with native synthetic fixtures. Never requests terrain. */
record GenerationDimensionData(Map<ResourceLocation, JsonObject> biomes, Map<ResourceLocation, JsonObject> naturalBiomes,
                               Map<ResourceLocation, JsonObject> terrain, GenerationDataSnapshot.DimensionEvidence evidence,
                               List<String> limitations) {
    static GenerationDimensionData capture(ResourceLocation dimension, ChunkGenerator generator, boolean levelAvailable,
                                            HolderLookup.Provider registries) {
        return capture(dimension, generator, levelAvailable, registries, new TreeMap<>());
    }
    static GenerationDimensionData capture(ResourceLocation dimension, ChunkGenerator generator, boolean levelAvailable,
                                            HolderLookup.Provider registries, Map<ResourceLocation, JsonObject> sharedBiomes) {
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        Map<ResourceLocation, JsonObject> biomes = new TreeMap<>(), natural = new TreeMap<>(), terrain = new TreeMap<>();
        // Subclasses can replace noise fill, surface, decorations and structures (for
        // example a void generator). Inheriting a class never proves native terrain.
        boolean supported = generator.getClass() == NoiseBasedChunkGenerator.class || generator.getClass() == FlatLevelSource.class;
        List<String> biomeIds = new ArrayList<>(), limitations = new ArrayList<>();
        if (!supported) limitations.add("Dimension " + dimension + ": unsupported generator " + generator.getClass().getName() + "; geology and decoration remain unknown");
        for (var biome : generator.getBiomeSource().possibleBiomes()) {
            ResourceLocation biomeId = biome.unwrapKey().map(ResourceKey::location).orElseThrow(() ->
                    new IllegalStateException("Inline biome in " + dimension + " requires a generation adapter"));
            biomeIds.add(biomeId.toString());
            JsonObject body = sharedBiomes.computeIfAbsent(biomeId, ignored -> encode(Biome.DIRECT_CODEC, biome.value(), ops, "effective biome " + biomeId));
            biomes.put(biomeId, body);
            if (supported) {
                JsonObject root = new JsonObject();
                root.add("features", generator instanceof FlatLevelSource flat
                        // Native adjustment nulls non-motion-blocking layers in its receiver.
                        // Work on a reconstructed definition, never mutate the live generator's
                        // layer cache or invoke adjustment twice on that one-shot cache.
                        ? encode(BiomeGenerationSettings.CODEC.codec(), flat.settings().withBiomeAndLayers(
                                flat.settings().getLayersInfo(), flat.settings().structureOverrides(), flat.settings().getBiome())
                                .adjustGenerationSettings(biome), ops,
                            "effective decoration " + dimension + "/" + biomeId).get("features")
                        : body.get("features"));
                natural.put(ResourceLocation.fromNamespaceAndPath(dimension.getNamespace(), dimension.getPath() + "/biome/" + biomeId.getNamespace() + "/" + biomeId.getPath()), root);
            }
        }
        String geology = "unknown";
        if (supported && generator instanceof NoiseBasedChunkGenerator noise) {
            terrain.put(dimension, encode(NoiseGeneratorSettings.DIRECT_CODEC, noise.generatorSettings().value(), ops, "effective terrain " + dimension));
            geology = "supported_noise_terrain";
        } else if (supported && generator instanceof FlatLevelSource flat) {
            JsonObject root = new JsonObject(); JsonArray layers = new JsonArray();
            flat.settings().getLayersInfo().stream().map(net.minecraft.world.level.levelgen.flat.FlatLayerInfo::getBlockState)
                    .filter(state -> !state.isAir()).distinct().forEach(state -> {
                JsonObject layer = new JsonObject(); layer.addProperty("Name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()); layers.add(layer);
            });
            root.add("surface_rule", layers); terrain.put(dimension, root);
            geology = layers.isEmpty() ? "proven_no_flat_terrain" : "supported_flat_layers";
        }
        return new GenerationDimensionData(Map.copyOf(biomes), Map.copyOf(natural), Map.copyOf(terrain),
                new GenerationDataSnapshot.DimensionEvidence(dimension.toString(), levelAvailable, generator.getClass().getName(),
                        supported ? "supported_data" : "partial_unknown_terrain", biomeIds, geology), List.copyOf(limitations));
    }
    private static <T> JsonObject encode(Codec<T> codec, T value, RegistryOps<com.google.gson.JsonElement> ops, String provenance) {
        return GenerationDataSnapshot.encodeDefinition(codec, value, ops, provenance);
    }
}
