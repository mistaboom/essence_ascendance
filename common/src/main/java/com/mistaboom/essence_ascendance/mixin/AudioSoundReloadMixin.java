package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.audio.AudioRuntime;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A resource epoch begins only after native sound definitions and the audio engine have committed. */
@Mixin(SoundManager.class)
abstract class AudioSoundReloadMixin {
    // Select the typed implementation, not its generic bridge, so each commit invalidates exactly once.
    @Inject(method="apply(Lnet/minecraft/client/sounds/SoundManager$Preparations;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",at=@At("TAIL"))
    private void essenceAscendance$soundResourcesCommitted(CallbackInfo ci) {
        AudioRuntime.resourcesReloaded();
    }
}
