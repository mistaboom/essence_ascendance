package com.mistaboom.essence_ascendance.fixture;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import com.mistaboom.essence_ascendance.projectile.ProjectileNativeHookTest;

/** Explicit no-world fixture only. Never included in either production package. */
@Mod(value = "essence_defense_native_test", dist = net.neoforged.api.distmarker.Dist.CLIENT)
public final class DefenseNativeMod {
    public DefenseNativeMod(IEventBus bus) { bus.addListener(this::setup); }
    private void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (!Boolean.getBoolean("essence.projectile.nativeHookTest"))
                throw new IllegalStateException("Explicit no-world native fixture required");
            var client = net.minecraft.client.Minecraft.getInstance();
            if (client.level != null || client.getSingleplayerServer() != null)
                throw new IllegalStateException("Native fixture cannot run with an open world");
            // FML's own sync/default API creates SynchronizedConfig instances with a null
            // backing path. It never opens serverconfig files or creates a Minecraft world.
            // This preserves real native onClimbable/NeoForge ladder policy in the fixture.
            net.neoforged.fml.config.ConfigTracker.INSTANCE.loadDefaultServerConfigs();
            net.neoforged.neoforge.common.NeoForgeConfig.SERVER.fullBoundingBoxLadders.get();
            // This setup-time fixture exits before ClientModLoader's ordinary end-of-loading
            // EVENT_BUS.start(). Enable the public native bus so cancellation and other
            // gameplay events execute exactly as they do after normal game loading.
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.start();
            ProjectileNativeHookTest.runAndExit();
        });
    }
}
