package com.mistaboom.essence_ascendance.worldgen;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.config.LatentOreWorldgenSettings;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Both loaders schedule exactly this feature once in each biome. */
public final class LatentOreWorldgen {
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(EssenceAscendance.MOD_ID, Registries.FEATURE);
    public static final RegistrySupplier<LatentOreFeature> FEATURE = FEATURES.register("adaptive_latent_ore",
            () -> new LatentOreFeature(NoneFeatureConfiguration.CODEC));
    public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE,
            ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "adaptive_latent_ore"));
    private static boolean initialized;
    private LatentOreWorldgen() { }
    public static void init() {
        if (initialized) return;
        FEATURES.register();
        LifecycleEvent.SERVER_STOPPED.register(server -> PrimarySubstrateDiscovery.clear());
        initialized = true;
    }

    static LatentOreWorldgenSettings settings() {
        return EssenceConfigManager.serverWorldgen();
    }
}
