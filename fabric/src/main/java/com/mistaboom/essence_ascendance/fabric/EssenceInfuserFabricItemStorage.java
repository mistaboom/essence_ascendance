package com.mistaboom.essence_ascendance.fabric;

import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import com.mistaboom.essence_ascendance.network.ServerMenuAccess;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.item.ItemStack;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/** Fabric Transfer API view exposing workpiece/output/component slots while protecting the installed machine Focus. */
final class EssenceInfuserFabricItemStorage implements SlottedStorage<ItemVariant> {

    private final EssenceInfuserBlockEntity infuser;
    private final List<SingleStackStorage> slots;
    private final TransferEffects effects = new TransferEffects();

    EssenceInfuserFabricItemStorage(EssenceInfuserBlockEntity infuser) {
        this.infuser = infuser;
        this.slots = List.of(
                new InfuserSlotStorage(EssenceInfuserBlockEntity.INPUT_SLOT),
                new InfuserSlotStorage(EssenceInfuserBlockEntity.OUTPUT_SLOT),
                new InfuserSlotStorage(EssenceInfuserBlockEntity.COMPONENT_SLOT)
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
        long inserted = slots.get(0).insert(resource, maxAmount, transaction);
        if (inserted >= maxAmount) {
            return inserted;
        }
        return inserted + slots.get(2).insert(resource, maxAmount - inserted, transaction);
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
            infuser.setItemFromTransferSnapshot(machineSlot, stack);
        }

        @Override
        protected boolean canInsert(ItemVariant variant) {
            if (!ServerMenuAccess.isLoaded(infuser)) {
                return false;
            }
            if (machineSlot == EssenceInfuserBlockEntity.INPUT_SLOT) {
                return EssenceInfuserBlockEntity.allowsAutomationInput(variant.toStack(1));
            }
            return machineSlot == EssenceInfuserBlockEntity.COMPONENT_SLOT
                    && !infuser.repairMode()
                    && infuser.canPlaceItem(machineSlot, variant.toStack(1));
        }

        @Override
        protected boolean canExtract(ItemVariant variant) {
            return ServerMenuAccess.isLoaded(infuser)
                    && machineSlot == EssenceInfuserBlockEntity.OUTPUT_SLOT;
        }

        @Override
        protected int getCapacity(ItemVariant variant) {
            int capacity = super.getCapacity(variant);
            return machineSlot == EssenceInfuserBlockEntity.INPUT_SLOT && !variant.isBlank()
                    ? Math.min(capacity, EssenceInfuserBlockEntity.workpieceStackLimit(variant.toStack(1)))
                    : capacity;
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            ItemStack before = getStack().copy();
            long inserted = super.insert(resource, maxAmount, transaction);
            if (inserted > 0L) {
                effects.record(transaction, !ItemStack.isSameItemSameComponents(before, getStack()));
            }
            return inserted;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            long extracted = super.extract(resource, maxAmount, transaction);
            if (extracted > 0L) {
                // Only the output is extractable; taking output does not reset work.
                effects.record(transaction, false);
            }
            return extracted;
        }

        // TransferEffects owns the one final-commit notification for all slots.
    }

    /** Pending effects are themselves transactional, including nested aborts. */
    private final class TransferEffects extends SnapshotParticipant<Boolean> {
        private boolean contextChanged;

        void record(TransactionContext transaction, boolean changed) {
            updateSnapshots(transaction);
            contextChanged |= changed;
        }

        @Override
        protected Boolean createSnapshot() {
            return contextChanged;
        }

        @Override
        protected void readSnapshot(Boolean snapshot) {
            contextChanged = snapshot;
        }

        @Override
        protected void onFinalCommit() {
            boolean changed = contextChanged;
            contextChanged = false;
            infuser.finishItemTransfer(changed);
        }
    }

}
