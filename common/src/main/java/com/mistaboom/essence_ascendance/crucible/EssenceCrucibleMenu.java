package com.mistaboom.essence_ascendance.crucible;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class EssenceCrucibleMenu extends AbstractContainerMenu {

    public static final int MACHINE_SLOT = 0;
    public static final int PLAYER_INVENTORY_START = 1;
    public static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 36;

    private final Container crucibleContainer;
    private final EssenceCrucibleBlockEntity serverCrucible;

    /* Client constructor used by MenuType. Server state arrives by S2C payload. */
    public EssenceCrucibleMenu(
            int containerId,
            Inventory playerInventory
    ) {
        this(
                containerId,
                playerInventory,
                new SimpleContainer(1),
                null
        );
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
                crucible
        );
    }

    private EssenceCrucibleMenu(
            int containerId,
            Inventory playerInventory,
            Container crucibleContainer,
            EssenceCrucibleBlockEntity serverCrucible
    ) {
        super(
                EssenceCrucibleContent.ESSENCE_CRUCIBLE_MENU.get(),
                containerId
        );

        this.crucibleContainer = crucibleContainer;
        this.serverCrucible = serverCrucible;

        crucibleContainer.startOpen(playerInventory.player);

        /* Centered machine slot in the top machine section. */
        addSlot(
                new Slot(
                        crucibleContainer,
                        MACHINE_SLOT,
                        141,
                        33
                ) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        /*
                         * The global item-mapping config is server authoritative.
                         * Client prediction must not reject a stack using a local
                         * mapping registry. The physical server menu performs the
                         * real mapping validation and corrects prediction.
                         *
                         * canPlaceItem() intentionally validates only the incoming
                         * stack, not the item already occupying the slot. That is
                         * what allows normal vanilla click-to-swap behavior.
                         */
                        return serverCrucible == null
                                || (EssenceCrucibleBlockEntity.isValidNewInput(stack)
                                    && crucibleContainer.canPlaceItem(MACHINE_SLOT, stack));
                    }
                }
        );

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

    public EssenceCrucibleBlockEntity serverCrucible() {
        return serverCrucible;
    }

    @Override
    public boolean stillValid(Player player) {
        return serverCrucible == null
                || serverCrucible.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(
            Player player,
            int index
    ) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();

        if (index == MACHINE_SLOT) {
            if (!moveItemStackTo(
                    source,
                    PLAYER_INVENTORY_START,
                    PLAYER_INVENTORY_END,
                    true
            )) {
                return ItemStack.EMPTY;
            }
        } else {
            if ((serverCrucible != null
                    && !EssenceCrucibleBlockEntity.isValidNewInput(source))
                    || !moveItemStackTo(
                            source,
                            MACHINE_SLOT,
                            MACHINE_SLOT + 1,
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
