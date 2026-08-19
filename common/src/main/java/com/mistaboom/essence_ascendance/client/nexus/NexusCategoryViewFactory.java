package com.mistaboom.essence_ascendance.client.nexus;

import com.mistaboom.essence_ascendance.client.ClientEssenceState;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceFamily;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatDefinition;

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

            NexusCategoryView.PresentationType presentationType =
                    essence.family() == EssenceFamily.ATTRIBUTE
                            ? NexusCategoryView.PresentationType.ATTRIBUTE_SLIDERS
                            : NexusCategoryView.PresentationType.PLACEHOLDER;

            List<NexusProgressionTrack> tracks =
                    presentationType == NexusCategoryView.PresentationType.ATTRIBUTE_SLIDERS
                            ? buildTracks(snapshot, essence)
                            : List.of();

            categories.add(
                    new NexusCategoryView(
                            essence,
                            presentationType,
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
                            List.of()
                    )
            );
        }

        return List.copyOf(tracks);
    }
}
