package com.mistaboom.essence_ascendance.fabric;

import net.fabricmc.api.ModInitializer;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleContent;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleStructureService;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;

public final class EssenceAscendanceFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        // Run our common setup.
        EssenceAscendance.init();

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
    }
}
