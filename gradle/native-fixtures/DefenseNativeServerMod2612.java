package com.mistaboom.essence_ascendance.fixture;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Runs the 26.1.2 projectile probe only after real registry/world binding. */
@Mod("essence_defense_native_server_test")
public final class DefenseNativeServerMod2612 {
    public DefenseNativeServerMod2612(IEventBus bus) {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::stopped);
    }

    private void started(ServerStartedEvent event) {
        try {
            Class.forName("com.mistaboom.essence_ascendance.projectile.ProjectileNativeServerFixture2612")
                    .getMethod("run", net.minecraft.server.level.ServerLevel.class)
                    .invoke(null, event.getServer().overworld());
            event.getServer().halt(false);
        } catch (Throwable failure) {
            failure.printStackTrace();
            Runtime.getRuntime().halt(1);
        }
    }

    private void stopped(ServerStoppedEvent event) {
        // The fixture already requested normal shutdown after its assertions.
        // Exit only after Minecraft has saved the world and posted the stopped event.
        System.out.println("DefenseNativeServerMod2612: saved world and stopped; exiting fixture");
        // Java's shutdown hooks include a lingering development-launcher thread.
        // The world is already saved and the server has posted its stopped event.
        Runtime.getRuntime().halt(0);
    }
}
