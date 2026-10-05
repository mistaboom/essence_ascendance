package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.mistaboom.essence_ascendance.balance.generated.StableRuntimePackResources;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional exact-version repair; no optional class or field is linked by this mixin. */
@Pseudo
@Mixin(targets = "plus.dragons.createdragonsplus.data.runtime.RuntimePackResources", remap = false)
public abstract class RuntimePackResourcesMixin {
    @Inject(method = "<init>(Ljava/lang/String;Lnet/neoforged/fml/ModContainer;Lnet/minecraft/server/packs/PackType;Lnet/minecraft/server/packs/repository/Pack$Position;Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;)V",
            at = @At("RETURN"), require = 0, remap = false)
    private void essenceAscendance$synchronizeRuntimeResources(CallbackInfo callback) {
        ModList mods = ModList.get();
        if (mods == null) return;
        String version = mods.getModContainerById("create_dragons_plus")
                .map(container -> container.getModInfo().getVersion().toString()).orElse("");
        StableRuntimePackResources.prepare(this, version);
    }
}
