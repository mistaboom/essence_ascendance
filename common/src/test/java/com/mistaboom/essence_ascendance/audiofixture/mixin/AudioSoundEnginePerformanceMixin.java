package com.mistaboom.essence_ascendance.audiofixture.mixin;

import com.mistaboom.essence_ascendance.audiofixture.AudioNativePerformanceAcceptance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;

/** Queries actual native channel state on Minecraft's own audio executor, once per second. */
@Mixin(SoundEngine.class)
public abstract class AudioSoundEnginePerformanceMixin {
    @Shadow @Final private Map<SoundInstance,ChannelAccess.ChannelHandle> instanceToChannel;
    @Shadow @Final private SoundEngineExecutor executor;
    @Shadow @Final private com.mojang.blaze3d.audio.Library library;
    @Inject(method="tick",at=@At("RETURN")) private void observeChannels(boolean paused,CallbackInfo ci) { AudioNativePerformanceAcceptance.observeNativeChannels(instanceToChannel,executor,library); }
}
