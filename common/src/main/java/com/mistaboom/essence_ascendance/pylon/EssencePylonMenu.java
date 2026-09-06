package com.mistaboom.essence_ascendance.pylon;

import com.mistaboom.essence_ascendance.network.ServerMenuAccess;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class EssencePylonMenu extends AbstractContainerMenu {

    public static final int FOCUS_SLOT = 0;
    public static final int PLAYER_INVENTORY_START = 1;
    public static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 36;

    private final Container pylonContainer;
    private final EssencePylonBlockEntity serverPylon;

    public EssencePylonMenu(
            int containerId,
            Inventory playerInventory
    ) {
        this(containerId, playerInventory, new SimpleContainer(1), null);
    }

    public EssencePylonMenu(
            int containerId,
            Inventory playerInventory,
            EssencePylonBlockEntity pylon
    ) {
        this(containerId, playerInventory, pylon, pylon);
    }

    private EssencePylonMenu(
            int containerId,
            Inventory playerInventory,
            Container pylonContainer,
            EssencePylonBlockEntity serverPylon
    ) {
        super(EssencePylonContent.ESSENCE_PYLON_MENU.get(), containerId);
        this.pylonContainer = pylonContainer;
        this.serverPylon = serverPylon;

        pylonContainer.startOpen(playerInventory.player);

        addSlot(
                new Slot(pylonContainer, FOCUS_SLOT, 107, 33) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return serverPylon == null
                                ? EssencePylonContent.isFocus(stack)
                                : pylonContainer.canPlaceItem(FOCUS_SLOT, stack);
                    }

                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                }
        );

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(
                        new Slot(
                                playerInventory,
                                column + row * 9 + 9,
                                35 + column * 18,
                                177 + row * 18
                        )
                );
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(
                    new Slot(
                            playerInventory,
                            column,
                            35 + column * 18,
                            235
                    )
            );
        }
    }

    public EssencePylonBlockEntity serverPylon() {
        return serverPylon;
    }

    @Override
    public boolean stillValid(Player player) {
        return serverPylon == null
                ? player.level().isClientSide
                : serverPylon.stillValid(player);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!player.level().isClientSide && !ServerMenuAccess.isCurrent(player, this)) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
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

        if (index == FOCUS_SLOT) {
            if (!moveItemStackTo(
                    source,
                    PLAYER_INVENTORY_START,
                    PLAYER_INVENTORY_END,
                    true
            )) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!EssencePylonContent.isFocus(source)
                    || !moveItemStackTo(source, FOCUS_SLOT, FOCUS_SLOT + 1, false)) {
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
        pylonContainer.stopOpen(player);
    }
}
