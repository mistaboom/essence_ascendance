package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.equipment.WeaponActionRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The native attack indicator reads the shared recharge clock used to scale each accepted attack. */
@Mixin(Player.class)
abstract class WeaponActionReadinessMixin {
    @Inject(method="getAttackStrengthScale",at=@At("HEAD"),cancellable=true)
    private void essenceAscendance$readiness(float partialTick,CallbackInfoReturnable<Float> cir) {
        Player player=(Player)(Object)this;
        if (player instanceof ServerPlayer server) {
            if (!com.mistaboom.essence_ascendance.equipment.TwoHandedEquipmentService.decision(server).actionWeapon()) return;
            var state=WeaponActionRuntime.snapshot(server);
            if (state!=null) cir.setReturnValue(state.readiness(WeaponActionRuntime.now(server)+partialTick));
        } else if (WeaponActionRuntime.requiresActionRuntime(player.getMainHandItem())
                && com.mistaboom.essence_ascendance.network.WeaponActionStatePayload.clientState(player)!=null)
            cir.setReturnValue(com.mistaboom.essence_ascendance.network.WeaponActionStatePayload.readiness(player,partialTick));
    }
    @Inject(method="getCurrentItemAttackStrengthDelay",at=@At("HEAD"),cancellable=true)
    private void essenceAscendance$cadence(CallbackInfoReturnable<Float> cir) {
        Player player=(Player)(Object)this;
        if (player instanceof ServerPlayer server) {
            if (!com.mistaboom.essence_ascendance.equipment.TwoHandedEquipmentService.decision(server).actionWeapon()) return;
            var state=WeaponActionRuntime.snapshot(server);
            if (state!=null) cir.setReturnValue((float)(state.readyAt()-state.acceptedAt()));
        } else if (WeaponActionRuntime.requiresActionRuntime(player.getMainHandItem())) {
            var state=com.mistaboom.essence_ascendance.network.WeaponActionStatePayload.clientState(player);
            if (state!=null) cir.setReturnValue((float)(state.readyAt()-state.acceptedAt()));
        }
    }
}
