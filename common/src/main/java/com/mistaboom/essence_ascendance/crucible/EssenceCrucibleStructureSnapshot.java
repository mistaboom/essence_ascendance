package com.mistaboom.essence_ascendance.crucible;

import com.mistaboom.essence_ascendance.pylon.EssencePylonContribution;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Full effective Crucible structure evaluation, including active pylons. */
public record EssenceCrucibleStructureSnapshot(
        EssenceCrucibleStructureStats stats,
        List<ActivePylon> activePylons
) {
    public EssenceCrucibleStructureSnapshot {
        activePylons = List.copyOf(activePylons);
    }

    public boolean containsPylon(BlockPos pos) {
        return activePylons.stream().anyMatch(pylon -> pylon.pos().equals(pos));
    }

    public record ActivePylon(
            BlockPos pos,
            @Nullable EssenceFocusTier focusTier,
            EssencePylonContribution contribution
    ) {
        public String focusDisplayName() {
            return focusTier == null
                    ? "Latent Focus"
                    : focusTier.displayName() + " Focus";
        }
    }
}
