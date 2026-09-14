package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.skill.effect.GuardCounterattackService;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Extend the native packet distance gate for attack actions only; native interaction reach is unchanged. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RiposteReachConnectionMixin {
    @WrapOperation(method = "handleInteract", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;canInteractWithEntity(Lnet/minecraft/world/phys/AABB;D)Z"), require = 1)
    private boolean essenceAscendance$counterattackReach(ServerPlayer player, AABB bounds, double padding,
                                                         Operation<Boolean> original, ServerboundInteractPacket packet) {
        boolean nativeAccepted = original.call(player, bounds, padding);
        if (!GuardCounterattackService.isAttack(packet)) return nativeAccepted;
        Entity target = packet.getTarget(player.serverLevel());
        // When a pending counterattack exists, even vanilla's latency padding cannot extend its exact reach/LOS.
        if (target instanceof net.minecraft.world.entity.LivingEntity && GuardCounterattackService.bonusReach(player) > 0)
            return GuardCounterattackService.canReach(player, target);
        return nativeAccepted;
    }
}
