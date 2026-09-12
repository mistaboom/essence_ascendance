package com.mistaboom.essence_ascendance.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** Execution-time checks shared by machine menus; ownership stays on each machine. */
public final class ServerMenuAccess {
    private static final double VANILLA_MENU_DISTANCE = 8.0D;
    private static final double BLOCK_CENTER_TOLERANCE = 1.5D;

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
        Level level = machine.getLevel();
        return level != null && isLoaded(machine)
                && canReach(player, level, machine.getBlockPos());
    }

    /**
     * Menu lifetime validation follows the player's live block-interaction
     * range while retaining vanilla's longstanding eight-block container
     * allowance. The small center tolerance covers the difference between a
     * ray hit on the block face and this center-point distance check.
     */
    public static boolean canReach(Player player, Level level, BlockPos pos) {
        if (!player.isAlive() || player.isSpectator() || player.level() != level
                || !level.hasChunkAt(pos)) return false;
        double maximum = Math.max(VANILLA_MENU_DISTANCE,
                player.blockInteractionRange() + BLOCK_CENTER_TOLERANCE);
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= maximum * maximum;
    }
}
