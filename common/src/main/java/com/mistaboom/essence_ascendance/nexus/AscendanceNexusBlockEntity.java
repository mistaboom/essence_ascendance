package com.mistaboom.essence_ascendance.nexus;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Render-only block entity for the modeled Ascendance Nexus.
 *
 * <p>The Nexus remains mechanically stateless; this block entity exists only
 * so the Generic-Model mesh can use the same rendering path as the Crucible
 * and Pylon.</p>
 */
public final class AscendanceNexusBlockEntity extends BlockEntity {

    public AscendanceNexusBlockEntity(BlockPos pos, BlockState state) {
        super(
                AscendanceNexusContent.ASCENDANCE_NEXUS_BLOCK_ENTITY.get(),
                pos,
                state
        );
    }
}
