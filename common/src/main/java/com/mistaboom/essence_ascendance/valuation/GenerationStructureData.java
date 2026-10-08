package com.mistaboom.essence_ascendance.valuation;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/** Configured structure eligibility, distinct from actual placement, access gates and template linkage. */
record GenerationStructureData(Set<ResourceLocation> sets, Set<ResourceLocation> structures) {
    /** Generic source analysis needs public base settings, not an optional structure's placement encoder. */
    static com.google.gson.JsonObject settings(ResourceLocation id, net.minecraft.world.level.levelgen.structure.Structure structure,
                                               HolderLookup.Provider registries) {
        var settings = new net.minecraft.world.level.levelgen.structure.Structure.StructureSettings(
                structure.biomes(), structure.spawnOverrides(), structure.step(), structure.terrainAdaptation());
        return GenerationDataSnapshot.encodeDefinition(net.minecraft.world.level.levelgen.structure.Structure.StructureSettings.CODEC.codec(),
                settings, registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), "effective structure settings " + id);
    }

    static GenerationStructureData capture(ChunkGenerator generator, HolderLookup.Provider registries, boolean enabled) {
        if (!enabled || generator.getClass() != FlatLevelSource.class && generator.getClass() != NoiseBasedChunkGenerator.class)
            return new GenerationStructureData(Set.of(), Set.of());
        var possibleBiomes = generator.getBiomeSource().possibleBiomes();
        Stream<? extends Holder<StructureSet>> candidates = generator instanceof FlatLevelSource flat && flat.settings().structureOverrides().isPresent()
                ? flat.settings().structureOverrides().get().stream() : registries.lookupOrThrow(Registries.STRUCTURE_SET).listElements();
        Set<ResourceLocation> sets = new TreeSet<>(), structures = new TreeSet<>();
        try { candidates.forEach(set -> {
            boolean eligible = false;
            for (var entry : set.value().structures()) {
                if (entry.structure().value().biomes().stream().noneMatch(biome -> possibleBiomes.stream().anyMatch(possible -> possible.unwrapKey().equals(biome.unwrapKey())))) continue;
                var id = entry.structure().unwrapKey().map(ResourceKey::location).orElse(null);
                if (id != null) { structures.add(id); eligible = true; }
            }
            if (eligible) set.unwrapKey().map(ResourceKey::location).ifPresent(sets::add);
        }); } catch (UnsupportedOperationException notReady) {
            throw new IllegalStateException("Structure biome tags are not ready for authoritative generation; no empty evidence result was accepted", notReady);
        }
        return new GenerationStructureData(Set.copyOf(sets), Set.copyOf(structures));
    }
}
