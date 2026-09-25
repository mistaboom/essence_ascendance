package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.ore.LatentOreModels;
import com.mistaboom.essence_ascendance.client.ore.LatentOreParticles;
import net.minecraft.client.resources.model.ModelManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelManager.class)
abstract class LatentOreModelReloadMixin {
    @Inject(method = "apply", at = @At("TAIL"))
    private void essence$discardOldAtlasReferences(CallbackInfo ci) {
        LatentOreModels.invalidate();
        LatentOreParticles.clear();
    }
}
