package com.mistaboom.essence_ascendance.client.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded, in-memory navigation snapshots, ordered by the most recent save.
 * Keys and snapshots must be immutable values: IDs, offsets and typed records,
 * never a live screen, player, menu, draft or transaction. A screen captures a
 * session when it opens so its late removal cannot repopulate a cleared session.
 * There is deliberately no serialization or ownership of a host's lifecycle.
 */
public final class UiNavigationMemory<K, S> {
    /** Opaque identity only; retaining this token does not retain the memory or its owner. */
    public static final class Session {
        private Session() { }
    }

    private final int capacity;
    private final Map<K, S> recent = new LinkedHashMap<>();
    private Session session = new Session();

    public UiNavigationMemory(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Navigation memory capacity must be positive");
        this.capacity = capacity;
    }

    public Session session() { return session; }

    public S recall(Session ownerSession, K key, S initial) {
        Objects.requireNonNull(key, "Navigation key");
        Objects.requireNonNull(initial, "Initial navigation");
        return ownerSession == session ? recent.getOrDefault(key, initial) : initial;
    }

    /** Returns false when a screen from an earlier session attempts a late save. */
    public boolean remember(Session ownerSession, K key, S snapshot) {
        Objects.requireNonNull(key, "Navigation key");
        Objects.requireNonNull(snapshot, "Navigation snapshot");
        if (ownerSession != session) return false;
        recent.remove(key);
        recent.put(key, snapshot);
        while (recent.size() > capacity) recent.remove(recent.keySet().iterator().next());
        return true;
    }

    /** Only the connection/session owner should call this, never a temporary synchronization wait. */
    public void clearSession() {
        recent.clear();
        session = new Session();
    }
}
