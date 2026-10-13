package com.mistaboom.essence_ascendance.team;

import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import java.util.*;

/** Coalesces external callbacks into complete observations on successive server ticks. */
public final class MembershipSettlement {
    private final EssenceSavedData saved;
    private Map<PoolId, MembershipRoster> candidate;
    private boolean pending = true;
    private Set<PoolId> confirmedDeletions = Set.of();
    public boolean pending() { return pending; }
    public Set<PoolId> confirmedDeletions() { return confirmedDeletions; }
    public MembershipSettlement(EssenceSavedData saved) { this.saved = saved; }
    public void invalidate(String detail) {
        pending = true; candidate = null;
        saved.reservoirs().pause(new ProviderReadiness(ProviderReadiness.Status.NOT_READY, "stage-3", detail));
    }
    public void observe(MembershipProvider.Observation observation) {
        if (!MembershipAuthority.ready(observation.readiness())) {
            candidate = null; pending = true; saved.reservoirs().pause(observation.readiness()); return;
        }
        Map<PoolId, MembershipRoster> next = MembershipPublication.normalize(saved.membershipAuthority(), observation.teams());
        Map<PoolId, MembershipRoster> current = new HashMap<>();
        saved.reservoirs().settledTeams().forEach(t -> current.put(t.pool(), MembershipRoster.from(t)));
        if (!pending && next.equals(current)) return;
        if (!next.equals(candidate)) {
            invalidate("Roster/roles settling; financial and permission access paused"); candidate = Map.copyOf(next); return;
        }
        Set<PoolId> deleted = new HashSet<>(current.keySet()); deleted.removeAll(next.keySet());
        MembershipPublication.publish(saved, observation.readiness(), next.values());
        confirmedDeletions = Set.copyOf(deleted); pending = false; candidate = null;
    }
}
