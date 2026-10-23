package com.mistaboom.essence_ascendance.fixture;

import com.mistaboom.essence_ascendance.projectile.ProjectileNativeHookTest;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** 26.1.2 initialization-only hook probe, never bundled in production. */
@Mod(value = "essence_defense_native_test", dist = net.neoforged.api.distmarker.Dist.CLIENT)
public final class DefenseNativeMod2612 {
    public DefenseNativeMod2612(IEventBus bus) { bus.addListener(this::setup); }

    private void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (!Boolean.getBoolean("essence.projectile.nativeHookTest"))
                throw new IllegalStateException("Explicit native fixture required");
            var client = net.minecraft.client.Minecraft.getInstance();
            if (client.level != null || client.getSingleplayerServer() != null)
                throw new IllegalStateException("Native fixture cannot run with an open world");
            net.neoforged.fml.config.ConfigTracker.INSTANCE.loadDefaultServerConfigs();
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.start();
            ProjectileNativeHookTest.runAndExit();
        });
    }
}
