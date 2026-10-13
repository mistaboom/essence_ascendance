package com.mistaboom.essence_ascendance.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.ArrayList;
import java.util.List;

/** Extracted loaded-chunk scan: discovery, structure and observer consumers share the world index. */
public final class LoadedMachineDiscovery {
    private LoadedMachineDiscovery() { }
    public static <T extends BlockEntity> List<T> within(ServerLevel level, BlockPos origin, double range, Class<T> type) {
        List<T> result = new ArrayList<>();
        int radius = (int) Math.ceil(range);
        for (int x = (origin.getX() - radius) >> 4; x <= (origin.getX() + radius) >> 4; x++) {
            for (int z = (origin.getZ() - radius) >> 4; z <= (origin.getZ() + radius) >> 4; z++) {
                if (!(level.getChunk(x, z, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) continue;
                for (BlockEntity entity : chunk.getBlockEntities().values())
                    if (!entity.isRemoved() && type.isInstance(entity) && origin.distSqr(entity.getBlockPos()) <= range * range)
                        result.add(type.cast(entity));
            }
        }
        return result;
    }
}
