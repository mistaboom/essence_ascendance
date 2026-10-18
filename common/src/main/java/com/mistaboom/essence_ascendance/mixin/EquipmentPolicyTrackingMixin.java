package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.network.TwoHandedPolicyPayload;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Share the same decision with newly tracking clients after their native spawn/equipment packets. */
@Mixin(ServerEntity.class)
abstract class EquipmentPolicyTrackingMixin {
    @Shadow @Final private Entity entity;
    @Inject(method = "addPairing", at = @At("TAIL"))
    private void essenceAscendance$track(ServerPlayer observer, CallbackInfo ci) {
        if (entity instanceof ServerPlayer owner) TwoHandedPolicyPayload.track(owner, observer);
        if (entity instanceof ServerPlayer owner) com.mistaboom.essence_ascendance.network.WeaponActionStatePayload.track(owner,observer);
    }
    @Inject(method = "removePairing", at = @At("TAIL"))
    private void essenceAscendance$untrack(ServerPlayer observer, CallbackInfo ci) {
        if (entity instanceof ServerPlayer owner) TwoHandedPolicyPayload.untrack(owner, observer);
        if (entity instanceof ServerPlayer owner) com.mistaboom.essence_ascendance.network.WeaponActionStatePayload.untrack(owner,observer);
    }
}
