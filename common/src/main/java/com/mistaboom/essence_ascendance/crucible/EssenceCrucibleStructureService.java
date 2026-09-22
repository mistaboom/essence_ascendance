package com.mistaboom.essence_ascendance.crucible;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.pylon.EssencePylonBlockEntity;
import com.mistaboom.essence_ascendance.pylon.EssencePylonContribution;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/*
 * Single structure-evaluation seam for the Crucible/pylon system.
 *
 * Base and Focus values are intentionally centralized and temporary. The
 * structure detector itself is freeform: linked pylons may be placed anywhere
 * inside the configured spherical radius.
 */
public final class EssenceCrucibleStructureService {

    public static final int MAX_SUPPORTED_ACTIVE_PYLONS = 8;
    public static final int MAX_INPUT_SLOTS = 1 + MAX_SUPPORTED_ACTIVE_PYLONS;

    public static EssenceCrucibleStructureStats base() { return EssenceConfigManager.runtime().crucible(); }
    public static EssenceCrucibleStructureSnapshot baseSnapshot() { return new EssenceCrucibleStructureSnapshot(base(), List.of()); }

    private EssenceCrucibleStructureService() {
    }

    public static EssenceCrucibleStructureStats evaluate(
            Level level,
            BlockPos cruciblePos
    ) {
        return evaluateSnapshot(level, cruciblePos).stats();
    }

    public static EssenceCrucibleStructureSnapshot evaluateSnapshot(
            Level level,
            BlockPos cruciblePos
    ) {
        if (!(level instanceof ServerLevel serverLevel)
                || !(level.getBlockEntity(cruciblePos)
                        instanceof EssenceCrucibleBlockEntity crucible)
                || crucible.ownerId() == null) {
            return baseSnapshot();
        }

        double radius = EssenceConfigManager.get().pylonRadius();
        int maxActive = EssenceConfigManager.get().maxActivePylons();
        if (maxActive <= 0) {
            return baseSnapshot();
        }

        int scan = (int) Math.ceil(radius);
        double radiusSquared = radius * radius;
        List<EssencePylonBlockEntity> candidates = new ArrayList<>();

        for (int dx = -scan; dx <= scan; dx++) {
            for (int dy = -scan; dy <= scan; dy++) {
                for (int dz = -scan; dz <= scan; dz++) {
                    BlockPos candidatePos = cruciblePos.offset(dx, dy, dz);
                    if (candidatePos.equals(cruciblePos)
                            || cruciblePos.distSqr(candidatePos) > radiusSquared
                            || !serverLevel.hasChunkAt(candidatePos)) {
                        continue;
                    }

                    if (!(serverLevel.getBlockEntity(candidatePos)
                            instanceof EssencePylonBlockEntity pylon)
                            || pylon.ownerId() == null
                            || !pylon.ownerId().equals(crucible.ownerId())
                            || !pylon.isLinkedTo(cruciblePos)
                            || !pylon.hasInstalledFocus()) {
                        continue;
                    }

                    candidates.add(pylon);
                }
            }
        }

        candidates.sort(
                Comparator.<EssencePylonBlockEntity>comparingDouble(
                                pylon -> cruciblePos.distSqr(pylon.getBlockPos())
                        )
                        .thenComparingLong(pylon -> pylon.getBlockPos().asLong())
        );

        int activeCount = Math.min(maxActive, candidates.size());
        if (activeCount == 0) {
            return baseSnapshot();
        }

        long reservoirCapacity = base().reservoirCapacity();
        double transferRange = base().transferRange();
        long transferRate = base().transferRatePerSecond();
        double dissolutionSpeedBonus = 0.0D;
        int simultaneousProcesses = base().simultaneousItemProcesses();
        List<EssenceCrucibleStructureSnapshot.ActivePylon> activePylons =
                new ArrayList<>(activeCount);

        for (int i = 0; i < activeCount; i++) {
            EssencePylonBlockEntity pylon = candidates.get(i);
            EssencePylonContribution contribution = pylon.contribution();

            reservoirCapacity = Math.addExact(
                    reservoirCapacity,
                    contribution.reservoirCapacityBonus()
            );
            transferRange += contribution.transferRangeBonus();
            transferRate = Math.addExact(
                    transferRate,
                    contribution.transferRatePerSecondBonus()
            );
            dissolutionSpeedBonus += contribution.dissolutionSpeedBonus();
            simultaneousProcesses = Math.addExact(
                    simultaneousProcesses,
                    contribution.simultaneousItemProcessesBonus()
            );

            activePylons.add(
                    new EssenceCrucibleStructureSnapshot.ActivePylon(
                            pylon.getBlockPos().immutable(),
                            pylon.focusTier(),
                            contribution
                    )
            );
        }

        int dissolutionTicks = Math.max(
                1,
                (int) Math.ceil(
                        base().dissolutionTicksPerItem()
                                / (1.0D + dissolutionSpeedBonus)
                )
        );

        EssenceCrucibleStructureStats stats =
                new EssenceCrucibleStructureStats(
                        Math.min(MAX_INPUT_SLOTS, base().usableItemSlots() + activeCount),
                        reservoirCapacity,
                        transferRange,
                        transferRate,
                        dissolutionTicks,
                        simultaneousProcesses,
                        base().automationConnectionPorts(),
                        1 + activeCount,
                        activeCount
                );

        return new EssenceCrucibleStructureSnapshot(stats, activePylons);
    }

    public static long effectiveReservoirCapacity(
            EssenceCrucibleStructureStats stats
    ) {
        return stats.reservoirCapacity();
    }

    public static boolean allowsAutomationConnection(
            Level level,
            BlockPos cruciblePos,
            Direction side
    ) {
        return evaluate(level, cruciblePos).automationConnectionPorts() > 0;
    }

    public static List<TransferEndpoint> transferEndpoints(
            Level level,
            BlockPos cruciblePos,
            EssenceCrucibleStructureStats stats
    ) {
        /* Player channeling always remains one concentrated Crucible -> player stream. */
        return List.of(
                new TransferEndpoint(
                        cruciblePos.getX() + 0.5D,
                        cruciblePos.getY() + 0.65D,
                        cruciblePos.getZ() + 0.5D
                )
        );
    }

    public record TransferEndpoint(
            double x,
            double y,
            double z
    ) {
    }
}
