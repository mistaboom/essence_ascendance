package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class AttunementMovementMixin {
    @Shadow public ServerPlayer player;
    @WrapMethod(method = "handleContainerClick")
    private void essenceAscendance$nativeMenuRequest(ServerboundContainerClickPacket packet, Operation<Void> original) {
        if (!player.server.isSameThread()) { original.call(packet); return; }
        boolean current = packet.getContainerId() == player.containerMenu.containerId
                && packet.getStateId() == player.containerMenu.getStateId();
        AttunementGameplay.withMenuRequest(current, () -> original.call(packet));
    }
    @WrapMethod(method = "handleMovePlayer")
    private void essenceAscendance$acceptedMovement(ServerboundMovePlayerPacket packet, Operation<Void> original) {
        Vec3 before = player.position();
        String dimension = player.level().dimension().location().toString();
        if (!player.server.isSameThread()) { original.call(packet); return; }
        var scope = AttunementGameplay.beginExertion(player, "movement");
        try {
            original.call(packet);
            AttunementGameplay.moved(player, before, dimension);
        } finally { AttunementGameplay.endExertion(scope); }
    }
}
