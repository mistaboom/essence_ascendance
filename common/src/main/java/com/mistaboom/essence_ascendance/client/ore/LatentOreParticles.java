package com.mistaboom.essence_ascendance.client.ore;

import com.mistaboom.essence_ascendance.ore.LatentOreBlockEntity;
import com.mistaboom.essence_ascendance.ore.LatentOreHost;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Destruction packets contain only the registered block state, and can arrive after
 * its block entity is removed. Keep a bounded, brief identity snapshot for debris.
 * This owns no images, sprites, world references, or ticking/render callbacks.
 */
public final class LatentOreParticles {
    private static final int MAX_REMOVALS = 256;
    private static final long LIFETIME_NANOS = 2_000_000_000L;
    private static final Map<BlockPos, Removal> REMOVED = new LinkedHashMap<>();
    private static WeakReference<Level> world = new WeakReference<>(null);

    private LatentOreParticles() { }

    public static synchronized void remember(LatentOreBlockEntity ore) {
        Level level = ore.getLevel();
        if (level == null || !level.isClientSide) return;
        useWorld(level);
        prune();
        ore.hostState().ifPresent(host -> REMOVED.put(ore.getBlockPos().immutable(),
                new Removal(host, System.nanoTime())));
        while (REMOVED.size() > MAX_REMOVALS) REMOVED.remove(REMOVED.keySet().iterator().next());
    }

    public static synchronized Optional<BlockState> host(Level level, BlockPos pos) {
        Optional<BlockState> live = LatentOreHost.host(level, pos);
        if (live.isPresent()) return live;
        useWorld(level);
        prune();
        Removal removed = REMOVED.get(pos);
        return removed == null ? Optional.empty() : Optional.of(removed.host);
    }

    public static synchronized void clear() {
        REMOVED.clear();
        world.clear();
    }

    private static void useWorld(Level level) {
        if (world.get() != level) {
            REMOVED.clear();
            world = new WeakReference<>(level);
        }
    }

    private static void prune() {
        long now = System.nanoTime();
        REMOVED.values().removeIf(removal -> now - removal.removedAt > LIFETIME_NANOS);
    }

    private record Removal(BlockState host, long removedAt) { }
}
