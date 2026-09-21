package com.mistaboom.essence_ascendance.utility;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Target-local claims and level-local work bounds. Keys are loaded native objects, never chunk loaders. */
public final class SharedTargetWork {
    private static final Map<Object, Map<String, Long>> CLAIMS = new WeakHashMap<>();
    private static final Map<Object, TickBudget> BUDGETS = new WeakHashMap<>();
    private static final Map<Object, Map<PositionChannel, Long>> POSITIONS = new WeakHashMap<>();
    public static final int MAX_WORK_PER_TICK = 4096;
    private SharedTargetWork() { }
    public static boolean claim(Object level, Object target, String channel, long now, int interval) {
        Map<String, Long> claims = CLAIMS.computeIfAbsent(target, ignored -> new HashMap<>());
        Long previous = claims.get(channel);
        if (previous != null && now >= previous && now - previous < Math.max(1, interval)) return false;
        if (!visit(level, now)) return false;
        claims.put(channel, now);
        return true;
    }
    public static boolean visit(Object level, long now) {
        TickBudget budget = BUDGETS.computeIfAbsent(level, ignored -> new TickBudget());
        if (budget.tick != now) { budget.tick = now; budget.used = 0; }
        if (budget.used >= MAX_WORK_PER_TICK) return false;
        budget.used++;
        return true;
    }
    public static boolean claimPosition(Object level, long position, String channel, long now, int interval) {
        Map<PositionChannel, Long> claims = POSITIONS.computeIfAbsent(level, ignored -> new HashMap<>());
        claims.values().removeIf(expires -> expires <= now);
        PositionChannel key = new PositionChannel(position, channel);
        if (claims.containsKey(key) || claims.size() >= MAX_WORK_PER_TICK || !visit(level, now)) return false;
        claims.put(key, now + Math.max(1, interval));
        return true;
    }
    private record PositionChannel(long position, String channel) { }
    private static final class TickBudget { long tick = Long.MIN_VALUE; int used; }
}
