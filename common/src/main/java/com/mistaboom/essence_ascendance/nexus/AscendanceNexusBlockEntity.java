package com.mistaboom.essence_ascendance.nexus;

import com.mistaboom.essence_ascendance.visual.MachineVisualState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//? if >=26.1.2
/*import net.minecraft.world.level.storage.ValueInput;*/
//? if >=26.1.2
/*import net.minecraft.world.level.storage.ValueOutput;*/

/**
 * Modeled Nexus with bounded survival-placement provenance for build/use sequences.
 *
 * <p>Progression remains player-owned. The optional placer UUID is evidence for
 * this placed instance only; old/unknown Nexus placements have no inferred actor.</p>
 */
public final class AscendanceNexusBlockEntity extends BlockEntity {
    private MachineVisualState.Nexus clientVisualState;
    private MachineVisualState.Nexus lastSentVisualState;
    private java.util.UUID survivalBuilder;
    private net.minecraft.resources.ResourceLocation placementDimension;
    private Long placementPosition;
    private static final String BUILDER_TAG = "survival_builder";
    private static final String PLACEMENT_DIMENSION_TAG = "survival_placement_dimension";
    private static final String PLACEMENT_POSITION_TAG = "survival_placement_position";

    public AscendanceNexusBlockEntity(BlockPos pos, BlockState state) {
        super(
                AscendanceNexusContent.ASCENDANCE_NEXUS_BLOCK_ENTITY.get(),
                pos,
                state
        );
    }

    public void recordPlacement(ServerPlayer player) {
        survivalBuilder = com.mistaboom.essence_ascendance.attunement.AttunementGameplay.eligible(player)
                ? player.getUUID() : null;
        placementDimension = survivalBuilder != null && level != null ? level.dimension().location() : null;
        placementPosition = survivalBuilder == null ? null : worldPosition.asLong();
        setChanged();
    }

    public java.util.UUID survivalBuilder() {
        // /clone copies native block-entity NBT. The source's placement at another address
        // cannot prove this physical Nexus was placed by that survival player.
        invalidateCopiedPlacement();
        return level == null ? null : survivalBuilder;
    }

    private void invalidateCopiedPlacement() {
        if (survivalBuilder != null && (placementDimension == null || placementPosition == null
                || placementPosition != worldPosition.asLong()
                || level != null && !placementDimension.equals(level.dimension().location()))) {
            // Clear rather than only reject: copying this block back to the old address cannot restore provenance.
            survivalBuilder = null; placementDimension = null; placementPosition = null;
        }
    }

    //? if >=26.1.2
    /*@Override public void preRemoveSideEffects(BlockPos pos, BlockState state) { if (level instanceof ServerLevel serverLevel) for (ServerPlayer player : serverLevel.players()) if (player.containerMenu instanceof AscendanceNexusMenu menu && menu.isAt(serverLevel, pos)) player.closeContainer(); super.preRemoveSideEffects(pos, state); }*/

    public MachineVisualState.Nexus visualState() {
        if (level != null && level.isClientSide) return clientVisualState == null
                ? MachineVisualState.IDLE_NEXUS : clientVisualState;
        if (!(level instanceof ServerLevel serverLevel)) return MachineVisualState.IDLE_NEXUS;
        for (ServerPlayer player : serverLevel.players()) {
            if (player.containerMenu instanceof AscendanceNexusMenu menu
                    && menu.isAt(serverLevel, worldPosition)) {
                return new MachineVisualState.Nexus(true);
            }
        }
        return MachineVisualState.IDLE_NEXUS;
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state,
                                  AscendanceNexusBlockEntity nexus) {
        if (level.getGameTime() % 10L != 0L) return;
        MachineVisualState.Nexus current = nexus.visualState();
        if (current.equals(nexus.lastSentVisualState)) return;
        nexus.lastSentVisualState = current;
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    //? if <26.1.2
    @Override
    //? if <26.1.2
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        //? if <26.1.2
        super.loadAdditional(tag, registries);
        //? if <26.1.2
        survivalBuilder = tag.hasUUID(BUILDER_TAG) ? tag.getUUID(BUILDER_TAG) : null;
        //? if <26.1.2
        placementDimension = net.minecraft.resources.ResourceLocation.tryParse(tag.getString(PLACEMENT_DIMENSION_TAG));
        //? if <26.1.2
        placementPosition = tag.contains(PLACEMENT_POSITION_TAG, Tag.TAG_LONG) ? tag.getLong(PLACEMENT_POSITION_TAG) : null;
        //? if <26.1.2
        invalidateCopiedPlacement();
        //? if <26.1.2
        clientVisualState = tag.contains(MachineVisualState.TAG, Tag.TAG_COMPOUND)
                //? if <26.1.2
                ? MachineVisualState.Nexus.read(tag.getCompound(MachineVisualState.TAG)) : null;
    //? if <26.1.2
    }

    //? if >=26.1.2
    /*@Override protected void loadAdditional(ValueInput input) { super.loadAdditional(input); survivalBuilder = input.read(BUILDER_TAG, net.minecraft.core.UUIDUtil.CODEC).orElse(null); placementDimension = net.minecraft.resources.ResourceLocation.tryParse(input.getStringOr(PLACEMENT_DIMENSION_TAG, "")); placementPosition = input.read(PLACEMENT_POSITION_TAG, com.mojang.serialization.Codec.LONG).orElse(null); invalidateCopiedPlacement(); clientVisualState = input.read(MachineVisualState.TAG, CompoundTag.CODEC).map(MachineVisualState.Nexus::read).orElse(null); }*/

    @Override
    //? if >=26.1.2
    /*protected void saveAdditional(ValueOutput tag) {*/
    //? if <26.1.2
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        //? if >=26.1.2
        /*super.saveAdditional(tag);*/
        //? if <26.1.2
        super.saveAdditional(tag, registries);
        invalidateCopiedPlacement();
        if (survivalBuilder != null && placementDimension != null && placementPosition != null) {
            //? if >=26.1.2
            /*tag.store(BUILDER_TAG, net.minecraft.core.UUIDUtil.CODEC, survivalBuilder);*/
            //? if <26.1.2
            tag.putUUID(BUILDER_TAG, survivalBuilder);
            tag.putString(PLACEMENT_DIMENSION_TAG, placementDimension.toString());
            tag.putLong(PLACEMENT_POSITION_TAG, placementPosition);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put(MachineVisualState.TAG, visualState().save());
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this,
                (entity, registries) -> entity.getUpdateTag(registries));
    }
}
