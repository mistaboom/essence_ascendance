package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.skill.effect.ImmobilizationController;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Native server validation still receives movement and rotation; rooted client prediction receives a correction. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ImmobilizedPlayerConnectionMixin {
    @Shadow public ServerPlayer player;
    @Shadow private Vec3 awaitingPositionFromClient;
    @WrapMethod(method = "handleMovePlayer")
    private void essenceAscendance$rootPlayerTranslation(ServerboundMovePlayerPacket packet, Operation<Void> original) {
        if (!player.server.isSameThread() || !ImmobilizationController.active(player)) { original.call(packet); return; }
        double x = player.getX(), y = player.getY(), z = player.getZ();
        if (!Double.isFinite(packet.getX(x)) || !Double.isFinite(packet.getY(y)) || !Double.isFinite(packet.getZ(z))
                || !Float.isFinite(packet.getYRot(player.getYRot())) || !Float.isFinite(packet.getXRot(player.getXRot()))) {
            original.call(packet); return;
        }
        double tolerance = ImmobilizationController.tolerance(player);
        boolean correction = Math.abs(packet.getX(x) - x) > tolerance || Math.abs(packet.getZ(z) - z) > tolerance
                || packet.getY(y) > y + tolerance;
        original.call(new ServerboundMovePlayerPacket.PosRot(x, Math.min(y, packet.getY(y)), z,
                packet.getYRot(player.getYRot()), packet.getXRot(player.getXRot()), packet.isOnGround()));
        ImmobilizationController.moved(player);
        if (correction && awaitingPositionFromClient == null && ImmobilizationController.active(player)) player.connection.teleport(
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }
}
