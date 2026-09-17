package com.mistaboom.essence_ascendance.damage;

/** Native client hurt packets and native health synchronization share one per-entity presentation batch. */
public interface DamageFeedbackAccess {
    DamageFeedbackState essenceAscendance$damageFeedback();
}
