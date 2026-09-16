package com.mistaboom.essence_ascendance.vitality;

/** Exact native timer access used only to remove transient acceleration on eligibility/lifecycle loss. */
public interface NaturalRecoveryClockAccess {
    int essenceAscendance$naturalTimer();
    void essenceAscendance$naturalTimer(int value);
    void essenceAscendance$naturalSurplus(int value);
}
