package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.AscensionAnimation;
import com.mistaboom.essence_ascendance.client.transientfx.TransientWorldVisuals;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class TransientVisualClientMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void essenceAscendance$transientLifecycle(CallbackInfo ci) {
        TransientWorldVisuals.tick();
        com.mistaboom.essence_ascendance.client.transientfx.TransientGuiVisuals.tick();
        AscensionAnimation.tick();
    }
}
