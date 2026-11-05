package com.mistaboom.essence_ascendance.audiofixture.mixin;

import com.mistaboom.essence_ascendance.audio.AudioEngine;
import com.mistaboom.essence_ascendance.client.audio.AudioRuntime;
import com.mistaboom.essence_ascendance.audiofixture.AudioNativePerformanceAcceptance;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Timing/counter observers only; never alters production admission, discovery or source state. */
@Mixin(value=AudioRuntime.class,remap=false)
public abstract class AudioRuntimePerformanceMixin {
    @Shadow @Final private static AudioEngine ENGINE;
    @Inject(method="tick",at=@At("HEAD"),remap=false) private static void beforeTick(CallbackInfo ci) { AudioNativePerformanceAcceptance.runtimeBegin(ENGINE); }
    @Inject(method="tick",at=@At("RETURN"),remap=false) private static void afterTick(CallbackInfo ci) { AudioNativePerformanceAcceptance.runtimeEnd(); }
    @Inject(method="discoverMachines",at=@At("HEAD"),remap=false) private static void beforeMachines(CallbackInfo ci) { AudioNativePerformanceAcceptance.discoveryBegin(true); }
    @Inject(method="discoverMachines",at=@At("RETURN"),remap=false) private static void afterMachines(CallbackInfo ci) { AudioNativePerformanceAcceptance.discoveryEnd(true); }
    @Inject(method="discoverEntities",at=@At("HEAD"),remap=false) private static void beforeEntities(CallbackInfo ci) { AudioNativePerformanceAcceptance.discoveryBegin(false); }
    @Inject(method="discoverEntities",at=@At("RETURN"),remap=false) private static void afterEntities(CallbackInfo ci) { AudioNativePerformanceAcceptance.discoveryEnd(false); }
    @Inject(method="machineChanged",at=@At("HEAD"),remap=false) private static void offered(BlockEntity source,CallbackInfo ci) { AudioNativePerformanceAcceptance.machineOffered(source); }
}
