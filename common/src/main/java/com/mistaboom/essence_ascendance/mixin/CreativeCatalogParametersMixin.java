package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.item.CreativeVariantRegistry;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A catalog change invalidates every native parameter cache, including per-tab caches. */
@Mixin(CreativeModeTab.ItemDisplayParameters.class)
public abstract class CreativeCatalogParametersMixin {
    @Unique private long essenceAscendance$catalogRevision;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void essenceAscendance$captureCatalog(CallbackInfo callback) {
        essenceAscendance$catalogRevision = CreativeVariantRegistry.revision();
    }

    @Inject(method = "needsUpdate", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$catalogChanged(CallbackInfoReturnable<Boolean> callback) {
        if (essenceAscendance$catalogRevision != CreativeVariantRegistry.revision()) callback.setReturnValue(true);
    }
}
