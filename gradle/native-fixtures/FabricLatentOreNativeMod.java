package com.mistaboom.essence_ascendance.fixture;

import com.mistaboom.essence_ascendance.ore.LatentOreNativeFixture;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class FabricLatentOreNativeMod implements ModInitializer {
    @Override public void onInitialize() {
        LatentOreNativeFixture.registerBlocks((id, block) -> Registry.register(BuiltInRegistries.BLOCK, id, block));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                LatentOreNativeFixture.run(server);
                server.halt(false);
            } catch (Throwable failure) {
                failure.printStackTrace();
                Runtime.getRuntime().halt(1);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            System.out.println("LatentOreNativeFixture: saved world and stopped");
            Runtime.getRuntime().halt(0);
        });
    }
}
