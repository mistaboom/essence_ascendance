package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.equipment.WeaponActionRuntime;
import com.mistaboom.essence_ascendance.equipment.TwoHandedEquipmentService;
import net.minecraft.server.level.ServerPlayer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Both logical sides reject native damage for all profile-declared action weapons. */
@Mixin(Player.class)
abstract class WeaponActionAttackMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$requireActionRuntime(Entity target, CallbackInfo ci) {
        if ((Object)this instanceof ServerPlayer player) {
            var d = TwoHandedEquipmentService.decision(player);
            if (d.actionWeapon()) { WeaponActionRuntime.attempt(player, false); ci.cancel(); return; }
            if (!TwoHandedEquipmentService.validateAttack(player)) { ci.cancel(); return; }
            return; // Server capabilities and compatibility are already cached.
        }
        if (!WeaponActionRuntime.allowsNativeAttack(((Player) (Object) this).getMainHandItem())) ci.cancel();
    }

    @WrapMethod(method = "attack")
    private void essenceAscendance$combatScope(Entity target, Operation<Void> original) {
        TwoHandedEquipmentService.enterCombat();
        try { original.call(target); } finally { TwoHandedEquipmentService.exitCombat(); }
    }
}
