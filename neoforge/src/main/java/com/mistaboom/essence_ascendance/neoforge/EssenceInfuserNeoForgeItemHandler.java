package com.mistaboom.essence_ascendance.neoforge;

import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** NeoForge capability view exposing input/output while protecting the Focus. */
final class EssenceInfuserNeoForgeItemHandler implements IItemHandler {

    private final EssenceInfuserBlockEntity infuser;

    EssenceInfuserNeoForgeItemHandler(EssenceInfuserBlockEntity infuser) {
        this.infuser = infuser;
    }

    @Override
    public int getSlots() {
        return 2;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return switch (slot) {
            case 0 -> infuser.getItem(EssenceInfuserBlockEntity.INPUT_SLOT);
            case 1 -> infuser.getItem(EssenceInfuserBlockEntity.OUTPUT_SLOT);
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (slot != 0
                || stack.isEmpty()
                || !infuser.canPlaceItem(EssenceInfuserBlockEntity.INPUT_SLOT, stack)) {
            return stack;
        }

        ItemStack current = infuser.getItem(EssenceInfuserBlockEntity.INPUT_SLOT);
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack)) {
            return stack;
        }

        int room = stack.getMaxStackSize() - current.getCount();
        int inserted = Math.min(room, stack.getCount());
        if (inserted <= 0) {
            return stack;
        }

        if (!simulate) {
            ItemStack replacement = current.isEmpty() ? stack.copy() : current.copy();
            if (current.isEmpty()) {
                replacement.setCount(inserted);
            } else {
                replacement.grow(inserted);
            }
            infuser.setItem(EssenceInfuserBlockEntity.INPUT_SLOT, replacement);
        }

        if (inserted == stack.getCount()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        remainder.setCount(stack.getCount() - inserted);
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot != 1 || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack current = infuser.getItem(EssenceInfuserBlockEntity.OUTPUT_SLOT);
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int extracted = Math.min(amount, current.getCount());
        ItemStack result = current.copy();
        result.setCount(extracted);
        if (!simulate) {
            infuser.removeItem(EssenceInfuserBlockEntity.OUTPUT_SLOT, extracted);
        }
        return result;
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot == 0 || slot == 1 ? 64 : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot == 0
                && infuser.canPlaceItem(EssenceInfuserBlockEntity.INPUT_SLOT, stack);
    }
}
