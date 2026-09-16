package com.mistaboom.essence_ascendance.vitality;

import java.util.Objects;
import java.util.UUID;

/** One target per owner. An exact timeout, miss or clock rollback ends the chain. */
public final class RecoveryChain {
    private UUID target;
    private long lastHit = Long.MIN_VALUE;
    private int hits;

    public int hit(UUID next, long now, int timeout, int cap) {
        expire(now, timeout);
        if (!Objects.equals(target, next)) clear();
        target = Objects.requireNonNull(next);
        lastHit = now;
        hits = Math.min(Math.max(1, cap), hits + 1);
        return hits;
    }
    public void expire(long now, int timeout) {
        if (hits > 0 && (now < lastHit || now - lastHit >= timeout)) clear();
    }
    public void clear() { target = null; lastHit = Long.MIN_VALUE; hits = 0; }
    public UUID target() { return target; }
    public int hits() { return hits; }
    public long expiresAt(int timeout) { return hits == 0 ? 0 : lastHit + timeout; }
}
