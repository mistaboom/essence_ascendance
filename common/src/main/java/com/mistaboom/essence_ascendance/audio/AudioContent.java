package com.mistaboom.essence_ascendance.audio;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import java.util.*;

/** Registration has no client dependencies. Cue IDs come exclusively from the generated catalog. */
public final class AudioContent {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(EssenceAscendance.MOD_ID, Registries.SOUND_EVENT);
    private static final Map<ResourceLocation, RegistrySupplier<SoundEvent>> EVENTS = new HashMap<>();
    private static boolean initialized;
    private AudioContent() { }
    public static void init() {
        if (initialized) return;
        for (var cue : AudioCatalog.get().cues()) EVENTS.put(cue.id(), SOUNDS.register(cue.id(), () -> SoundEvent.createVariableRangeEvent(cue.id())));
        SOUNDS.register(); initialized = true;
    }
    public static SoundEvent event(AudioCatalog.Cue cue) { return EVENTS.get(cue.id()).get(); }
}
