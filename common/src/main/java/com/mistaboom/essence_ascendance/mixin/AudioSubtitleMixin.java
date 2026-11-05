package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.audio.*;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import com.mistaboom.essence_ascendance.client.ui.UiBounds;
//? if >=26.1.2
/*import net.minecraft.client.gui.GuiGraphicsExtractor;*/
//? if <26.1.2
import net.minecraft.client.gui.GuiGraphics;

@Mixin(SubtitleOverlay.class)
abstract class AudioSubtitleMixin {
    // Read the actual native backdrop, after its own filtering and transforms. Never change its pose or args.
    //? if >=26.1.2 {
    /*@ModifyArgs(method="extractRenderState",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void essenceAscendance$observeNativeBounds(Args args,GuiGraphicsExtractor graphics) {
        var pose=graphics.pose(); int x=(int)Math.floor(pose.m20()),y=(int)Math.floor(pose.m21());
    *///?} else {
    @ModifyArgs(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void essenceAscendance$observeNativeBounds(Args args,GuiGraphics graphics) {
        var pose=graphics.pose().last().pose(); int x=(int)Math.floor(pose.m30()),y=(int)Math.floor(pose.m31());
    //?}
        int left=args.get(0),top=args.get(1),right=args.get(2),bottom=args.get(3);
        CaptionRouter.nativeBounds(graphics.guiWidth(),graphics.guiHeight(),new UiBounds(x+left,y+top,right-left,bottom-top));
    }

    @Inject(method="onPlaySound",at=@At("HEAD"),cancellable=true)
    private void essenceAscendance$observeCaption(SoundInstance sound,WeighedSoundEvents event,float range,CallbackInfo ci) {
        if(sound instanceof NativeAudio.Instance instance && !CaptionRouter.nativeSubtitle(instance,event.getSubtitle(),range)) ci.cancel();
    }
    // Observe the Component draw, after native preference/range/expiry filtering. Arrows are String draws.
    //? if >=26.1.2
    /*@ModifyArg(method="extractRenderState",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"),index=1)*/
    //? if <26.1.2
    @ModifyArg(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"),index=1)
    private Component essenceAscendance$nativeTextDrawn(Component text) { return CaptionRouter.nativeRendered(text); }
}
