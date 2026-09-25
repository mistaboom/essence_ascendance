package com.mistaboom.essence_ascendance.config;

import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One distribution owner per exact dimension. Overrides replace selection, never add a pass. */
public record LatentOreWorldgenSettings(
        DimensionSettings overworld, DimensionSettings nether, DimensionSettings end,
        boolean automaticDimensions, Map<ResourceLocation, DimensionOverride> dimensions
) {
    public record DimensionSettings(boolean enabled, int veinSize, int veinsPerChunk,
                                    int minY, int maxY, double discardChanceOnAirExposure) {
        public DimensionSettings {
            if (veinSize < 1 || veinSize > 64) throw new IllegalArgumentException("Latent Ore vein_size must be 1..64");
            if (veinsPerChunk < 0 || veinsPerChunk > 128) throw new IllegalArgumentException("Latent Ore veins_per_chunk must be 0..128");
            if (minY < -2048 || maxY > 2048 || minY > maxY)
                throw new IllegalArgumentException("Latent Ore min_y/max_y must be ordered within -2048..2048");
            if (!Double.isFinite(discardChanceOnAirExposure) || discardChanceOnAirExposure < 0 || discardChanceOnAirExposure > 1)
                throw new IllegalArgumentException("Latent Ore discard_chance_on_air_exposure must be 0..1");
        }
    }

    /** Empty hosts means automatic discovery; an explicit disabled rule always wins. */
    public record DimensionOverride(boolean enabled, List<ResourceLocation> hosts, DimensionSettings distribution) {
        public DimensionOverride {
            hosts = List.copyOf(Objects.requireNonNull(hosts));
            if (hosts.size() > 32 || hosts.stream().distinct().count() != hosts.size())
                throw new IllegalArgumentException("Latent Ore hosts must be at most 32 distinct exact block ids");
        }
    }

    public LatentOreWorldgenSettings {
        Objects.requireNonNull(overworld);
        Objects.requireNonNull(nether);
        Objects.requireNonNull(end);
        dimensions = Map.copyOf(Objects.requireNonNull(dimensions));
    }

    public DimensionSettings distribution(ResourceLocation dimension, int minY, int maxY) {
        DimensionOverride override = dimensions.get(dimension);
        DimensionSettings selected = override != null && override.distribution() != null ? override.distribution() : switch (dimension.toString()) {
            case "minecraft:overworld" -> overworld;
            case "minecraft:the_nether" -> nether;
            case "minecraft:the_end" -> end;
            default -> new DimensionSettings(true, overworld.veinSize(), overworld.veinsPerChunk(),
                    minY, maxY, overworld.discardChanceOnAirExposure());
        };
        return override == null ? selected : new DimensionSettings(override.enabled(), selected.veinSize(), selected.veinsPerChunk(),
                selected.minY(), selected.maxY(), selected.discardChanceOnAirExposure());
    }

    public boolean enabled(ResourceLocation dimension) {
        DimensionOverride override = dimensions.get(dimension);
        if (override != null) return override.enabled();
        return automaticDimensions || isVanillaDimension(dimension);
    }

    public static boolean isVanillaDimension(ResourceLocation dimension) {
        return switch (dimension.toString()) {
            case "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end" -> true;
            default -> false;
        };
    }

    public static LatentOreWorldgenSettings defaults() {
        return new LatentOreWorldgenSettings(
                new DimensionSettings(true, 9, 16, -48, 64, 0),
                new DimensionSettings(true, 9, 16, 16, 112, 0),
                new DimensionSettings(true, 9, 16, 0, 80, 0),
                true, Map.of());
    }
}
