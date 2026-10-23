package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.skill.effect.GuardCounterattackService;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
//? if >=26.1.2
/*import net.minecraft.network.protocol.game.ServerboundAttackPacket;*/
//? if >=26.1.2
/*import net.minecraft.world.item.ItemStack;*/
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Extend the native packet distance gate for attack actions only; native interaction reach is unchanged. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RiposteReachConnectionMixin {
    //? if >=26.1.2
    /*@WrapOperation(method = "handleAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isWithinAttackRange(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/AABB;D)Z"), require = 1)*/
    //? if <26.1.2
    @WrapOperation(method = "handleInteract", at = @At(value = "INVOKE",
            //? if <26.1.2
            target = "Lnet/minecraft/server/level/ServerPlayer;canInteractWithEntity(Lnet/minecraft/world/phys/AABB;D)Z"), require = 1)
    //? if >=26.1.2
    /*private boolean essenceAscendance$counterattackReach(ServerPlayer player, ItemStack stack, AABB bounds, double padding, Operation<Boolean> original, ServerboundAttackPacket packet) {*/
    //? if <26.1.2
    private boolean essenceAscendance$counterattackReach(ServerPlayer player, AABB bounds, double padding,
                                                         //? if <26.1.2
                                                         Operation<Boolean> original, ServerboundInteractPacket packet) {
        //? if >=26.1.2
        /*boolean nativeAccepted = original.call(player, stack, bounds, padding);*/
        //? if <26.1.2
        boolean nativeAccepted = original.call(player, bounds, padding);
        //? if <26.1.2
        if (!GuardCounterattackService.isAttack(packet)) return nativeAccepted;
        //? if >=26.1.2
        /*Entity target = player.serverLevel().getEntityOrPart(packet.entityId());*/
        //? if <26.1.2
        Entity target = packet.getTarget(player.serverLevel());
        // When a pending counterattack exists, even vanilla's latency padding cannot extend its exact reach/LOS.
        if (target instanceof net.minecraft.world.entity.LivingEntity && GuardCounterattackService.bonusReach(player) > 0)
            return GuardCounterattackService.canReach(player, target);
        return nativeAccepted;
    }
}
