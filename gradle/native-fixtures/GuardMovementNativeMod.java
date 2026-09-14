package com.mistaboom.essence_ascendance.fixture;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Compiled/packaged only by the explicitly requested no-world client fixture Gradle init script. */
@Mod(value = "essence_guard_movement_hook_test", dist = Dist.CLIENT)
public final class GuardMovementNativeMod {
    public GuardMovementNativeMod(IEventBus bus) { bus.addListener(this::setup); }
    private void setup(FMLClientSetupEvent event) {
        event.enqueueWork(GuardMovementNativeTest::runAndExit);
    }
}
