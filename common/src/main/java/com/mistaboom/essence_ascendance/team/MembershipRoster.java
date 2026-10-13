package com.mistaboom.essence_ascendance.team;

import java.util.*;

/** Provider evidence only: all actual members and translated grants, no money or external API types. */
public record MembershipRoster(PoolId pool, List<UUID> members, Map<UUID, Set<PoolCapability>> grants, UUID recoveryRecipient) {
    public MembershipRoster(PoolId pool, List<UUID> members, Map<UUID, Set<PoolCapability>> grants) {
        this(pool, members, grants, MembershipSnapshot.inferRecipient(members, grants));
    }
    public MembershipRoster {
        MembershipSnapshot validated = new MembershipSnapshot(pool, members, grants, 1, 1, recoveryRecipient);
        if (pool.kind() != PoolId.Kind.TEAM) throw new IllegalArgumentException("Batch contains a non-team");
        members = validated.members(); grants = validated.permissions();
    }
    public static MembershipRoster from(MembershipSnapshot snapshot) {
        return new MembershipRoster(snapshot.pool(), snapshot.members(), snapshot.permissions(), snapshot.recoveryRecipient());
    }
    public static MembershipRoster party(MembershipAuthority authority, UUID id,
                                          Collection<UUID> members, Set<UUID> managers) {
        return new MembershipRoster(new PoolId(authority, PoolId.Kind.TEAM, id),
                List.copyOf(members), MembershipPolicy.grants(members, managers));
    }
    public static MembershipRoster party(MembershipAuthority authority, UUID id,
                                          Collection<UUID> members, Set<UUID> managers, UUID leader) {
        return new MembershipRoster(new PoolId(authority, PoolId.Kind.TEAM, id), List.copyOf(members),
                MembershipPolicy.grants(members, managers), leader);
    }
    public MembershipSnapshot settle(MembershipSnapshot previous) {
        MembershipSnapshot next = previous == null ? new MembershipSnapshot(pool, members, grants, 1, 1)
                : previous.reconcile(members, grants).after();
        return new MembershipSnapshot(pool, members, grants, next.membershipRevision(), next.permissionRevision(), recoveryRecipient);
    }
}
