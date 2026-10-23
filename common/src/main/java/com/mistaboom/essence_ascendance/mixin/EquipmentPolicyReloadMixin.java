package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.equipment.TwoHandedEquipmentPolicy;
import net.minecraft.server.ReloadableServerResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReloadableServerResources.class)
abstract class EquipmentPolicyReloadMixin {
    //? if >=26.1.2
    /*@Inject(method = "updateComponentsAndStaticRegistryTags()V", at = @At("RETURN"))*/
    //? if <26.1.2
    @Inject(method = "updateRegistryTags()V", at = @At("RETURN"))
    private void essenceAscendance$tags(CallbackInfo ci) { TwoHandedEquipmentPolicy.rulesChanged(); }
}
