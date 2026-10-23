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

/**
 * Render-only block entity for the modeled Ascendance Nexus.
 *
 * <p>The Nexus remains mechanically stateless; this block entity exists only
 * so the Generic-Model mesh can use the same rendering path as the Crucible
 * and Pylon.</p>
 */
public final class AscendanceNexusBlockEntity extends BlockEntity {
    private MachineVisualState.Nexus clientVisualState;
    private MachineVisualState.Nexus lastSentVisualState;

    public AscendanceNexusBlockEntity(BlockPos pos, BlockState state) {
        super(
                AscendanceNexusContent.ASCENDANCE_NEXUS_BLOCK_ENTITY.get(),
                pos,
                state
        );
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
        clientVisualState = tag.contains(MachineVisualState.TAG, Tag.TAG_COMPOUND)
                //? if <26.1.2
                ? MachineVisualState.Nexus.read(tag.getCompound(MachineVisualState.TAG)) : null;
    //? if <26.1.2
    }

    //? if >=26.1.2
    /*@Override protected void loadAdditional(ValueInput input) { super.loadAdditional(input); clientVisualState = input.read(MachineVisualState.TAG, CompoundTag.CODEC).map(MachineVisualState.Nexus::read).orElse(null); }*/

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
