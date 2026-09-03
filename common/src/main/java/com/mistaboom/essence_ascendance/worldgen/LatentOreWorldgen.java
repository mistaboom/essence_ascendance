package com.mistaboom.essence_ascendance.worldgen;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Registration and stable keys for Latent Ore world generation. */
public final class LatentOreWorldgen {

    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(EssenceAscendance.MOD_ID, Registries.FEATURE);

    public static final RegistrySupplier<LatentOreFeature> OVERWORLD_FEATURE = FEATURES.register(
            "latent_ore_overworld",
            () -> new LatentOreFeature(
                    NoneFeatureConfiguration.CODEC,
                    LatentOreFeature.Target.OVERWORLD
            )
    );

    public static final RegistrySupplier<LatentOreFeature> NETHER_FEATURE = FEATURES.register(
            "latent_ore_nether",
            () -> new LatentOreFeature(
                    NoneFeatureConfiguration.CODEC,
                    LatentOreFeature.Target.NETHER
            )
    );

    public static final RegistrySupplier<LatentOreFeature> END_FEATURE = FEATURES.register(
            "latent_ore_end",
            () -> new LatentOreFeature(
                    NoneFeatureConfiguration.CODEC,
                    LatentOreFeature.Target.END
            )
    );

    public static final RegistrySupplier<LatentOreFeature> CUSTOM_FEATURE = FEATURES.register(
            "latent_ore_custom",
            () -> new LatentOreFeature(
                    NoneFeatureConfiguration.CODEC,
                    LatentOreFeature.Target.CUSTOM
            )
    );

    public static final ResourceKey<PlacedFeature> OVERWORLD_PLACED = placedKey("latent_ore_overworld");
    public static final ResourceKey<PlacedFeature> NETHER_PLACED = placedKey("latent_ore_nether");
    public static final ResourceKey<PlacedFeature> END_PLACED = placedKey("latent_ore_end");
    public static final ResourceKey<PlacedFeature> CUSTOM_PLACED = placedKey("latent_ore_custom");

    private static boolean initialized = false;

    private LatentOreWorldgen() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        FEATURES.register();
        initialized = true;
    }

    private static ResourceKey<PlacedFeature> placedKey(String path) {
        return ResourceKey.create(
                Registries.PLACED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, path)
        );
    }
}
