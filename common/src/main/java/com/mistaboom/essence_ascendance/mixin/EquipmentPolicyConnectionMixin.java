package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.equipment.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;

/** Both loaders share the native packet ownership boundary. Netty dispatch runs before any mutation scope. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class EquipmentPolicyConnectionMixin {
    @Shadow public ServerPlayer player;
    @WrapMethod(method="handleMovePlayer")
    private void essenceAscendance$observeAim(ServerboundMovePlayerPacket packet,Operation<Void> original) {
        original.call(packet);
        if (player.server.isSameThread()) WeaponActionRuntime.observeAim(player);
    }
    @WrapMethod(method = "handleAnimate")
    private void essenceAscendance$swing(ServerboundSwingPacket packet, Operation<Void> original) {
        if (!player.server.isSameThread()) { original.call(packet); return; }
        if (WeaponActionRuntime.consumesWorldInput(player)) {
            // Interaction animations share this packet. Actual glaive attacks have explicit input packets.
            return;
        }
        if (packet.getHand() == InteractionHand.OFF_HAND && !TwoHandedEquipmentService.permitsOffhandUse(player)) return;
        if (!TwoHandedEquipmentService.validateAttack(player)) return;
        original.call(packet);
    }
    @WrapMethod(method = "handleSetCarriedItem")
    private void essenceAscendance$handleSetCarriedItem(ServerboundSetCarriedItemPacket packet, Operation<Void> original) {
        if (!player.server.isSameThread()) { original.call(packet); return; }
        TwoHandedEquipmentService.begin(player);
        try { original.call(packet); } finally { TwoHandedEquipmentService.end(player); }
    }
    //? if <26.1.2
    @WrapMethod(method = "handlePickItem")
    //? if <26.1.2
    private void essenceAscendance$handlePickItem(ServerboundPickItemPacket packet, Operation<Void> original) {
        //? if <26.1.2
        if (!player.server.isSameThread()) { original.call(packet); return; }
        //? if <26.1.2
        TwoHandedEquipmentService.begin(player);
        //? if <26.1.2
        try { original.call(packet); } finally { TwoHandedEquipmentService.end(player); }
    //? if <26.1.2
    }
    //? if >=26.1.2
    /*@WrapMethod(method = "handlePickItemFromBlock")*/
    //? if >=26.1.2
    /*private void essenceAscendance$pickBlock(ServerboundPickItemFromBlockPacket packet, Operation<Void> original) {*/
        //? if >=26.1.2
        /*if (!player.serverLevel().getServer().isSameThread()) { original.call(packet); return; }*/
        //? if >=26.1.2
        /*TwoHandedEquipmentService.begin(player);*/
        //? if >=26.1.2
        /*try { original.call(packet); } finally { TwoHandedEquipmentService.end(player); }*/
    //? if >=26.1.2
    /*}*/
    //? if >=26.1.2
    /*@WrapMethod(method = "handlePickItemFromEntity")*/
    //? if >=26.1.2
    /*private void essenceAscendance$pickEntity(ServerboundPickItemFromEntityPacket packet, Operation<Void> original) {*/
        //? if >=26.1.2
        /*if (!player.serverLevel().getServer().isSameThread()) { original.call(packet); return; }*/
        //? if >=26.1.2
        /*TwoHandedEquipmentService.begin(player);*/
        //? if >=26.1.2
        /*try { original.call(packet); } finally { TwoHandedEquipmentService.end(player); }*/
    //? if >=26.1.2
    /*}*/
    @WrapMethod(method = "handleContainerClick")
    private void essenceAscendance$handleContainerClick(ServerboundContainerClickPacket packet, Operation<Void> original) {
        if (!player.server.isSameThread()) { original.call(packet); return; }
        TwoHandedEquipmentService.begin(player);
        try { original.call(packet); } finally { TwoHandedEquipmentService.end(player); }
    }
    @WrapMethod(method = "handleSetCreativeModeSlot")
    private void essenceAscendance$handleSetCreativeModeSlot(ServerboundSetCreativeModeSlotPacket packet, Operation<Void> original) {
        if (!player.server.isSameThread()) { original.call(packet); return; }
        TwoHandedEquipmentService.begin(player);
        try { original.call(packet); } finally { TwoHandedEquipmentService.end(player); }
    }
    @WrapMethod(method = "handlePlayerAction")
    private void essenceAscendance$action(ServerboundPlayerActionPacket packet, Operation<Void> original) {
        if (!player.server.isSameThread()) { original.call(packet); return; }
        var action = packet.getAction();
        if ((action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                || action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK
                || action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK)
                && WeaponActionRuntime.consumesWorldInput(player)) {
            if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) WeaponActionRuntime.attempt(player, false);
            player.connection.ackBlockChangesUpTo(packet.getSequence());
            var pos = packet.getPos();
            //? if >=26.1.2
            /*if (player.isWithinBlockInteractionRange(pos, 1) && player.level().hasChunkAt(pos))*/
            //? if <26.1.2
            if (player.canInteractWithBlock(pos, 1) && player.level().hasChunkAt(pos))
                player.connection.send(new ClientboundBlockUpdatePacket(player.level(), pos));
            return;
        }
        // In particular, F-key swaps pass through unchanged and resolve after both writes.
        TwoHandedEquipmentService.begin(player);
        try { original.call(packet); } finally { TwoHandedEquipmentService.end(player); }
    }
    @WrapMethod(method = "handleUseItem")
    private void essenceAscendance$use(ServerboundUseItemPacket packet, Operation<Void> original) {
        if (!player.server.isSameThread()) { original.call(packet); return; }
        boolean action = WeaponActionRuntime.consumesWorldInput(player);
        if (action || packet.getHand() == InteractionHand.OFF_HAND && !TwoHandedEquipmentService.permitsOffhandUse(player)) {
            player.connection.ackBlockChangesUpTo(packet.getSequence());
            if (action && packet.getHand() == InteractionHand.MAIN_HAND) WeaponActionRuntime.attempt(player, true);
            return;
        }
        original.call(packet);
    }
    @WrapMethod(method = "handleUseItemOn")
    private void essenceAscendance$useOn(ServerboundUseItemOnPacket packet, Operation<Void> original) {
        if (!player.server.isSameThread()) { original.call(packet); return; }
        if (packet.getHand() == InteractionHand.OFF_HAND && !TwoHandedEquipmentService.permitsOffhandUse(player)) {
            player.connection.ackBlockChangesUpTo(packet.getSequence());
            var pos = packet.getHitResult().getBlockPos();
            //? if >=26.1.2
            /*if (player.isWithinBlockInteractionRange(pos, 1) && player.level().hasChunkAt(pos))*/
            //? if <26.1.2
            if (player.canInteractWithBlock(pos, 1) && player.level().hasChunkAt(pos))
                player.connection.send(new ClientboundBlockUpdatePacket(player.level(), pos));
            return;
        }
        original.call(packet);
    }
    @WrapMethod(method = "handleInteract")
    private void essenceAscendance$interact(ServerboundInteractPacket packet, Operation<Void> original) {
        if (!player.server.isSameThread()) { original.call(packet); return; }
        //? if >=26.1.2
        /*if (packet.hand() != InteractionHand.OFF_HAND || TwoHandedEquipmentService.permitsOffhandUse(player)) original.call(packet);*/
        //? if <26.1.2
        boolean[] consume = {false};
        //? if <26.1.2
        packet.dispatch(new ServerboundInteractPacket.Handler() {
            //? if <26.1.2
            public void onInteraction(InteractionHand hand) { use(hand); }
            //? if <26.1.2
            public void onInteraction(InteractionHand hand, Vec3 position) { use(hand); }
            //? if <26.1.2
            private void use(InteractionHand hand) {
                //? if <26.1.2
                consume[0] = hand == InteractionHand.OFF_HAND && !TwoHandedEquipmentService.permitsOffhandUse(player);
            //? if <26.1.2
            }
            //? if <26.1.2
            public void onAttack() {
                //? if <26.1.2
                if (WeaponActionRuntime.consumesWorldInput(player)) { consume[0] = true; WeaponActionRuntime.attempt(player, false); }
            //? if <26.1.2
            }
        //? if <26.1.2
        });
        //? if <26.1.2
        if (!consume[0]) original.call(packet);
    }
    //? if >=26.1.2
    /*@WrapMethod(method = "handleAttack")*/
    //? if >=26.1.2
    /*private void essenceAscendance$attack(ServerboundAttackPacket packet, Operation<Void> original) {*/
        //? if >=26.1.2
        /*if (!player.serverLevel().getServer().isSameThread()) { original.call(packet); return; }*/
        //? if >=26.1.2
        /*if (WeaponActionRuntime.consumesWorldInput(player)) { WeaponActionRuntime.attempt(player, false); return; }*/
        //? if >=26.1.2
        /*original.call(packet);*/
    //? if >=26.1.2
    /*}*/
}
