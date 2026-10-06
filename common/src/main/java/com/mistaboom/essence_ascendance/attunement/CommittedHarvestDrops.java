package com.mistaboom.essence_ascendance.attunement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/** One player harvest's receipts, independent of a loader's direct or captured drop pipeline. */
public final class CommittedHarvestDrops {
    private final Object level;
    private final BlockPos position;
    private final Set<UUID> observed = new HashSet<>();
    private double value;
    private boolean closed;

    public CommittedHarvestDrops(Object level, BlockPos position) {
        this.level = level;
        this.position = position.immutable();
    }

    /** Resolve the final stack only after a unique, local item was successfully inserted. */
    public void observe(Object itemLevel, UUID itemId, Vec3 itemPosition, boolean spawned, DoubleSupplier installedValue) {
        if (closed || !spawned || level != itemLevel || itemId == null || itemPosition == null
                || !Double.isFinite(itemPosition.x) || !Double.isFinite(itemPosition.y) || !Double.isFinite(itemPosition.z)
                || itemPosition.x < position.getX() || itemPosition.x >= position.getX() + 1.0
                || itemPosition.y < position.getY() || itemPosition.y >= position.getY() + 1.0
                || itemPosition.z < position.getZ() || itemPosition.z >= position.getZ() + 1.0
                || !observed.add(itemId)) return;
        double contribution = installedValue.getAsDouble();
        if (Double.isFinite(contribution) && contribution > 0)
            value = Math.min(Long.MAX_VALUE, value + contribution);
    }

    /** Canceled/failed destruction discards its drops; a receipt can be committed only once. */
    public double complete(boolean completed) {
        if (closed) return 0;
        closed = true;
        return completed ? value : 0;
    }
}
