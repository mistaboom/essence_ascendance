package com.mistaboom.essence_ascendance.gameplay;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable ownership, confirmed native kill and accepted companion-loss observations. */
public sealed interface CompanionOutcome {
    Context context();
    record Context(String actionId, UUID actor, String dimension, long committedTick) {
        public Context { text(actionId); Objects.requireNonNull(actor); text(dimension); if(committedTick<0)throw new IllegalArgumentException("Negative companion commit tick"); }
    }
    record BondedKill(Context context, UUID companion, UUID victim, UUID ownerAtContact,
                      UUID ownerAtCommit, boolean hostile, boolean empoweredAtContact,
                      boolean empoweredAtCommit) implements CompanionOutcome {
        public BondedKill { Objects.requireNonNull(context); Objects.requireNonNull(companion); Objects.requireNonNull(victim); Objects.requireNonNull(ownerAtContact); Objects.requireNonNull(ownerAtCommit); }
    }
    record CatFact(OutcomePolicies.CatCandidate candidate, boolean completeUndamagedEvidence) {
        public CatFact { Objects.requireNonNull(candidate); }
    }
    record CatKill(Context context, UUID victim, List<CatFact> cats) implements CompanionOutcome {
        public CatKill { Objects.requireNonNull(context); Objects.requireNonNull(victim); cats = List.copyOf(cats); if(cats.size()>64)throw new IllegalArgumentException("Too many companion candidates"); }
    }
    /** Invalidation is useful even while the owner is offline or ineligible for a success award. */
    record CatInvalidated(Context context, UUID cat, String reason) implements CompanionOutcome {
        public CatInvalidated { Objects.requireNonNull(context); Objects.requireNonNull(cat); text(reason); }
    }
    private static void text(String value) { if(value==null||value.isBlank()||value.length()>256)throw new IllegalArgumentException("Invalid bounded companion text"); }
}
