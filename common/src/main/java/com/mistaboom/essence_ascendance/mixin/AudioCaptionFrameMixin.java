package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.audio.CaptionRouter;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native 26 subtitles are deferred beyond Gui/Screen extraction. Observe their real final frame order. */
@Mixin(GameRenderer.class)
abstract class AudioCaptionFrameMixin {
    //? if >=26.1.2
    /*@Inject(method="extractGui",at=@At("HEAD"))*/
    //? if <26.1.2
    @Inject(method="render",at=@At("HEAD"))
    private void essenceAscendance$beginAudioFrame(CallbackInfo ci) { CaptionRouter.beginFrame(); }

    //? if <26.1.2 {
    @Inject(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",shift=At.Shift.AFTER))
    private void essenceAscendance$finishAudioHud(CallbackInfo ci) {
        if(net.minecraft.client.Minecraft.getInstance().screen==null) CaptionRouter.finishFrame();
    }
    //?}

    //? if >=26.1.2 {
    /*@Inject(method="extractGui",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/Gui;extractDeferredSubtitles()V",shift=At.Shift.AFTER))
    private void essenceAscendance$finishAudioFrame(CallbackInfo ci) { CaptionRouter.finishFrame(); }
    *///?}
}
