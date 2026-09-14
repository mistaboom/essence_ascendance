package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;

/** Outermost server-native hit, including spawn immunity/PvP rejection before Player.hurt. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerGuardDamageMixin {
    @WrapMethod(method = "hurt")
    private boolean essenceAscendance$guardOutcome(DamageSource source, float amount, Operation<Boolean> original) {
        ServerPlayer player = (ServerPlayer)(Object)this;
        EquipmentDamageService.beginDamage(player, source, amount);
        boolean completed = false, accepted = false;
        try { accepted = original.call(source, amount); completed = true; return accepted; }
        finally { EquipmentDamageService.endDamage(player, source, completed, accepted); }
    }
}
