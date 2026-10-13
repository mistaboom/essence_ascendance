package com.mistaboom.essence_ascendance.team;

import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleContent;
import com.mistaboom.essence_ascendance.machine.BoundMachine;
import com.mistaboom.essence_ascendance.network.ServerMenuAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A genuinely slot-free interface. Outsiders never receive a machine or player inventory here. */
public final class TeamMenu extends AbstractContainerMenu {
    public enum Kind { TEAM, ACCESS }
    public final Kind kind;
    private final BoundMachine machine;
    private final long bindingRevision;
    public final int view;
    public int page;
    public TeamMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public TeamMenu(int id, Inventory inventory, BoundMachine machine) {
        this(id, inventory, machine, Kind.TEAM);
    }
    public TeamMenu(int id, Inventory inventory, BoundMachine machine, Kind kind) {
        super(switch (kind) {
            case TEAM -> EssenceCrucibleContent.TEAM_MENU.get();
            case ACCESS -> EssenceCrucibleContent.MACHINE_ACCESS_MENU.get();
        }, id);
        this.kind = kind;
        this.view = kind.ordinal();
        this.machine = machine;
        bindingRevision = machine == null ? -1 : machine.machineBinding().revision();
    }
    public BoundMachine machine() { return machine; }
    @Override public boolean stillValid(Player player) {
        // ACCESS exposes no slots. Membership/ownership changes should update its explanation and
        // invalidate action receipts, while keeping the same loaded physical device on screen.
        return machine == null || (kind == Kind.ACCESS || machine.machineBinding().revision() == bindingRevision)
                && ServerMenuAccess.canReach(player, (BlockEntity) machine);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public void clicked(int slot, int button, net.minecraft.world.inventory.ClickType type, Player player) { }
}
