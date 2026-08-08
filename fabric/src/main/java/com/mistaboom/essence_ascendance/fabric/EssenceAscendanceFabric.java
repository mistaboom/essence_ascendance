package com.mistaboom.essence_ascendance.fabric;

import net.fabricmc.api.ModInitializer;

import com.mistaboom.essence_ascendance.EssenceAscendance;

public final class EssenceAscendanceFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        // Run our common setup.
        EssenceAscendance.init();
    }
}
