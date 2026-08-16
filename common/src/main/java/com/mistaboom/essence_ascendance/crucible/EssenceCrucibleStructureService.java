package com.mistaboom.essence_ascendance.crucible;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.List;

/*
 * Single structure-evaluation seam for the future pylon system.
 *
 * TEMPORARY TESTING VALUES -- these are intentionally not final balance.
 */
public final class EssenceCrucibleStructureService {

    public static final EssenceCrucibleStructureStats BASE =
            new EssenceCrucibleStructureStats(
                    1,          // usable item slots
                    2_000_000L, // base owner reservoir capacity per enabled Essence family
                    8.0D,       // block transfer range
                    10_000L,    // TOTAL Essence / second
                    20,         // ticks to dissolve one item
                    1,          // simultaneous item dissolutions
                    6,          // all six block faces accept automation
                    1,          // no pylons: Crucible -> player
                    0           // active pylon count
            );

    private EssenceCrucibleStructureService() {
    }

    public static EssenceCrucibleStructureStats evaluate(
            Level level,
            BlockPos cruciblePos
    ) {
        /* Future: scan/evaluate pylons here and return the effective snapshot. */
        return BASE;
    }


    public static long effectiveReservoirCapacity(
            EssenceCrucibleStructureStats stats
    ) {
        int enabledEssenceFamilies =
                1 + (EssenceConfigManager.get().skillEssencesEnabled() ? 1 : 0);

        return Math.multiplyExact(
                stats.reservoirCapacity(),
                (long) enabledEssenceFamilies
        );
    }

    public static boolean allowsAutomationConnection(
            Level level,
            BlockPos cruciblePos,
            Direction side
    ) {
        /*
         * BASE exposes every face. Future structure evaluation can map actual
         * pylon/port locations to sides here without changing loader adapters.
         */
        return evaluate(level, cruciblePos).automationConnectionPorts() > 0;
    }

    public static List<TransferEndpoint> transferEndpoints(
            Level level,
            BlockPos cruciblePos,
            EssenceCrucibleStructureStats stats
    ) {
        /*
         * Future pylons can replace/add endpoints without changing transfer
         * ownership or particle code. For now the source is block center.
         */
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
