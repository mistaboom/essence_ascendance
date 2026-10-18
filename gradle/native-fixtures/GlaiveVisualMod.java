package com.mistaboom.essence_ascendance.fixture;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Explicit OpenGL screenshot fixture only; no world is created. */
@Mod(value="essence_glaive_visual_test",dist=net.neoforged.api.distmarker.Dist.CLIENT)
public final class GlaiveVisualMod {
    public GlaiveVisualMod(IEventBus bus) { bus.addListener(this::setup); }
    private void setup(FMLClientSetupEvent event) {
        event.enqueueWork(com.mistaboom.essence_ascendance.projectile.GlaiveNativeVisualFixture::init);
    }
}
