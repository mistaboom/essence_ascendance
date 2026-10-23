package com.mistaboom.essence_ascendance.fixture;

import com.mistaboom.essence_ascendance.ore.LatentOreNativeFixture;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@Mod("essence_latent_ore_item_test")
public final class NeoForgeLatentOreNativeMod {
    public NeoForgeLatentOreNativeMod(IEventBus bus) {
        bus.addListener(NeoForgeLatentOreNativeMod::register);
        NeoForge.EVENT_BUS.addListener(NeoForgeLatentOreNativeMod::started);
        NeoForge.EVENT_BUS.addListener(NeoForgeLatentOreNativeMod::stopped);
    }
    private static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> LatentOreNativeFixture.registerBlocks(helper::register));
    }
    private static void started(ServerStartedEvent event) {
        try {
            LatentOreNativeFixture.run(event.getServer());
            event.getServer().halt(false);
        } catch (Throwable failure) {
            failure.printStackTrace();
            Runtime.getRuntime().halt(1);
        }
    }
    private static void stopped(ServerStoppedEvent event) {
        System.out.println("LatentOreNativeFixture: saved world and stopped");
        Runtime.getRuntime().halt(0);
    }
}
