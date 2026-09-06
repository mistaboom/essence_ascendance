package com.mistaboom.essence_ascendance.neoforge;

import com.mistaboom.essence_ascendance.network.ServerMenuAccess;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/* NeoForge item capability adapter: active pylon-expanded lanes, insertion only. */
final class EssenceCrucibleNeoForgeItemHandler implements IItemHandler {

    private final EssenceCrucibleBlockEntity crucible;

    EssenceCrucibleNeoForgeItemHandler(EssenceCrucibleBlockEntity crucible) {
        this.crucible = crucible;
    }

    @Override
    public int getSlots() {
        return crucible.activeInputSlotCount();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < getSlots()
                ? crucible.getItem(slot)
                : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(
            int slot,
            ItemStack stack,
            boolean simulate
    ) {
        if (!ServerMenuAccess.isLoaded(crucible)
                || slot < 0
                || slot >= getSlots()
                || stack.isEmpty()
                || !crucible.canPlaceItem(slot, stack)) {
            return stack;
        }

        ItemStack current = crucible.getItem(slot);
        if (!current.isEmpty()
                && !ItemStack.isSameItemSameComponents(current, stack)) {
            return stack;
        }

        int maxStack = Math.min(
                stack.getMaxStackSize(),
                current.isEmpty()
                        ? stack.getMaxStackSize()
                        : current.getMaxStackSize()
        );
        int room = maxStack - current.getCount();
        if (room <= 0) {
            return stack;
        }

        int inserted = Math.min(room, stack.getCount());
        if (!simulate) {
            ItemStack replacement;
            if (current.isEmpty()) {
                replacement = stack.copy();
                replacement.setCount(inserted);
            } else {
                replacement = current.copy();
                replacement.grow(inserted);
            }
            crucible.setItem(slot, replacement);
        }

        if (inserted >= stack.getCount()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        remainder.setCount(stack.getCount() - inserted);
        return remainder;
    }

    @Override
    public ItemStack extractItem(
            int slot,
            int amount,
            boolean simulate
    ) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        if (slot < 0 || slot >= getSlots()) {
            return 0;
        }
        ItemStack current = crucible.getItem(slot);
        return current.isEmpty()
                ? 64
                : current.getMaxStackSize();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return ServerMenuAccess.isLoaded(crucible)
                && slot >= 0
                && slot < getSlots()
                && crucible.canPlaceItem(slot, stack);
    }
}
