package com.mistaboom.essence_ascendance.client.nexus;

import java.util.Objects;

/** Presentation handoff requires an accepted Ascension plus the matching changed-tier state. */
public final class NexusAscensionHandoff {
    private String fromTier = "";
    private long acceptedRevision = -1;

    public void begin(String tier) {
        fromTier = Objects.requireNonNull(tier);
        acceptedRevision = -1;
    }

    public void acknowledge(boolean accepted, boolean ascended, long revision) {
        acceptedRevision = accepted && ascended && revision >= 0 ? revision : -1;
    }

    public boolean ready(boolean snapshotReady, long revision, String currentTier) {
        return snapshotReady && acceptedRevision >= 0 && revision >= acceptedRevision
                && !fromTier.isEmpty() && currentTier != null && !currentTier.isEmpty() && !currentTier.equals(fromTier);
    }
}
