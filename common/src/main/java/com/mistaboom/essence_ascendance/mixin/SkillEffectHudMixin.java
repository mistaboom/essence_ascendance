package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.SkillEffectHudOverlay;
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

@Mixin(Gui.class)
abstract class SkillEffectHudMixin {
    //? if >=26.1.2
    /*@Inject(method = "extractRenderState", at = @At("TAIL"))*/
    //? if <26.1.2
    @Inject(method = "render", at = @At("TAIL"))
    //? if >=26.1.2
    /*private void essenceAscendance$renderSkillEffectHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,*/
    //? if <26.1.2
    private void essenceAscendance$renderSkillEffectHud(GuiGraphics graphics, DeltaTracker deltaTracker,
                                                         CallbackInfo callback) {
        SkillEffectHudOverlay.render(graphics);
        com.mistaboom.essence_ascendance.client.AscensionAnimation.render(graphics);
    }
}
