package com.mistaboom.essence_ascendance.client.nexus;

import com.mistaboom.essence_ascendance.network.PlayerEssenceSyncPayload;

/** Resolves the one bottom action on the Ascendance page without mixing commits and Ascension. */
public record NexusAscendanceAction(
        Kind kind,
        boolean enabled
) {
    public static NexusAscendanceAction resolve(
            PlayerEssenceSyncPayload.ProgressStatus progressStatus,
            boolean attunementReady,
            boolean hasChanges,
            boolean invalidated,
            boolean requestPending
    ) {
        if (requestPending) {
            return new NexusAscendanceAction(Kind.APPLYING, false);
        }
        if (hasChanges) {
            return new NexusAscendanceAction(
                    invalidated ? Kind.DISCARD_DRAFT : Kind.APPLY_CHANGES,
                    true
            );
        }
        if (progressStatus == PlayerEssenceSyncPayload.ProgressStatus.MAX_TIER) {
            return new NexusAscendanceAction(Kind.MAXIMUM_TIER, false);
        }
        if (progressStatus
                == PlayerEssenceSyncPayload.ProgressStatus.CONFIGURATION_ERROR) {
            return new NexusAscendanceAction(Kind.UNAVAILABLE, false);
        }
        return new NexusAscendanceAction(Kind.ASCEND, attunementReady);
    }

    public enum Kind {
        APPLYING,
        DISCARD_DRAFT,
        APPLY_CHANGES,
        ASCEND,
        MAXIMUM_TIER,
        UNAVAILABLE
    }
}
