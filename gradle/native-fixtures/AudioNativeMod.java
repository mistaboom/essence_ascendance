package com.mistaboom.essence_ascendance.audiofixture;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

/** Compiled only into the explicit isolated audio fixture. */
@Mod("essence_audio_test")
public final class AudioNativeMod {
    public AudioNativeMod(IEventBus bus) {
        bus.addListener((FMLLoadCompleteEvent event)->event.enqueueWork(()->{
            try {
                Class.forName("com.mistaboom.essence_ascendance.audiofixture.AudioLoaderTest")
                        .getMethod(Boolean.getBoolean("essence.audio.fixtureClient")?"clientAndExit":"serverAndExit").invoke(null);
            } catch(Throwable failure) { failure.printStackTrace(); System.exit(1); }
        }));
    }
}
