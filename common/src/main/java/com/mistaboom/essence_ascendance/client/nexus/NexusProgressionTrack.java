package com.mistaboom.essence_ascendance.client.nexus;

import com.mistaboom.essence_ascendance.client.ClientEssenceState;
import com.mistaboom.essence_ascendance.stat.StatDefinition;

import java.util.List;
import java.util.Objects;

/** Immutable data required to render one progression track. */
public record NexusProgressionTrack(
        StatDefinition stat,
        ClientEssenceState.StatSnapshot state,
        List<NexusMilestoneView> milestones
) {
    public NexusProgressionTrack {
        Objects.requireNonNull(stat, "Progression stat cannot be null");
        Objects.requireNonNull(state, "Progression state cannot be null");
        milestones = List.copyOf(milestones);
    }
}
