package com.mistaboom.essence_ascendance.team;

import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import java.util.*;

/** Shared normalization/publication boundary. Source receipts advance only when settled contents change. */
public final class MembershipPublication {
    private MembershipPublication() { }
    public static Map<PoolId, MembershipRoster> normalize(MembershipAuthority authority, Collection<MembershipRoster> rosters) {
        Map<PoolId, MembershipRoster> result = new HashMap<>(); Set<UUID> members = new HashSet<>();
        for (MembershipRoster roster : rosters) {
            if (roster.pool().authority() != authority || result.put(roster.pool(), roster) != null)
                throw new IllegalArgumentException("Conflicting provider team/authority");
            for (UUID member : roster.members()) if (!members.add(member)) throw new IllegalArgumentException("Conflicting provider member");
        }
        return Map.copyOf(result);
    }
    public static boolean publish(EssenceSavedData saved, ProviderReadiness ready, Collection<MembershipRoster> rosters) {
        if (!MembershipAuthority.ready(ready)) { saved.reservoirs().pause(ready); return false; }
        Map<PoolId, MembershipSnapshot> old = new HashMap<>();
        saved.reservoirs().settledTeams().forEach(s -> old.put(s.pool(), s));
        Map<PoolId, MembershipSnapshot> next = new HashMap<>();
        for (MembershipRoster roster : normalize(saved.membershipAuthority(), rosters).values()) {
            if (next.put(roster.pool(), roster.settle(old.get(roster.pool()))) != null)
                throw new IllegalArgumentException("Duplicate provider team");
        }
        long receipt = saved.reservoirs().rosterRevision();
        if (!next.equals(old) || receipt == 0) receipt = Math.incrementExact(receipt);
        return saved.reservoirs().reconcile(ready, receipt, next.values());
    }
}
