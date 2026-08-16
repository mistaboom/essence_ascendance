package com.mistaboom.essence_ascendance.neoforge;

import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/* NeoForge item capability adapter: one exposed slot, insertion only. */
final class EssenceCrucibleNeoForgeItemHandler implements IItemHandler {

    private final EssenceCrucibleBlockEntity crucible;

    EssenceCrucibleNeoForgeItemHandler(EssenceCrucibleBlockEntity crucible) {
        this.crucible = crucible;
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot == 0
                ? crucible.getItem(0)
                : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(
            int slot,
            ItemStack stack,
            boolean simulate
    ) {
        if (slot != 0
                || stack.isEmpty()
                || !EssenceCrucibleBlockEntity.isValidNewInput(stack)) {
            return stack;
        }

        ItemStack current = crucible.getItem(0);
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
            crucible.setItem(0, replacement);
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
        if (slot != 0) {
            return 0;
        }
        ItemStack current = crucible.getItem(0);
        return current.isEmpty()
                ? 99
                : current.getMaxStackSize();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot == 0
                && EssenceCrucibleBlockEntity.isValidNewInput(stack);
    }
}
