package com.mistaboom.essence_ascendance.fabric;

import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.ItemStack;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/** Fabric Transfer API view exposing only the Infuser's input and output. */
final class EssenceInfuserFabricItemStorage implements SlottedStorage<ItemVariant> {

    private final EssenceInfuserBlockEntity infuser;
    private final List<SingleStackStorage> slots;

    EssenceInfuserFabricItemStorage(EssenceInfuserBlockEntity infuser) {
        this.infuser = infuser;
        this.slots = List.of(
                new InfuserSlotStorage(EssenceInfuserBlockEntity.INPUT_SLOT),
                new InfuserSlotStorage(EssenceInfuserBlockEntity.OUTPUT_SLOT)
        );
    }

    @Override
    public int getSlotCount() {
        return slots.size();
    }

    @Override
    public SingleSlotStorage<ItemVariant> getSlot(int slot) {
        return slots.get(slot);
    }

    @Override
    public boolean supportsInsertion() {
        return true;
    }

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        return slots.get(0).insert(resource, maxAmount, transaction);
    }

    @Override
    public boolean supportsExtraction() {
        return true;
    }

    @Override
    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        return slots.get(1).extract(resource, maxAmount, transaction);
    }

    @Override
    public Iterator<StorageView<ItemVariant>> iterator() {
        return new Iterator<>() {
            private int slot = 0;

            @Override
            public boolean hasNext() {
                return slot < slots.size();
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

    private final class InfuserSlotStorage extends SingleStackStorage {
        private final int machineSlot;

        private InfuserSlotStorage(int machineSlot) {
            this.machineSlot = machineSlot;
        }

        @Override
        protected ItemStack getStack() {
            return infuser.getItem(machineSlot);
        }

        @Override
        protected void setStack(ItemStack stack) {
            infuser.setItem(machineSlot, stack);
        }

        @Override
        protected boolean canInsert(ItemVariant variant) {
            return machineSlot == EssenceInfuserBlockEntity.INPUT_SLOT
                    && EssenceInfuserBlockEntity.isLatentCarrier(variant.toStack(1));
        }

        @Override
        protected boolean canExtract(ItemVariant variant) {
            return machineSlot == EssenceInfuserBlockEntity.OUTPUT_SLOT;
        }

        @Override
        protected void onFinalCommit() {
            infuser.setChanged();
        }
    }
}
