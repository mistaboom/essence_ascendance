package com.mistaboom.essence_ascendance.config;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Global, pre-world Latent Ore distribution settings.
 *
 * <p>Built-in Overworld/Nether/End values remain implicit unless explicitly
 * overridden in the config. Custom-dimension rules are optional and may target
 * either one exact dimension id or a dimension-type tag.</p>
 */
public final class LatentOreWorldgenSettings {

    public record DimensionSettings(
            boolean enabled,
            int veinSize,
            int veinsPerChunk,
            int minY,
            int maxY,
            double discardChanceOnAirExposure
    ) {
        public DimensionSettings {
            if (veinSize < 1 || veinSize > 64) {
                throw new IllegalArgumentException("Latent Ore vein size must be between 1 and 64");
            }
            if (veinsPerChunk < 0 || veinsPerChunk > 128) {
                throw new IllegalArgumentException("Latent Ore veins/chunk must be between 0 and 128");
            }
            if (minY < -2048 || minY > 2048 || maxY < -2048 || maxY > 2048) {
                throw new IllegalArgumentException("Latent Ore Y range must stay between -2048 and 2048");
            }
            if (minY > maxY) {
                throw new IllegalArgumentException("Latent Ore min_y cannot be greater than max_y");
            }
            if (!Double.isFinite(discardChanceOnAirExposure)
                    || discardChanceOnAirExposure < 0.0D
                    || discardChanceOnAirExposure > 1.0D) {
                throw new IllegalArgumentException(
                        "Latent Ore discard_chance_on_air_exposure must be between 0.0 and 1.0"
                );
            }
        }
    }

    public enum OreVariant {
        STONE("stone"),
        DEEPSLATE("deepslate"),
        NETHERRACK("netherrack"),
        END_STONE("end_stone");

        private final String configName;

        OreVariant(String configName) {
            this.configName = configName;
        }

        public String configName() {
            return configName;
        }

        public static OreVariant parse(String raw) {
            if (raw == null) {
                throw new IllegalArgumentException("ore_variant cannot be null");
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
            for (OreVariant variant : values()) {
                if (variant.configName.equals(normalized)) {
                    return variant;
                }
            }
            throw new IllegalArgumentException(
                    "Unknown ore_variant '" + raw
                            + "' (expected stone, deepslate, netherrack, or end_stone)"
            );
        }
    }

    public record ReplacementTarget(
            ResourceLocation id,
            boolean tag
    ) {
        public ReplacementTarget {
            Objects.requireNonNull(id, "Replacement target id cannot be null");
        }

        public String configValue() {
            return tag ? "#" + id : id.toString();
        }
    }

    public record CustomDimensionSettings(
            ResourceLocation dimension,
            ResourceLocation dimensionTypeTag,
            ReplacementTarget replacement,
            OreVariant oreVariant,
            ResourceLocation oreBlock,
            DimensionSettings distribution
    ) {
        public CustomDimensionSettings {
            if ((dimension == null) == (dimensionTypeTag == null)) {
                throw new IllegalArgumentException(
                        "Exactly one of dimension or dimension_type_tag must be configured"
                );
            }
            Objects.requireNonNull(replacement, "Custom Latent Ore replacement target cannot be null");
            Objects.requireNonNull(oreVariant, "Custom Latent Ore ore variant cannot be null");
            Objects.requireNonNull(distribution, "Custom Latent Ore distribution cannot be null");
        }

        /**
         * Optional registered block to place instead of one of the built-in
         * Latent Ore substrate variants. This is intended for compatibility
         * addons/resource packs that register a native-looking ore block for
         * a custom dimension. When present it overrides {@link #oreVariant()}.
         */
        public boolean usesCustomOreBlock() {
            return oreBlock != null;
        }
    }

    private final DimensionSettings overworld;
    private final DimensionSettings nether;
    private final DimensionSettings end;
    private final Map<String, CustomDimensionSettings> customDimensions;

    public LatentOreWorldgenSettings(
            DimensionSettings overworld,
            DimensionSettings nether,
            DimensionSettings end,
            Map<String, CustomDimensionSettings> customDimensions
    ) {
        this.overworld = Objects.requireNonNull(overworld, "Overworld Latent Ore settings cannot be null");
        this.nether = Objects.requireNonNull(nether, "Nether Latent Ore settings cannot be null");
        this.end = Objects.requireNonNull(end, "End Latent Ore settings cannot be null");
        this.customDimensions = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(customDimensions, "Custom Latent Ore rules cannot be null"))
        );
    }

    public DimensionSettings overworld() {
        return overworld;
    }

    public DimensionSettings nether() {
        return nether;
    }

    public DimensionSettings end() {
        return end;
    }

    public Map<String, CustomDimensionSettings> customDimensions() {
        return customDimensions;
    }

    public static LatentOreWorldgenSettings defaults() {
        return new LatentOreWorldgenSettings(
                // Bootstrap supply: intentionally modest and concentrated underground.
                new DimensionSettings(
                        true,
                        4,
                        2,
                        -48,
                        16,
                        0.25D
                ),
                // First industrial source: substantially more Latent per generated chunk.
                new DimensionSettings(
                        true,
                        7,
                        4,
                        16,
                        112,
                        0.0D
                ),
                // Late-game bulk source: largest/richest deposits.
                new DimensionSettings(
                        true,
                        10,
                        6,
                        0,
                        80,
                        0.0D
                ),
                Map.of()
        );
    }
}
