package com.mistaboom.essence_ascendance.gathering;

/** Minimal native hook surface shared by the mixin and generated fishing timing logic. */
public interface GatheringFishingHookAccess {
    int essenceAscendance$nibble();
    void essenceAscendance$setNibble(int ticks);
    int essenceAscendance$timeUntilLured();
    void essenceAscendance$setTimeUntilLured(int ticks);
    int essenceAscendance$timeUntilHooked();
    void essenceAscendance$setTimeUntilHooked(int ticks);
    int essenceAscendance$lureBefore();
    void essenceAscendance$setLureBefore(int ticks);
    int essenceAscendance$hookBefore();
    void essenceAscendance$setHookBefore(int ticks);
    int essenceAscendance$nibbleBefore();
    void essenceAscendance$setNibbleBefore(int ticks);
    double essenceAscendance$lureCarry();
    void essenceAscendance$setLureCarry(double carry);
    double essenceAscendance$hookCarry();
    void essenceAscendance$setHookCarry(double carry);
    double essenceAscendance$reelCarry();
    void essenceAscendance$setReelCarry(double carry);
}
