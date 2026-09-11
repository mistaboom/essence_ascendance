package com.mistaboom.essence_ascendance.skill.effect;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable, server-owned transient stacks. A chain refreshes one shared deadline;
 * independent stacks retain their individual deadlines unless explicitly refreshed.
 * Eligibility, damage, targets and other skill-specific rules belong to the handler.
 */
public final class TimedStackState implements SkillEffectState {
    private static final int MAX_STACKS = 1_000;
    private final boolean sharedDeadline;
    private final List<Long> deadlines = new ArrayList<>();

    private TimedStackState(boolean sharedDeadline) {
        this.sharedDeadline = sharedDeadline;
    }

    public static TimedStackState shared() { return new TimedStackState(true); }
    public static TimedStackState independent() { return new TimedStackState(false); }

    /** Copies existing state, never retaining a mutable caller-owned collection. */
    public static TimedStackState independent(List<Long> initialExpiries) {
        TimedStackState state = independent();
        initialExpiries.stream().filter(expiry -> expiry != null).sorted()
                .forEach(state.deadlines::add);
        return state;
    }

    /** Exact-deadline expiry and cap changes are applied before any grant or query. */
    public void reconcile(long now, int maxStacks) {
        deadlines.removeIf(expiry -> expiry <= now);
        int cap = cap(maxStacks);
        if (deadlines.size() > cap) deadlines.subList(cap, deadlines.size()).clear();
    }

    /**
     * Grants at most one stack. At cap an independent grant leaves timers alone;
     * refreshAll or a shared-deadline policy refreshes every retained stack.
     */
    public void grant(long now, int maxStacks, int durationTicks, boolean refreshAll) {
        reconcile(now, maxStacks);
        long expiry = SkillEffectMath.expiresAt(now, durationTicks);
        if (deadlines.size() < cap(maxStacks)) deadlines.add(expiry);
        if (sharedDeadline || refreshAll) deadlines.replaceAll(ignored -> expiry);
        deadlines.sort(Long::compareTo);
    }

    /** Call reconcile before read-only queries when time or tuning may have changed. */
    public int count() { return deadlines.size(); }
    public long nextExpiry() { return deadlines.isEmpty() ? 0L : deadlines.get(0); }
    public List<Long> expiries() { return List.copyOf(deadlines); }
    @Override public void clear() { deadlines.clear(); }

    private static int cap(int maximum) { return Math.max(0, Math.min(MAX_STACKS, maximum)); }
}
