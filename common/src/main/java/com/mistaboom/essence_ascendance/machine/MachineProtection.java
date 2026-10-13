package com.mistaboom.essence_ascendance.machine;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;

/** Lossless physical removal custody; ownership never blocks vanilla breaking. */
public final class MachineProtection {
    private MachineProtection() {}
    /** Vanilla and other protection mods decide whether a block may be broken. */
    public static void init() { }
    public static boolean canBreak(BoundMachine machine, Player player) {
        return com.mistaboom.essence_ascendance.network.ServerMenuAccess.canReach(player, (BlockEntity) machine);
    }
    /** One physical pickup retains inventory and paid progress, with fresh ownership on placement. */
    public static ItemStack takePickup(BlockEntity entity) {
        if (!(entity instanceof BoundMachine machine) || entity.getLevel() == null
                || entity.getLevel().isClientSide) return ItemStack.EMPTY;
        ItemStack pickup = new ItemStack(entity.getBlockState().getBlock());
        var data = entity.saveWithFullMetadata(entity.getLevel().registryAccess());
        data.putBoolean("processing_enabled", false);
        data.remove("machine_binding"); data.remove("machine_link");
        data.remove("owner"); data.remove("owner_name");
        pickup.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data));
        if (!machine.machineBinding().claimPickup()) return ItemStack.EMPTY;
        // Snapshot publication owns all contents now. Empty the removed object to defeat secondary drop hooks.
        if (entity instanceof EssenceInfuserBlockEntity infuser) infuser.releasePickupContents();
        else if (entity instanceof Container container)
            for (int slot = 0; slot < container.getContainerSize(); slot++) container.removeItemNoUpdate(slot);
        return pickup;
    }
}
