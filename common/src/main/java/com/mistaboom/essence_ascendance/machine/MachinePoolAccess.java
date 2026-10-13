package com.mistaboom.essence_ascendance.machine;

import com.mistaboom.essence_ascendance.team.MembershipSnapshot;
import com.mistaboom.essence_ascendance.team.PoolCapability;

import java.util.UUID;

/** One-member specialization used by the shared binding policy. No world-side claim/use entry point. */
public final class MachinePoolAccess {
    private MachinePoolAccess() { }

    public static boolean allowsPersonal(MembershipSnapshot membership, UUID owner, UUID actor,
                                          PoolCapability capability) {
        return membership.isSoloFor(owner)
                && owner.equals(actor) && membership.allows(actor, capability);
    }

}
