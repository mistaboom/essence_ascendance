package com.mistaboom.essence_ascendance.crucible;

/** Common authoritative presentation reasons. Clients translate; they do not infer eligibility. */
public enum ChannelReason {
    STOPPED, RECEIVING, NO_SELECTION, WAITING_ESSENCE, SHARING_BANDWIDTH, RECEIVER_CAPPED,
    SOURCE_UNAVAILABLE, OUTPUT_BLOCKED, INVALID_RECIPIENT, AUTHORIZATION_UNRESOLVED, AUTHORIZATION_REVOKED
}
