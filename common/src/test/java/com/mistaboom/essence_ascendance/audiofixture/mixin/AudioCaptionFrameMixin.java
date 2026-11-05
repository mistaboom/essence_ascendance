package com.mistaboom.essence_ascendance.audiofixture.mixin;

import com.mistaboom.essence_ascendance.client.audio.CaptionRouter;
import com.mistaboom.essence_ascendance.audiofixture.AudioMultiplayerAcceptance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.network.chat.Component;

/** External fixture observer only: never invokes or reorders either production draw. */
@Mixin(value=CaptionRouter.class,remap=false)
public abstract class AudioCaptionFrameMixin {
    @Inject(method="beginFrame",at=@At("HEAD"),remap=false)
    private static void observeFrameStart(CallbackInfo callback) {
        com.mistaboom.essence_ascendance.audiofixture.AudioNativePerformanceAcceptance.frame();
        AudioMultiplayerAcceptance.actualFrameStart();
    }
    @Inject(method="nativeRendered",at=@At("HEAD"),remap=false)
    private static void observeNativeText(Component text,CallbackInfoReturnable<Component> callback) {
        AudioMultiplayerAcceptance.actualNativeText(text);
    }
    @Inject(method="render",at=@At("HEAD"),remap=false)
    private static void observeActualFrame(CallbackInfo callback) {
        AudioMultiplayerAcceptance.actualFrame();
    }
}
