package com.mistaboom.essence_ascendance.gameplay;

import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Server-session replay guard. Stores values only; expires on delivery and never evicts live receipts. */
public final class CommittedReceiptWindow {
    public static final int MAX_RECEIPTS = 4096;
    public static final long LIFETIME_TICKS = 200;
    private final Map<Key, Long> receipts = new HashMap<>();
    // Index only live receipts. Delivery examines expired tick buckets, not every live key.
    private final TreeMap<Long, Set<Key>> byTick = new TreeMap<>();
    private record Key(UUID actor, String action) { }

    public boolean accept(UUID actor, String action, long committedTick, long now) {
        if (actor == null || action == null || action.isBlank() || action.length() > 256
                || committedTick < 0 || now < committedTick || now - committedTick >= LIFETIME_TICKS) return false;
        while (!byTick.isEmpty() && byTick.firstKey() <= now - LIFETIME_TICKS)
            discard(byTick.pollFirstEntry().getValue());
        // A server clock rewind invalidates receipts from its previous future as before.
        while (!byTick.isEmpty() && byTick.lastKey() > now)
            discard(byTick.pollLastEntry().getValue());
        if (receipts.size() >= MAX_RECEIPTS) return false;
        Key key = new Key(actor, action);
        if (receipts.putIfAbsent(key, committedTick) != null) return false;
        byTick.computeIfAbsent(committedTick, ignored -> new HashSet<>()).add(key);
        return true;
    }

    private void discard(Set<Key> keys) { keys.forEach(receipts::remove); }

    public int size() { return receipts.size(); }
}
