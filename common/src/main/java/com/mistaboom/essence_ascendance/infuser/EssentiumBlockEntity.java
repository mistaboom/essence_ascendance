package com.mistaboom.essence_ascendance.infuser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Stores the exact carrier stack while an Essentium block is placed in the world. */
public final class EssentiumBlockEntity extends BlockEntity {

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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!carrier.isEmpty()) {
            tag.put(CARRIER_TAG, carrier.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        carrier = tag.contains(CARRIER_TAG)
                ? ItemStack.parse(registries, tag.getCompound(CARRIER_TAG)).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
