package com.mistaboom.essence_ascendance.neoforge;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeGenerationSettingsBuilder;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** NeoForge-only registration used to attach dimension-aware ore generation to every biome. */
public final class EssenceAscendanceNeoForgeWorldgen {

    private static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
            DeferredRegister.create(
                    NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS,
                    EssenceAscendance.MOD_ID
            );

    public static final DeferredHolder<
            MapCodec<? extends BiomeModifier>,
            MapCodec<AllBiomesAddFeaturesBiomeModifier>
            > ADD_FEATURES_ALL_BIOMES = BIOME_MODIFIER_SERIALIZERS.register(
            "add_features_all_biomes",
            () -> RecordCodecBuilder.mapCodec(instance -> instance.group(
                    PlacedFeature.LIST_CODEC
                            .fieldOf("features")
                            .forGetter(AllBiomesAddFeaturesBiomeModifier::features),
                    GenerationStep.Decoration.CODEC
                            .fieldOf("step")
                            .forGetter(AllBiomesAddFeaturesBiomeModifier::step)
            ).apply(instance, AllBiomesAddFeaturesBiomeModifier::new))
    );

    private EssenceAscendanceNeoForgeWorldgen() {
    }

    public static void register(IEventBus modBus) {
        BIOME_MODIFIER_SERIALIZERS.register(modBus);
    }

    /**
     * Adds the configured feature to every biome at one consistent generation
     * step. LatentOreFeature resolves the actual dimension and its single generation policy.
     */
    public record AllBiomesAddFeaturesBiomeModifier(
            HolderSet<PlacedFeature> features,
            GenerationStep.Decoration step
    ) implements BiomeModifier {

        @Override
        public void modify(
                Holder<Biome> biome,
                Phase phase,
                ModifiableBiomeInfo.BiomeInfo.Builder builder
        ) {
            if (phase != Phase.ADD) {
                return;
            }

            BiomeGenerationSettingsBuilder generationSettings =
                    builder.getGenerationSettings();
            features.forEach(holder -> {
                if (!generationSettings.getFeatures(step).contains(holder)) generationSettings.addFeature(step, holder);
            });
        }

        @Override
        public MapCodec<? extends BiomeModifier> codec() {
            return ADD_FEATURES_ALL_BIOMES.get();
        }
    }
}
