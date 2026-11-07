package com.mistaboom.essence_ascendance.gameplay;

import java.util.UUID;

/** Immutable successful rod-operation facts. Distances are native blocks, never lifetime history. */
public record RodOutcome(String actionId, UUID actor, UUID target, String dimension, long committedTick,
                         Kind kind, double distance, double initialRange) {
    public enum Kind { ANCHORED_WATER_DISPLACEMENT, RELEASED_HOSTILE_DISPLACEMENT, HOSTILE_REELED,
        ITEM_REELED, SUPPORTED_GRAPPLE_LANDING, LAUNCHED_HOSTILE_FATAL_FALL }
}
