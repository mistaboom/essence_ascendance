package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.gameplay.CommittedReceiptWindow;
import com.mistaboom.essence_ascendance.gameplay.RodOutcome;
import java.util.*;
import java.util.function.IntConsumer;

/** Thresholds consume causal facts; the physics/tracker knows no achievement names or IDs. */
public final class RodAchievementRules {
    private final CommittedReceiptWindow receipts = new CommittedReceiptWindow();
    private final Map<Key, Long> reached = new HashMap<>();
    private record Key(UUID actor, String action, RodOutcome.Kind kind) { }
    public boolean consume(RodOutcome fact, long now, boolean eligible, IntConsumer award) {
        if (fact == null || !eligible || fact.actor() == null || fact.kind() == null || fact.dimension() == null
                || !Double.isFinite(fact.distance()) || fact.distance() < 0 || !Double.isFinite(fact.initialRange())
                || fact.initialRange() < 0) return false;
        int id = switch (fact.kind()) {
            case ANCHORED_WATER_DISPLACEMENT -> fact.distance() >= 20 ? 1 : 0;
            case RELEASED_HOSTILE_DISPLACEMENT -> fact.distance() >= 20 ? 3 : 0;
            case HOSTILE_REELED -> fact.initialRange() >= 8 ? 4 : 0;
            case ITEM_REELED -> fact.initialRange() >= 8 ? 7 : 0;
            case SUPPORTED_GRAPPLE_LANDING -> fact.distance() >= 10 ? 5 : 0;
            case LAUNCHED_HOSTILE_FATAL_FALL -> 2;
        };
        if (id == 0 || fact.actionId() == null || fact.actionId().length() > 180
                || !receipts.accept(fact.actor(), fact.actionId() + ":" + fact.kind() + ":" + fact.committedTick(), fact.committedTick(), now)) return false;
        reached.entrySet().removeIf(e -> now < e.getValue() || now - e.getValue() >= 200);
        Key key = new Key(fact.actor(),fact.actionId(),fact.kind());
        if (reached.containsKey(key) || reached.size() >= 1536) return false;
        reached.put(key,now); award.accept(id); return true;
    }
}
