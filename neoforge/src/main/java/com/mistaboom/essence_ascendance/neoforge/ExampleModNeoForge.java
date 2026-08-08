package com.mistaboom.essence_ascendance.neoforge;

import net.neoforged.fml.common.Mod;

import com.mistaboom.essence_ascendance.ExampleMod;

@Mod(ExampleMod.MOD_ID)
public final class ExampleModNeoForge {
    public ExampleModNeoForge() {
        // Run our common setup.
        ExampleMod.init();
    }
}
