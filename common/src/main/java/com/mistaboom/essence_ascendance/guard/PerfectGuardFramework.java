package com.mistaboom.essence_ascendance.guard;

/** Exact half-open timing boundary: readiness tick through readiness + window - 1. */
public final class PerfectGuardFramework {
    private PerfectGuardFramework() { }
    public static boolean perfect(long now, long readyTick, int windowTicks, boolean nativeReady, double blocked) {
        return nativeReady && Double.isFinite(blocked) && blocked > 0 && readyTick >= 0
                && windowTicks > 0 && now >= readyTick && now - readyTick < windowTicks;
    }
}
