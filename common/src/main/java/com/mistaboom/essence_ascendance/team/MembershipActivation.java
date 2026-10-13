package com.mistaboom.essence_ascendance.team;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import java.util.*;

/** Reviewable, optimistic administrator receipt. No provider or monetary state is copied here. */
public final class MembershipActivation {
    private final MembershipAuthority from, target;
    private final long revision, rosterRevision;
    private final Map<PoolId, MembershipRoster> rosters;
    private final UUID token = UUID.randomUUID();
    private MembershipActivation(EssenceSavedData saved, MembershipAuthority target, MembershipProvider.Observation evidence) {
        if (!MembershipAuthority.ready(evidence.readiness())) throw new IllegalStateException("Target authority is unavailable");
        this.from = saved.membershipAuthority(); this.target = target;
        this.revision = saved.reservoirs().revision(); this.rosterRevision = saved.reservoirs().rosterRevision();
        rosters = MembershipPublication.normalize(target, evidence.teams());
        saved.activateMembershipAuthority(target, evidence.readiness(), snapshots(), true);
    }
    public static MembershipActivation prepare(EssenceSavedData saved, MembershipAuthority target, MembershipProvider.Observation settled) {
        return new MembershipActivation(saved, target, settled);
    }
    public UUID token() { return token; }
    public int teamCount() { return rosters.size(); }
    public int memberCount() { return rosters.values().stream().mapToInt(r -> r.members().size()).sum(); }
    private List<MembershipSnapshot> snapshots() { return rosters.values().stream().map(r -> r.settle(null)).toList(); }
    public String description() {
        return from.id() + " -> " + target.id() + "; " + teamCount() + " target parties, "
                + memberCount()
                + " actual members. Old members detach with their recorded shares; target parties equalize all stored essence. "
                + "Available essence/progression stay personal. Solo pool identities change; existing machine bindings are not remapped across providers. Token " + token;
    }
    public void activate(EssenceSavedData saved, UUID supplied, MembershipProvider.Observation current) {
        if (!token.equals(supplied) || saved.membershipAuthority() != from
                || saved.reservoirs().revision() != revision || saved.reservoirs().rosterRevision() != rosterRevision
                || !MembershipAuthority.ready(current.readiness()) || !rosters.equals(MembershipPublication.normalize(target, current.teams())))
            throw new IllegalStateException("Activation receipt is stale/unavailable; prepare again");
        saved.activateMembershipAuthority(target, current.readiness(), snapshots(), false);
    }
}
