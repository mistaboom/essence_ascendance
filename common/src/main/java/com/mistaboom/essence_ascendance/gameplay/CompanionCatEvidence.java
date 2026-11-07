package com.mistaboom.essence_ascendance.gameplay;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded loaded-observation continuity, independent of awards and without live entity references. */
public final class CompanionCatEvidence {
    public static final int MAX_CATS = 4_096;
    public static final long RETENTION_TICKS = 200;
    private final Map<UUID, Evidence> cats = new HashMap<>();
    private long nextPrune;
    private static final class Evidence {
        UUID owner;
        long since, seen, damaged = Long.MIN_VALUE;
        String dimension;
        boolean closed;
        Evidence(UUID owner, String dimension, long now) { this.owner=owner; this.dimension=dimension; since=seen=now; }
    }
    public void observe(UUID cat, UUID owner, String dimension, long now) {
        prune(now);
        var evidence = cats.get(cat);
        if (evidence == null) {
            if (cats.size() < MAX_CATS) cats.put(cat,new Evidence(owner,dimension,now));
            return;
        }
        if (evidence.closed || !java.util.Objects.equals(evidence.owner,owner) || !dimension.equals(evidence.dimension)
                || now < evidence.seen || now-evidence.seen>1) {
            evidence.owner=owner; evidence.dimension=dimension; evidence.since=now;
        }
        evidence.closed=false;
        evidence.seen=now;
    }
    public void damaged(UUID cat, UUID owner, String dimension, long now) {
        observe(cat,owner,dimension,now);
        var evidence=cats.get(cat); if(evidence!=null)evidence.damaged=now;
    }
    public boolean undamaged(UUID cat, UUID owner, String dimension, long fightStart, long now) {
        var evidence=cats.get(cat);
        return evidence!=null && !evidence.closed && owner.equals(evidence.owner) && dimension.equals(evidence.dimension)
                && fightStart>=0 && fightStart<=now && evidence.since<=fightStart && now>=evidence.seen && now-evidence.seen<=1
                && evidence.damaged<fightStart;
    }
    public UUID owner(UUID cat) { var evidence=cats.get(cat); return evidence==null||evidence.closed?null:evidence.owner; }
    /** Close loaded continuity while retaining bounded known damage through same-tick reload/ownership changes. */
    public void removed(UUID cat) { var evidence=cats.get(cat); if(evidence!=null)evidence.closed=true; }
    public int size() { return cats.size(); }
    private void prune(long now) {
        if(now<nextPrune)return;
        cats.values().removeIf(evidence -> now<evidence.seen || now-evidence.seen>=RETENTION_TICKS);
        nextPrune=now+RETENTION_TICKS;
    }
}
