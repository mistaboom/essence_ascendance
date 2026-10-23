package com.mistaboom.essence_ascendance.fixture;

import com.mistaboom.essence_ascendance.projectile.ProjectileNativeServerFixture2612;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** Fabric lifecycle boundary for the shared registry-bound 26.1.2 projectile probe. */
public final class FabricProjectileNativeMod2612 implements ModInitializer {
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                ProjectileNativeServerFixture2612.run(server.overworld());
                server.halt(false);
            } catch (Throwable failure) {
                failure.printStackTrace();
                Runtime.getRuntime().halt(1);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            System.out.println("FabricProjectileNativeMod2612: saved world and stopped");
            Runtime.getRuntime().halt(0);
        });
    }
}
