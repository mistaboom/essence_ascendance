package com.mistaboom.essence_ascendance.fabric.mixin;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Keeps Minecraft's own ready indicator visible for fast attacks from any source. */
@Mixin(Gui.class)
abstract class VanillaAttackIndicatorMixin {
    /**
     * Vanilla hides the fully charged sprite when the attack delay is <= 5 ticks
     * (attack speed >= 4). Remove only that rendering threshold. The real attack
     * delay, charge calculation, living/alive target checks, sprite, and options
     * remain vanilla; no client attributes or gameplay timers are changed.
     */
    @ModifyConstant(method = "renderCrosshair", constant = @Constant(floatValue = 5.0F),
            require = 1, expect = 1, allow = 1)
    private float essenceAscendance$allowFastAttackReadyIndicator(float minimumDelay) {
        return 0.0F;
    }
}
