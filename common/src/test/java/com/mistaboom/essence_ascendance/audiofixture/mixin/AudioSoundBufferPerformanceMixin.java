package com.mistaboom.essence_ascendance.audiofixture.mixin;

import com.mistaboom.essence_ascendance.audiofixture.AudioNativePerformanceAcceptance;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;

/** Observes successful real Vorbis decode futures; does not request or replace any buffer. */
@Mixin(SoundBufferLibrary.class)
public abstract class AudioSoundBufferPerformanceMixin {
    @Inject(method="getCompleteBuffer",at=@At("RETURN"))
    private void observeDecodedBuffer(ResourceLocation path,CallbackInfoReturnable<CompletableFuture<SoundBuffer>> ci) { AudioNativePerformanceAcceptance.decoded(path.toString(),ci.getReturnValue()); }
    // These version selectors name the same native decode-worker operation; measurement policy is shared.
    //? if >=26.1.2
    /*@Inject(method="lambda$getCompleteBuffer$1",at=@At("HEAD"))*/
    //? if <26.1.2
    @Inject(method={"method_19747","lambda$getCompleteBuffer$0"},at=@At("HEAD"),require=1,allow=1)
    private void beforeNativeDecode(ResourceLocation path,CallbackInfoReturnable<SoundBuffer> ci) { AudioNativePerformanceAcceptance.decodeWorkerBegin(path.toString()); }
    //? if >=26.1.2
    /*@Inject(method="lambda$getCompleteBuffer$1",at=@At("RETURN"))*/
    //? if <26.1.2
    @Inject(method={"method_19747","lambda$getCompleteBuffer$0"},at=@At("RETURN"),require=1,allow=1)
    private void afterNativeDecode(ResourceLocation path,CallbackInfoReturnable<SoundBuffer> ci) { AudioNativePerformanceAcceptance.decodeWorkerEnd(); }
}
