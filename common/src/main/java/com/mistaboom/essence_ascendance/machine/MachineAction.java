package com.mistaboom.essence_ascendance.machine;

import com.mistaboom.essence_ascendance.team.PoolCapability;

/** One capability translation for every authenticated machine entry point. */
public enum MachineAction {
    USE(PoolCapability.USE), INVENTORY(PoolCapability.USE), FOCUS(PoolCapability.CONFIGURE),
    CONFIGURE(PoolCapability.CONFIGURE), LINK(PoolCapability.CONFIGURE),
    CONTRIBUTE(PoolCapability.USE), SPEND(PoolCapability.SPEND), REBIND(PoolCapability.MANAGE),
    BREAK(PoolCapability.MANAGE);
    public final PoolCapability capability;
    MachineAction(PoolCapability capability) { this.capability = capability; }
}
