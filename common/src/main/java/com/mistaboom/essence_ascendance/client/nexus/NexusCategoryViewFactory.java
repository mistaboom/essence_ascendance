package com.mistaboom.essence_ascendance.client.nexus;

import com.mistaboom.essence_ascendance.client.ClientEssenceState;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.progression.BonusTrackCurve;

import java.util.ArrayList;
import java.util.List;

/** Builds visible Nexus categories exclusively from synchronized player state. */
public final class NexusCategoryViewFactory {

    private NexusCategoryViewFactory() {
    }

    public static List<NexusCategoryView> build(
            ClientEssenceState.Snapshot snapshot
    ) {
        if (!snapshot.ready()) {
            return List.of();
        }

        List<NexusCategoryView> categories =
                new ArrayList<>();

        for (EssenceDefinition essence : EssenceRegistry.values()) {
            if (!snapshot.availableEssence().containsKey(essence.id())) {
                continue;
            }

            List<NexusProgressionTrack> tracks =
                    buildTracks(snapshot, essence);

            categories.add(
                    new NexusCategoryView(
                            essence,
                            snapshot.availableEssence().getOrDefault(
                                    essence.id(),
                                    0L
                            ),
                            tracks
                    )
            );
        }

        return List.copyOf(categories);
    }

    private static List<NexusProgressionTrack> buildTracks(
            ClientEssenceState.Snapshot snapshot,
            EssenceDefinition essence
    ) {
        List<NexusProgressionTrack> tracks =
                new ArrayList<>();

        for (StatDefinition stat : EssenceStatRegistry.values()) {
            if (!stat.essenceType().id().equals(essence.id())) {
                continue;
            }

            ClientEssenceState.StatSnapshot state =
                    snapshot.stats().get(stat.id());

            if (state == null) {
                continue;
            }

            tracks.add(
                    new NexusProgressionTrack(
                            stat,
                            state,
                            state.track().snapPoints().stream()
                                    .filter(fraction -> fraction > 0)
                                    .map(fraction -> {
                                        long cost = BonusTrackCurve.investmentForProgression(state.track().checkpoints(),
                                                state.track().investmentExponent(), fraction, state.track().completionTier());
                                        return new NexusMilestoneView(cost, fraction,
                                                state.effectiveInvestment() >= cost);
                                    }).toList()
                    )
            );
        }

        return List.copyOf(tracks);
    }
}
