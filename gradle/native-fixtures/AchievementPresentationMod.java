package com.mistaboom.essence_ascendance.fixture;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Disposable client resource/rendering fixture; no production registration. */
@Mod(value="essence_achievement_presentation_test", dist=net.neoforged.api.distmarker.Dist.CLIENT)
public final class AchievementPresentationMod {
    public AchievementPresentationMod(IEventBus bus) { bus.addListener(this::setup); }
    private void setup(FMLClientSetupEvent event) {
        event.enqueueWork(com.mistaboom.essence_ascendance.achievement.AchievementPresentationNativeFixture::init);
    }
}
