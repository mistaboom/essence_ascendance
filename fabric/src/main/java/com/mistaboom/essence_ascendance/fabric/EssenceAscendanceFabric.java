package com.mistaboom.essence_ascendance.fabric;

import net.fabricmc.api.ModInitializer;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleContent;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleStructureService;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mistaboom.essence_ascendance.worldgen.LatentOreWorldgen;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.world.level.levelgen.GenerationStep;

public final class EssenceAscendanceFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        // Run our common setup.
        EssenceAscendance.init();

        BiomeModifications.addFeature(
                BiomeSelectors.tag(ConventionalBiomeTags.IS_OVERWORLD),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                LatentOreWorldgen.OVERWORLD_PLACED
        );
        BiomeModifications.addFeature(
                BiomeSelectors.tag(ConventionalBiomeTags.IS_NETHER),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                LatentOreWorldgen.NETHER_PLACED
        );
        BiomeModifications.addFeature(
                BiomeSelectors.tag(ConventionalBiomeTags.IS_END),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                LatentOreWorldgen.END_PLACED
        );
        BiomeModifications.addFeature(
                BiomeSelectors.all(),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                LatentOreWorldgen.CUSTOM_PLACED
        );

        /*
         * Register a specific provider ahead of Fabric's vanilla-inventory
         * fallback so pipes and hoppers see the Crucible as insertion-only.
         */
        ItemStorage.SIDED.registerForBlockEntities(
                (blockEntity, direction) -> {
                    if (!(blockEntity instanceof EssenceCrucibleBlockEntity crucible)) {
                        return null;
                    }

                    if (direction != null
                            && crucible.getLevel() != null
                            && !EssenceCrucibleStructureService.allowsAutomationConnection(
                                    crucible.getLevel(),
                                    crucible.getBlockPos(),
                                    direction
                            )) {
                        return null;
                    }
                    return new EssenceCrucibleFabricItemStorage(crucible);
                },
                EssenceCrucibleContent.ESSENCE_CRUCIBLE_BLOCK_ENTITY.get()
        );

        ItemStorage.SIDED.registerForBlockEntities(
                (blockEntity, direction) -> blockEntity instanceof EssenceInfuserBlockEntity infuser
                        ? new EssenceInfuserFabricItemStorage(infuser)
                        : null,
                EssenceInfuserContent.ESSENCE_INFUSER_BLOCK_ENTITY.get()
        );
    }
}
