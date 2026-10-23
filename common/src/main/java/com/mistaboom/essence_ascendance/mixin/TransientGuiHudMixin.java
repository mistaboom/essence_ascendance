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
}
