package com.mistaboom.essence_ascendance.neoforge;

//? if >=26.1.2 {
/*import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStackResourceHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

// NeoForge 26 transactions use the same provisional writes and commit effects as Fabric.
// Slot journals restore exact contents on nested/root abort; only root commit publishes work.
abstract class MachineNeoForgeResourceHandler implements ResourceHandler<ItemResource> {
    private final Slot[] slots;
    private final Effects effects = new Effects(this::committed);

    MachineNeoForgeResourceHandler(int maximumSlots) {
        slots = new Slot[maximumSlots];
        for (int index = 0; index < maximumSlots; index++) slots[index] = new Slot(index);
    }

    abstract ItemStack stack(int index);
    abstract void write(int index, ItemStack stack);
    abstract boolean insertable(int index, ItemResource resource);
    abstract boolean extractable(int index, ItemResource resource);
    abstract int capacity(int index, ItemResource resource);
    abstract int contextFlags(int index, ItemStack before, ItemStack after);
    abstract void committed(int flags);

    private Slot slot(int index) { return slots[java.util.Objects.checkIndex(index, size())]; }
    public ItemResource getResource(int index) { return slot(index).getResource(0); }
    public long getAmountAsLong(int index) { return slot(index).getAmountAsLong(0); }
    public long getCapacityAsLong(int index, ItemResource resource) { return slot(index).getCapacityAsLong(0, resource); }
    public boolean isValid(int index, ItemResource resource) { return slot(index).isValid(0, resource); }
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        Slot slot = slot(index);
        ItemStack before = slot.getStack().copy();
        int inserted = slot.insert(0, resource, amount, transaction);
        if (inserted > 0) effects.record(transaction, contextFlags(index, before, slot.getStack()));
        return inserted;
    }
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        Slot slot = slot(index);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!extractable(index, resource)) return 0;
        int extracted = slot.extract(0, resource, amount, transaction);
        if (extracted > 0) effects.record(transaction, 0);
        return extracted;
    }

    private final class Slot extends ItemStackResourceHandler {
        private final int index;
        Slot(int index) { this.index = index; }
        protected ItemStack getStack() { return stack(index); }
        protected void setStack(ItemStack stack) { write(index, stack); }
        protected boolean isValid(ItemResource resource) { return insertable(index, resource); }
        protected int getCapacity(ItemResource resource) { return capacity(index, resource); }
    }

    static final class Effects extends SnapshotJournal<Integer> {
        private final java.util.function.IntConsumer commit;
        private int flags;
        Effects(java.util.function.IntConsumer commit) { this.commit = commit; }
        void record(TransactionContext transaction, int added) { updateSnapshots(transaction); flags |= added; }
        protected Integer createSnapshot() { return flags; }
        protected void revertToSnapshot(Integer snapshot) { flags = snapshot; }
        protected void onRootCommit(Integer original) {
            int applied = flags;
            flags = 0;
            commit.accept(applied);
        }
    }
}
*///? }
