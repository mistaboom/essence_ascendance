package com.mistaboom.essence_ascendance.neoforge;

//? if >=26.1.2 {
/*import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import com.mistaboom.essence_ascendance.network.ServerMenuAccess;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

// Machine policy stays here; rollback and commit mechanics are shared by both capabilities.
final class MachineNeoForgeTransfer {
    private MachineNeoForgeTransfer() { }
    static ResourceHandler<ItemResource> crucible(EssenceCrucibleBlockEntity machine, Direction side) {
        return new MachineNeoForgeResourceHandler(EssenceCrucibleBlockEntity.MAX_INPUT_SLOTS) {
            public int size() { return machine.activeInputSlotCount(); }
            ItemStack stack(int index) { return machine.getItem(index); }
            void write(int index, ItemStack stack) { machine.setItemFromTransferSnapshot(index, stack); }
            boolean insertable(int index, ItemResource resource) {
                return ServerMenuAccess.isLoaded(machine)
                        && machine.canPlaceItemThroughFace(index, resource.toStack(1), side);
            }
            boolean extractable(int index, ItemResource resource) { return false; }
            int capacity(int index, ItemResource resource) { return resource.getMaxStackSize(); }
            int contextFlags(int index, ItemStack before, ItemStack after) { return 0; }
            void committed(int flags) { machine.setChanged(); }
        };
    }

    static ResourceHandler<ItemResource> infuser(EssenceInfuserBlockEntity machine) {
        return new MachineNeoForgeResourceHandler(3) {
            private int mapped(int index) {
                return switch (index) {
                    case 0 -> EssenceInfuserBlockEntity.INPUT_SLOT;
                    case 1 -> EssenceInfuserBlockEntity.OUTPUT_SLOT;
                    case 2 -> EssenceInfuserBlockEntity.COMPONENT_SLOT;
                    default -> throw new IndexOutOfBoundsException(index);
                };
            }
            public int size() { return 3; }
            ItemStack stack(int index) { return machine.getItem(mapped(index)); }
            void write(int index, ItemStack stack) { machine.setItemFromTransferSnapshot(mapped(index), stack); }
            boolean insertable(int index, ItemResource resource) {
                return ServerMenuAccess.isLoaded(machine)
                        && machine.canPlaceItemThroughFace(mapped(index), resource.toStack(1), null);
            }
            boolean extractable(int index, ItemResource resource) {
                return ServerMenuAccess.isLoaded(machine)
                        && machine.canTakeItemThroughFace(mapped(index), resource.toStack(1), null);
            }
            int capacity(int index, ItemResource resource) {
                ItemStack stack = resource.toStack(1);
                int limit = Math.min(resource.getMaxStackSize(), machine.getMaxStackSize(stack));
                return index == 0 ? Math.min(limit, EssenceInfuserBlockEntity.workpieceStackLimit(stack)) : limit;
            }
            int contextFlags(int index, ItemStack before, ItemStack after) {
                if (index == 1 || ItemStack.isSameItemSameComponents(before, after)) return 0;
                return index == 0 ? 3 : 1;
            }
            void committed(int flags) { machine.finishItemTransfer((flags & 1) != 0, (flags & 2) != 0); }
        };
    }

}
*///? }
