package com.mistaboom.essence_ascendance.infuser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
//? if >=26.1.2
/*import net.minecraft.server.level.ServerLevel;*/
//? if >=26.1.2
/*import net.minecraft.world.level.block.Block;*/
//? if >=26.1.2
/*import net.minecraft.world.level.block.state.BlockState;*/
//? if >=26.1.2
/*import net.minecraft.world.level.storage.ValueInput;*/
//? if >=26.1.2
/*import net.minecraft.world.level.storage.ValueOutput;*/
import org.jetbrains.annotations.Nullable;

/** Stores the exact carrier stack while an Essentium block is placed in the world. */
public final class EssentiumBlockEntity extends BlockEntity {

    //? if >=26.1.2
    /*@Override public void preRemoveSideEffects(BlockPos pos, BlockState state) { if (level instanceof ServerLevel server && !dropSuppressed && !carrier.isEmpty()) Block.popResource(server, pos, carrier.copy()); super.preRemoveSideEffects(pos, state); }*/

    private static final String CARRIER_TAG = "carrier";
    private ItemStack carrier = ItemStack.EMPTY;
    private boolean dropSuppressed;

    public EssentiumBlockEntity(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        super(EssenceInfuserContent.ESSENTIUM_BLOCK_BLOCK_ENTITY.get(), pos, state);
    }

    public void setCarrier(ItemStack stack) {
        carrier = stack.copyWithCount(1);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public ItemStack carrierStack() {
        return carrier.copy();
    }

    public void suppressDrop() {
        dropSuppressed = true;
    }

    public boolean isDropSuppressed() {
        return dropSuppressed;
    }

    @Override
    //? if >=26.1.2
    /*protected void saveAdditional(ValueOutput tag) {*/
    //? if <26.1.2
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        //? if >=26.1.2
        /*super.saveAdditional(tag);*/
        //? if <26.1.2
        super.saveAdditional(tag, registries);
        if (!carrier.isEmpty()) {
            //? if >=26.1.2
            /*tag.store(CARRIER_TAG, ItemStack.CODEC, carrier);*/
            //? if <26.1.2
            tag.put(CARRIER_TAG, carrier.save(registries));
        }
    }

    @Override
    //? if >=26.1.2
    /*protected void loadAdditional(ValueInput tag) {*/
    //? if <26.1.2
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        //? if >=26.1.2
        /*super.loadAdditional(tag);*/
        //? if <26.1.2
        super.loadAdditional(tag, registries);
        //? if >=26.1.2
        /*carrier = tag.read(CARRIER_TAG, ItemStack.CODEC).orElse(ItemStack.EMPTY);*/
        //? if <26.1.2
        carrier = tag.contains(CARRIER_TAG)
                //? if <26.1.2
                ? ItemStack.parse(registries, tag.getCompound(CARRIER_TAG)).orElse(ItemStack.EMPTY)
                //? if <26.1.2
                : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        //? if >=26.1.2
        /*return saveCustomOnly(registries);*/
        //? if <26.1.2
        CompoundTag tag = new CompoundTag();
        //? if <26.1.2
        saveAdditional(tag, registries);
        //? if <26.1.2
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
