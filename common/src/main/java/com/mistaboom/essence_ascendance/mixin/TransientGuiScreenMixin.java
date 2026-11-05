package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.transientfx.TransientGuiVisuals;
//? if >=26.1.2
/*import net.minecraft.client.gui.GuiGraphicsExtractor;*/
//? if <26.1.2
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One generic screen tail keeps transient GUI effects independent of concrete screen classes. */
@Mixin(Screen.class)
public abstract class TransientGuiScreenMixin {
    //? if >=26.1.2 {
    /*@Inject(method="extractRenderStateWithTooltipAndSubtitles",at=@At("HEAD"))
    private void essenceAscendance$captureCaptionFrame(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick,CallbackInfo ci) {
        com.mistaboom.essence_ascendance.client.audio.CaptionRouter.captureFrame(graphics);
    }
    *///?} else {
    @Inject(method="renderWithTooltip",at=@At("HEAD"))
    private void essenceAscendance$captureCaptionFrame(GuiGraphics graphics,int mouseX,int mouseY,float partialTick,CallbackInfo ci) {
        com.mistaboom.essence_ascendance.client.audio.CaptionRouter.screenStarted();
    }
    //?}
    //? if >=26.1.2
    /*@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))*/
    //? if <26.1.2
    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void essenceAscendance$renderTransientGuiEffects(
            //? if >=26.1.2
            /*GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {*/
            //? if <26.1.2
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        TransientGuiVisuals.renderScreen(graphics);
        //? if <26.1.2
        com.mistaboom.essence_ascendance.client.audio.CaptionRouter.render(graphics);
    }
}
