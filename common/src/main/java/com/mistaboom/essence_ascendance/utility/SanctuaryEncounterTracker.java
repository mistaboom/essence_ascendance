package com.mistaboom.essence_ascendance.utility;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded, actor-local evidence of one continuously targeting encounter; no entities or awards. */
public final class SanctuaryEncounterTracker {
    public static final int MAX_TARGETS = 256;
    public static final int RECENT_DAMAGE_TICKS = 200;
    public static final int MAX_ENCOUNTER_TICKS = 12_000;
    private final Map<UUID, Encounter> targets = new HashMap<>();

    public void started(UUID target, String dimension, long now) {
        prune(now);
        Encounter previous = targets.get(target);
        if (previous == null && targets.size() >= MAX_TARGETS) return;
        boolean damaged = previous != null && previous.damaged
                && elapsed(now, previous.lastDamage) < RECENT_DAMAGE_TICKS;
        targets.put(target, new Encounter(dimension, now, true, damaged,
                previous == null ? Long.MIN_VALUE : previous.lastDamage, now));
    }

    public void ended(UUID target, long now) {
        Encounter entry = targets.get(target);
        if (entry != null) targets.put(target, new Encounter(entry.dimension, entry.started, false,
                entry.damaged, entry.lastDamage, now));
        prune(now);
    }

    /** Also retains a short pre-acquisition rejection record for delayed native retaliation. */
    public void damaged(UUID target, String dimension, long now) {
        prune(now);
        Encounter entry = targets.get(target);
        if (entry == null && targets.size() >= MAX_TARGETS) return;
        if (entry == null || !entry.dimension.equals(dimension))
            entry = new Encounter(dimension, now, false, true, now, now);
        targets.put(target, new Encounter(dimension, entry.started, entry.targeting, true, now, now));
    }

    public boolean undamaged(UUID target, String dimension, long now) {
        prune(now);
        Encounter entry = targets.get(target);
        return entry != null && entry.targeting && !entry.damaged && entry.dimension.equals(dimension)
                && elapsed(now, entry.started) < MAX_ENCOUNTER_TICKS;
    }

    public void remove(UUID target) { targets.remove(target); }
    public void clear() { targets.clear(); }
    public int size() { return targets.size(); }

    private void prune(long now) {
        targets.entrySet().removeIf(value -> {
            Encounter entry = value.getValue();
            return entry.targeting ? elapsed(now, entry.started) >= MAX_ENCOUNTER_TICKS
                    : elapsed(now, entry.lastObserved) >= RECENT_DAMAGE_TICKS;
        });
    }

    private static long elapsed(long now, long then) {
        if (then == Long.MIN_VALUE || now < then) return Long.MAX_VALUE;
        long delta = now - then;
        return delta < 0 ? Long.MAX_VALUE : delta;
    }

    private record Encounter(String dimension, long started, boolean targeting,
                             boolean damaged, long lastDamage, long lastObserved) { }
}
