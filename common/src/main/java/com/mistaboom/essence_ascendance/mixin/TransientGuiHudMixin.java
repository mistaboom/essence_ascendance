package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.transientfx.TransientGuiVisuals;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
//? if >=26.1.2
/*import net.minecraft.client.gui.GuiGraphicsExtractor;*/
//? if <26.1.2
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** HUD-scope GUI recipes share the same lifecycle and recipe path as screen effects. */
@Mixin(Gui.class)
public abstract class TransientGuiHudMixin {
    //? if >=26.1.2
    /*@Inject(method="extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",at=@At("HEAD"))*/
    //? if <26.1.2
    @Inject(method="render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",at=@At("HEAD"))
    private void essenceAscendance$beginCaptionFrame(
            //? if >=26.1.2
            /*GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {*/
            //? if <26.1.2
            GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        com.mistaboom.essence_ascendance.client.audio.CaptionRouter.captureFrame(graphics);
    }
    //? if >=26.1.2
    /*@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",*/
    //? if <26.1.2
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("TAIL"))
    private void essenceAscendance$renderTransientHudEffects(
            //? if >=26.1.2
            /*GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {*/
            //? if <26.1.2
            GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        TransientGuiVisuals.renderHud(graphics);
    }

    //? if >=26.1.2 {
    /*@org.spongepowered.asm.mixin.injection.ModifyArgs(method="extractOverlayMessage",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"))
    private void essenceAscendance$reserveActionBar(org.spongepowered.asm.mixin.injection.invoke.arg.Args args,GuiGraphicsExtractor graphics,DeltaTracker delta) {
        var pose=graphics.pose(); int x=(int)Math.floor(pose.m20()),y=(int)Math.floor(pose.m21());
    *///?} else {
    @org.spongepowered.asm.mixin.injection.ModifyArgs(method="renderOverlayMessage",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;drawStringWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)I"))
    private void essenceAscendance$reserveActionBar(org.spongepowered.asm.mixin.injection.invoke.arg.Args args,GuiGraphics graphics,DeltaTracker delta) {
        var pose=graphics.pose().last().pose(); int x=(int)Math.floor(pose.m30()),y=(int)Math.floor(pose.m31());
    //?}
        int left=args.get(2),top=args.get(3),width=args.get(4);
        net.minecraft.client.gui.Font font=args.get(0);
        com.mistaboom.essence_ascendance.client.audio.CaptionRouter.reserve(new com.mistaboom.essence_ascendance.client.ui.UiBounds(x+left-2,y+top-2,width+4,font.lineHeight+4));
    }
}
