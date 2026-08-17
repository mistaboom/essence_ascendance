package com.mistaboom.essence_ascendance.fabric;

import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/*
 * Fabric Transfer API adapter for the pylon-expanded Crucible input lanes.
 * Only currently-active lanes are exposed and extraction remains disabled.
 */
final class EssenceCrucibleFabricItemStorage implements SlottedStorage<ItemVariant> {

    private final EssenceCrucibleBlockEntity crucible;
    private final List<SingleStackStorage> slots;

    EssenceCrucibleFabricItemStorage(EssenceCrucibleBlockEntity crucible) {
        this.crucible = crucible;
        this.slots = new ArrayList<>(EssenceCrucibleBlockEntity.MAX_INPUT_SLOTS);
        for (int slot = 0; slot < EssenceCrucibleBlockEntity.MAX_INPUT_SLOTS; slot++) {
            slots.add(new CrucibleSlotStorage(slot));
        }
    }

    @Override
    public int getSlotCount() {
        return crucible.activeInputSlotCount();
    }

    @Override
    public SingleSlotStorage<ItemVariant> getSlot(int slot) {
        if (slot < 0 || slot >= getSlotCount()) {
            throw new IndexOutOfBoundsException("Crucible input slot " + slot + " is not active");
        }
        return slots.get(slot);
    }

    @Override
    public boolean supportsInsertion() {
        return true;
    }

    @Override
    public long insert(
            ItemVariant resource,
            long maxAmount,
            TransactionContext transaction
    ) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);

        long inserted = 0L;
        int active = getSlotCount();
        for (int slot = 0; slot < active && inserted < maxAmount; slot++) {
            inserted += slots.get(slot).insert(
                    resource,
                    maxAmount - inserted,
                    transaction
            );
        }
        return inserted;
    }

    @Override
    public boolean supportsExtraction() {
        return false;
    }

    @Override
    public long extract(
            ItemVariant resource,
            long maxAmount,
            TransactionContext transaction
    ) {
        return 0L;
    }

    @Override
    public Iterator<StorageView<ItemVariant>> iterator() {
        int active = getSlotCount();
        return new Iterator<>() {
            private int slot = 0;

            @Override
            public boolean hasNext() {
                return slot < active;
            }

            @Override
            public StorageView<ItemVariant> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return slots.get(slot++);
            }
        };
    }

    private final class CrucibleSlotStorage extends SingleStackStorage {
        private final int slot;

        private CrucibleSlotStorage(int slot) {
            this.slot = slot;
        }

        @Override
        protected ItemStack getStack() {
            return crucible.getItem(slot);
        }

        @Override
        protected void setStack(ItemStack stack) {
            crucible.setItem(slot, stack);
        }

        @Override
        protected boolean canInsert(ItemVariant variant) {
            return crucible.isInputSlotActive(slot)
                    && crucible.canPlaceItem(slot, variant.toStack(1));
        }

        @Override
        protected boolean canExtract(ItemVariant variant) {
            return false;
        }

        @Override
        public boolean supportsExtraction() {
            return false;
        }

        @Override
        protected void onFinalCommit() {
            crucible.setChanged();
        }
    }
}
