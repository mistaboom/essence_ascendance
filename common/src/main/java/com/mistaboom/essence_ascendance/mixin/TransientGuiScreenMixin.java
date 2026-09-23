package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.transientfx.TransientGuiVisuals;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One generic screen tail keeps transient GUI effects independent of concrete screen classes. */
@Mixin(Screen.class)
public abstract class TransientGuiScreenMixin {
    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void essenceAscendance$renderTransientGuiEffects(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        TransientGuiVisuals.renderScreen(graphics);
    }
}
