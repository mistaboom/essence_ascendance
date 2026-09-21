package com.mistaboom.essence_ascendance.vitality;

/** Attached to native timed effects; copies, merges and save/load retain food lineage. */
public interface FoodEffectOrigin {
    boolean essenceAscendance$foodOrigin();
    void essenceAscendance$foodOrigin(boolean value);
    FoodEffectOrigin essenceAscendance$hiddenOrigin();
}
