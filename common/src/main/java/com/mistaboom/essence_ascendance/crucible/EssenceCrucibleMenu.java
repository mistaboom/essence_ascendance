package com.mistaboom.essence_ascendance.crucible;

import com.mistaboom.essence_ascendance.network.ServerMenuAccess;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class EssenceCrucibleMenu extends AbstractContainerMenu {

    public static final int MAX_MACHINE_SLOTS = EssenceCrucibleBlockEntity.MAX_INPUT_SLOTS;
    public static final int PLAYER_INVENTORY_START = MAX_MACHINE_SLOTS;
    public static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 36;

    /* Slot 0 stays exactly where the original single Crucible slot lived. */
    private static final int[] MACHINE_SLOT_X = {
            141, 123, 159, 105, 177, 87, 195, 69, 213
    };
    private static final int MACHINE_SLOT_Y = 33;

    private final Container crucibleContainer;
    private final EssenceCrucibleBlockEntity serverCrucible;
    private final SimpleContainerData menuData;

    /* Client constructor used by MenuType. Server state arrives by menu sync/S2C payload. */
    public EssenceCrucibleMenu(
            int containerId,
            Inventory playerInventory
    ) {
        this(
                containerId,
                playerInventory,
                new SimpleContainer(MAX_MACHINE_SLOTS),
                null,
                new SimpleContainerData(1)
        );
        menuData.set(0, 1);
    }

    public EssenceCrucibleMenu(
            int containerId,
            Inventory playerInventory,
            EssenceCrucibleBlockEntity crucible
    ) {
        this(
                containerId,
                playerInventory,
                crucible,
                crucible,
                new SimpleContainerData(1)
        );
        menuData.set(0, crucible.activeInputSlotCount());
    }

    private EssenceCrucibleMenu(
            int containerId,
            Inventory playerInventory,
            Container crucibleContainer,
            EssenceCrucibleBlockEntity serverCrucible,
            SimpleContainerData menuData
    ) {
        super(
                EssenceCrucibleContent.ESSENCE_CRUCIBLE_MENU.get(),
                containerId
        );

        this.crucibleContainer = crucibleContainer;
        this.serverCrucible = serverCrucible;
        this.menuData = menuData;

        crucibleContainer.startOpen(playerInventory.player);
        addDataSlots(menuData);

        for (int slotIndex = 0; slotIndex < MAX_MACHINE_SLOTS; slotIndex++) {
            final int machineSlot = slotIndex;
            addSlot(
                    new Slot(
                            crucibleContainer,
                            machineSlot,
                            machineSlotX(machineSlot),
                            MACHINE_SLOT_Y
                    ) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            if (!isMachineSlotCurrentlyAvailable(machineSlot)) {
                                return false;
                            }

                            /*
                             * The mapping config is server authoritative. The client
                             * may predict placement, but the real menu validates the
                             * mapping plus the one-distinct-item-type-per-slot rule.
                             */
                            return serverCrucible == null
                                    || crucibleContainer.canPlaceItem(machineSlot, stack);
                        }

                        @Override
                        public boolean isActive() {
                            /*
                             * An occupied lane stays visible/removable after pylons
                             * disappear, but it cannot accept or process new items
                             * until that lane becomes active again.
                             */
                            return isMachineSlotCurrentlyAvailable(machineSlot)
                                    || hasItem();
                        }
                    }
            );
        }

        /* Player main inventory: 3 rows x 9. */
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(
                        new Slot(
                                playerInventory,
                                column + row * 9 + 9,
                                69 + column * 18,
                                237 + row * 18
                        )
                );
            }
        }

        /* Player hotbar. */
        for (int column = 0; column < 9; column++) {
            addSlot(
                    new Slot(
                            playerInventory,
                            column,
                            69 + column * 18,
                            295
                    )
            );
        }
    }

    public static int machineSlotX(int slot) {
        if (slot < 0 || slot >= MACHINE_SLOT_X.length) {
            return MACHINE_SLOT_X[0];
        }
        return MACHINE_SLOT_X[slot];
    }

    public static int machineSlotY() {
        return MACHINE_SLOT_Y;
    }

    public EssenceCrucibleBlockEntity serverCrucible() {
        return serverCrucible;
    }

    public int activeMachineSlots() {
        if (serverCrucible != null) {
            return serverCrucible.activeInputSlotCount();
        }
        return Math.max(1, Math.min(MAX_MACHINE_SLOTS, menuData.get(0)));
    }

    public boolean isMachineSlotCurrentlyAvailable(int slot) {
        return slot >= 0 && slot < activeMachineSlots();
    }

    public boolean shouldRenderMachineSlot(int slot) {
        return isMachineSlotCurrentlyAvailable(slot)
                || (slot >= 0 && slot < MAX_MACHINE_SLOTS && getSlot(slot).hasItem());
    }

    @Override
    public void broadcastChanges() {
        if (serverCrucible != null) {
            menuData.set(0, serverCrucible.activeInputSlotCount());
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return serverCrucible == null
                ? player.level().isClientSide
                : serverCrucible.stillValid(player);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!player.level().isClientSide && !ServerMenuAccess.isCurrent(player, this)) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(
            Player player,
            int index
    ) {
        if ((!player.level().isClientSide && !ServerMenuAccess.isCurrent(player, this))
                || index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }


        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();

        if (index < MAX_MACHINE_SLOTS) {
            if (!moveItemStackTo(
                    source,
                    PLAYER_INVENTORY_START,
                    PLAYER_INVENTORY_END,
                    true
            )) {
                return ItemStack.EMPTY;
            }
        } else {
            int activeEnd = activeMachineSlots();
            if ((serverCrucible != null
                    && !EssenceCrucibleBlockEntity.isValidNewInput(source))
                    || !moveItemStackTo(
                            source,
                            0,
                            activeEnd,
                            false
                    )) {
                return ItemStack.EMPTY;
            }
        }

        if (source.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (source.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, source);
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        crucibleContainer.stopOpen(player);
    }
}
