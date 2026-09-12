package com.mistaboom.essence_ascendance.nexus;

import com.mistaboom.essence_ascendance.network.ServerMenuAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/**
 * Slotless menu context for the Ascendance Nexus.
 *
 * The Nexus stores no Essence or progression. The server-side menu only keeps
 * the block-use context alive so future progression action packets can verify
 * that the player still has a valid Nexus open.
 */
public final class AscendanceNexusMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;

    public AscendanceNexusMenu(
            int containerId,
            Inventory inventory
    ) {
        this(
                containerId,
                inventory,
                ContainerLevelAccess.NULL
        );
    }

    public AscendanceNexusMenu(
            int containerId,
            Inventory inventory,
            ContainerLevelAccess access
    ) {
        super(
                AscendanceNexusContent.ASCENDANCE_NEXUS_MENU.get(),
                containerId
        );
        this.access = access;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> ServerMenuAccess.canReach(player, level, pos)
                        && level.getBlockState(pos).is(AscendanceNexusContent.ASCENDANCE_NEXUS.get()),
                player.level().isClientSide);
    }

    public boolean isAt(Level level, BlockPos pos) {
        return access.evaluate((menuLevel, menuPos) -> menuLevel == level && menuPos.equals(pos), false);
    }

    @Override
    public ItemStack quickMoveStack(
            Player player,
            int index
    ) {
        return ItemStack.EMPTY;
    }
}
