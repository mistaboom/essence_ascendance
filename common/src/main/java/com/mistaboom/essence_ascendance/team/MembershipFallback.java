package com.mistaboom.essence_ascendance.team;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;

/** A native fallback is a durable identity transition, never an authorization bypass or money copy. */
public final class MembershipFallback {
    private MembershipFallback() { }

    public static boolean activate(EssenceSavedData saved, String actualFailure) {
        return saved.fallbackToNative(actualFailure);
    }
}
