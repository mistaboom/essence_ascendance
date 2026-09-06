package com.mistaboom.essence_ascendance.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** Execution-time checks shared by machine menus; ownership stays on each machine. */
public final class ServerMenuAccess {
    private ServerMenuAccess() {
    }

    public static boolean isCurrent(Player player, AbstractContainerMenu menu) {
        return player instanceof ServerPlayer serverPlayer
                && serverPlayer.server.isSameThread()
                && serverPlayer.server.getPlayerList().getPlayer(player.getUUID()) == player
                && player.isAlive()
                && !player.isSpectator()
                && player.containerMenu == menu
                && menu.stillValid(player);
    }

    /** Do not resolve a removed/replaced block or load its chunk to validate access. */
    public static boolean isLoaded(BlockEntity machine) {
        Level level = machine.getLevel();
        return level != null
                && !machine.isRemoved()
                && level.hasChunkAt(machine.getBlockPos())
                && level.getBlockEntity(machine.getBlockPos()) == machine;
    }

    public static boolean canReach(Player player, BlockEntity machine) {
        return player.isAlive()
                && !player.isSpectator()
                && player.level() == machine.getLevel()
                && isLoaded(machine)
                && player.distanceToSqr(Vec3.atCenterOf(machine.getBlockPos())) <= 64.0D;
    }
}
