package com.mistaboom.essence_ascendance.ore;

import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//? if >=26.1.2
/*import net.minecraft.world.level.storage.ValueInput;*/
//? if >=26.1.2
/*import net.minecraft.world.level.storage.ValueOutput;*/
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Compact persistent state holder. There is deliberately no ticker or block-entity renderer. */
public final class LatentOreBlockEntity extends BlockEntity {
    @Nullable private volatile BlockState host;
    @Nullable private volatile CompoundTag generatedOrigin;

    public LatentOreBlockEntity(BlockPos pos, BlockState state) {
        super(EssenceInfuserContent.LATENT_ORE_BLOCK_ENTITY.get(), pos, state);
    }

    public Optional<BlockState> hostState() { return Optional.ofNullable(host); }

    /** The worldgen block write already marks its chunk dirty; no live-level callbacks on worker threads. */
    public void initializeHost(BlockState state) {
        if (!LatentOreHost.isValid(state)) throw new IllegalArgumentException("Invalid Latent Ore host");
        host = state;
    }

    public void setHost(BlockState state) {
        initializeHost(state);
        generatedOrigin = null; // Player/portable/synthetic loot initialization cannot retain worldgen proof.
        setChanged();
        requestClientRenderUpdate();
    }

    /** Only the native worldgen placement owner calls this, after the host write succeeds. */
    public void initializeGeneratedOrigin(String dimension) {
        generatedOrigin = com.mistaboom.essence_ascendance.gathering.GeneratedOreOrigin.created(dimension, worldPosition);
    }
    public boolean hasGeneratedOrigin(String dimension, BlockPos position) {
        normalizeGeneratedOrigin();
        return host != null && worldPosition.equals(position)
                && com.mistaboom.essence_ascendance.gathering.GeneratedOreOrigin.matches(generatedOrigin, dimension, position);
    }
    public void clearGeneratedOrigin() {
        if (generatedOrigin == null) return;
        generatedOrigin = null;
        setChanged();
    }

    private void normalizeGeneratedOrigin() {
        generatedOrigin = com.mistaboom.essence_ascendance.gathering.GeneratedOreOrigin.atAddress(generatedOrigin,
                level == null ? null : level.dimension().location().toString(), worldPosition);
    }

    /** Loader model-data snapshots must be refreshed before a new chunk mesh is requested. */
    private void requestClientRenderUpdate() {
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    //? if <26.1.2
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        //? if <26.1.2
        super.saveAdditional(tag, registries);
        normalizeGeneratedOrigin();
        if (host != null) tag.put(LatentOreHost.HOST_TAG, LatentOreHost.encode(host));
        if (generatedOrigin != null) tag.put(com.mistaboom.essence_ascendance.gathering.GeneratedOreOrigin.TAG, generatedOrigin.copy());
    }

    //? if >=26.1.2
    /*@Override protected void saveAdditional(ValueOutput output) { super.saveAdditional(output); normalizeGeneratedOrigin(); if (host != null) output.store(LatentOreHost.HOST_TAG, CompoundTag.CODEC, LatentOreHost.encode(host)); if (generatedOrigin != null) output.store(com.mistaboom.essence_ascendance.gathering.GeneratedOreOrigin.TAG, CompoundTag.CODEC, generatedOrigin.copy()); }*/

    //? if <26.1.2
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        //? if <26.1.2
        super.loadAdditional(tag, registries);
        host = LatentOreHost.decode(tag.getCompound(LatentOreHost.HOST_TAG)).orElse(null);
        generatedOrigin = tag.contains(com.mistaboom.essence_ascendance.gathering.GeneratedOreOrigin.TAG, Tag.TAG_COMPOUND)
                ? tag.getCompound(com.mistaboom.essence_ascendance.gathering.GeneratedOreOrigin.TAG).copy() : null;
        normalizeGeneratedOrigin();
        // Client packet handling uses the same load path as disk. Mark its chunk mesh dirty.
        if (level != null && level.isClientSide)
            requestClientRenderUpdate();
    }

    //? if >=26.1.2
    /*@Override protected void loadAdditional(ValueInput input) { super.loadAdditional(input); host = LatentOreHost.decode(input.read(LatentOreHost.HOST_TAG, CompoundTag.CODEC).orElseGet(CompoundTag::new)).orElse(null); generatedOrigin = input.read(com.mistaboom.essence_ascendance.gathering.GeneratedOreOrigin.TAG, CompoundTag.CODEC).map(CompoundTag::copy).orElse(null); normalizeGeneratedOrigin(); if (level != null && level.isClientSide()) requestClientRenderUpdate(); }*/

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

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
