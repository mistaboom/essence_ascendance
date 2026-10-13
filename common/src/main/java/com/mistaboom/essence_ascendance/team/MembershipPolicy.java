package com.mistaboom.essence_ascendance.team;

import java.util.*;

/** One authorization policy for every actual roster. Providers translate management roles only. */
public final class MembershipPolicy {
    private MembershipPolicy() { }
    public static Map<UUID, Set<PoolCapability>> grants(Collection<UUID> members, Set<UUID> managers) {
        if (!members.containsAll(managers)) throw new IllegalArgumentException("Manager outside actual roster");
        Map<UUID, Set<PoolCapability>> result = new HashMap<>();
        for (UUID member : members) result.put(member, managers.contains(member)
                ? EnumSet.allOf(PoolCapability.class)
                : EnumSet.of(PoolCapability.USE, PoolCapability.CONFIGURE, PoolCapability.SPEND, PoolCapability.APPROVE_REQUEST));
        return result;
    }
}
