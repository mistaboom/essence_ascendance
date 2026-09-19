package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.UtilitySenseClientState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class UtilitySenseClientMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void essenceAscendance$waylightVision(CallbackInfo ci) {
        UtilitySenseClientState.tickClient();
    }
}
