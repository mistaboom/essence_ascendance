package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.mistaboom.essence_ascendance.client.SkillEffectHudOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class SkillEffectHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void essenceAscendance$renderSkillEffectHud(GuiGraphics graphics, DeltaTracker deltaTracker,
                                                         CallbackInfo callback) {
        SkillEffectHudOverlay.render(graphics);
        com.mistaboom.essence_ascendance.client.AscensionAnimation.render(graphics);
    }
}
