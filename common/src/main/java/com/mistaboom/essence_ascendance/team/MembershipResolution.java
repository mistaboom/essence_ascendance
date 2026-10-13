package com.mistaboom.essence_ascendance.team;

import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;

import java.util.Objects;
import java.util.Optional;

/** Unknown/unavailable is explicit, never an empty financial membership or an implicit solo. */
public record MembershipResolution(ProviderReadiness readiness, Optional<MembershipSnapshot> snapshot) {
    public enum State { ABSENT, UNSUPPORTED, UNAVAILABLE, READY_SOLO, READY_TEAM }
    public State state() {
        if (snapshot.isPresent()) return snapshot.get().pool().kind() == PoolId.Kind.SOLO ? State.READY_SOLO : State.READY_TEAM;
        return switch (readiness.status()) {
            case ABSENT -> State.ABSENT;
            case UNSUPPORTED, PARTIALLY_SUPPORTED -> State.UNSUPPORTED;
            default -> State.UNAVAILABLE;
        };
    }
    public MembershipResolution {
        Objects.requireNonNull(readiness);
        Objects.requireNonNull(snapshot);
        if (MembershipAuthority.ready(readiness) != snapshot.isPresent())
            throw new IllegalArgumentException("Only complete, ready membership may expose a snapshot");
    }

    public MembershipSnapshot requireReady() {
        return snapshot.orElseThrow(() -> new IllegalStateException("Membership unavailable: " + readiness.detail()));
    }
}
