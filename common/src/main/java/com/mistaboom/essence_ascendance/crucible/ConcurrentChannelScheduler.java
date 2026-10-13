package com.mistaboom.essence_ascendance.crucible;

import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBalance;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Server-thread registrations, exact bandwidth and equal-share policy. Currency and effects
 * remain in transactional consumers. Four-tick accounting batches preserve the Infuser interval.
 * Only fractional twentieths carry between batches; idle whole credits are discarded. */
public final class ConcurrentChannelScheduler<S, R> {
    public static final int INTERVAL = 4;
    /** Exact presentation conversion of a completed batch; never used as an allocation promise. */
    public static java.math.BigInteger sampledPerSecond(long batchAmount) {
        return java.math.BigInteger.valueOf(batchAmount).multiply(java.math.BigInteger.valueOf(20 / INTERVAL));
    }
    public interface Consumer {
        boolean valid();
        long demand();
        /** Negative means no receiver processing cap. */
        long ratePerSecond();
        /** Prepare, validate and atomically deliver at most the grant; return committed input. */
        long deliver(long grant);
    }
    public record Key<S, R>(S source, R receiver) {}
    public record Session<S, R>(Key<S, R> key, long generation) {}
    /** Last completed accounting batch, in essence units (not a promised future rate). */
    public record Feedback(long tick, long allocated, long transferred, boolean consuming) {}
    private static final class Budget {
        int remainder;
        long whole;
        void advance(long rate) {
            if (rate <= 0) return;
            var slice = EssenceInfuserBalance.throughputForTicks(rate, 1, remainder);
            remainder = slice.remainderTwentieths();
            whole = saturatedAdd(whole, slice.amount());
        }
    }
    private final class Source {
        final Object identity;
        final LongSupplier rate;
        final BooleanSupplier valid;
        final Budget budget = new Budget();
        long cursor;
        Source(Object identity, LongSupplier rate, BooleanSupplier valid) {
            this.identity = identity; this.rate = rate; this.valid = valid;
        }
    }
    private final class Entry {
        final Session<S, R> session;
        final Object identity;
        final long revision;
        final Source source;
        final Consumer consumer;
        long feedbackTick = Long.MIN_VALUE, allocated, transferred;
        boolean consuming;
        Entry(Session<S, R> session, Object identity, long revision, Source source, Consumer consumer) {
            this.session = session; this.identity = identity; this.revision = revision;
            this.source = source; this.consumer = consumer;
        }
    }
    private final Map<S, Source> sources = new LinkedHashMap<>();
    private final Map<Key<S, R>, Entry> entries = new LinkedHashMap<>();
    private final Map<R, Budget> receivers = new HashMap<>();
    private long generation, sourceCursor;
    private long lastTick = Long.MIN_VALUE;

    public void source(S key, Object identity, LongSupplier rate, BooleanSupplier valid) {
        Source old = sources.get(key);
        if (old != null && old.identity == identity) return;
        if (old != null) removeSource(key, old.identity);
        sources.put(key, new Source(identity, rate, valid));
    }
    public Session<S, R> register(S source, R receiver, Object identity, long revision, Consumer consumer) {
        Source station = Objects.requireNonNull(sources.get(source), "Unregistered source");
        Key<S, R> key = new Key<>(source, receiver);
        Entry old = entries.get(key);
        if (old != null && old.source == station && old.identity == identity && old.revision == revision)
            return old.session;
        Session<S, R> session = new Session<>(key, Math.incrementExact(generation));
        generation = session.generation();
        entries.put(key, new Entry(session, identity, revision, station, consumer));
        return session;
    }
    /** A player may change sources, but cannot receive concurrent physical-source budgets.
     * Validate the destination before replacing any registration. Repeated Start on the same
     * live source keeps its generation, while stale registrations for the receiver are removed. */
    public Session<S, R> registerExclusive(S source, R receiver, Object identity, long revision, Consumer consumer) {
        Source station = Objects.requireNonNull(sources.get(source), "Unregistered source");
        if (!station.valid.getAsBoolean() || !consumer.valid()) return null;
        Session<S, R> session = register(source, receiver, identity, revision, consumer);
        entries.entrySet().removeIf(entry -> entry.getKey().receiver().equals(receiver)
                && !entry.getValue().session.equals(session));
        return session;
    }
    public boolean current(Session<S, R> session) {
        Entry entry = session == null ? null : entries.get(session.key());
        return entry != null && entry.session.equals(session) && sources.get(session.key().source()) == entry.source;
    }
    public void remove(Session<S, R> session) {
        if (current(session)) entries.remove(session.key());
    }
    public void removeSource(S key, Object identity) {
        Source source = sources.get(key);
        if (source == null || source.identity != identity) return;
        sources.remove(key);
        entries.entrySet().removeIf(e -> e.getValue().source == source);
    }
    public boolean sourceCurrent(S key, Object identity) { return sources.containsKey(key) && sources.get(key).identity == identity; }
    public void removeReceiver(R receiver, Object identity) {
        entries.entrySet().removeIf(e -> e.getKey().receiver().equals(receiver) && e.getValue().identity == identity);
    }
    public List<Session<S, R>> sessions() { return entries.values().stream().map(e -> e.session).toList(); }
    public Feedback feedback(Session<S, R> session) {
        if (!current(session)) return new Feedback(Long.MIN_VALUE, 0, 0, false);
        Entry entry = entries.get(session.key());
        boolean recent = entry.feedbackTick != Long.MIN_VALUE && lastTick - entry.feedbackTick <= INTERVAL * 2L;
        return new Feedback(entry.feedbackTick, recent ? entry.allocated : 0, recent ? entry.transferred : 0,
                entry.consumer.valid() && entry.consumer.demand() > 0 && entry.consumer.ratePerSecond() != 0);
    }
    public long demand(Session<S, R> session) {
        if (!current(session)) return 0;
        Consumer consumer = entries.get(session.key()).consumer;
        return consumer.valid() ? Math.max(0, consumer.demand()) : 0;
    }
    public void clear() { sources.clear(); entries.clear(); receivers.clear(); lastTick = Long.MIN_VALUE; }

    public void tick(long tick) {
        if (tick == lastTick) return;
        lastTick = tick;
        for (var item : List.copyOf(sources.entrySet())) {
            Source source = item.getValue();
            if (!source.valid.getAsBoolean()) { removeSource(item.getKey(), source.identity); continue; }
            source.budget.advance(Math.max(0, source.rate.getAsLong()));
        }
        for (Entry entry : List.copyOf(entries.values()))
            if (!entry.consumer.valid()) remove(entry.session);
        Map<R, Long> caps = new HashMap<>();
        for (Entry entry : List.copyOf(entries.values())) {
            long rate = entry.consumer.ratePerSecond();
            if (rate >= 0) caps.merge(entry.session.key().receiver(), rate, Math::min);
        }
        // Only live capped receivers need carry. Logout, invalidation and source removal
        // must not retain a budget for every player/machine ever seen by this server.
        receivers.keySet().retainAll(caps.keySet());
        caps.forEach((receiver, rate) -> receivers.computeIfAbsent(receiver, ignored -> new Budget()).advance(rate));
        if (Math.floorMod(tick, INTERVAL) != 0) return;
        for (Entry entry : entries.values()) {
            entry.feedbackTick = tick; entry.allocated = 0; entry.transferred = 0;
            entry.consuming = entry.consumer.valid() && entry.consumer.demand() > 0 && entry.consumer.ratePerSecond() != 0;
        }
        Map<Source, List<Entry>> groups = new IdentityHashMap<>();
        for (Entry entry : List.copyOf(entries.values())) groups.computeIfAbsent(entry.source, ignored -> new ArrayList<>()).add(entry);
        List<Map.Entry<S, Source>> ordered = new ArrayList<>();
        for (var item : List.copyOf(sources.entrySet())) if (item.getValue().budget.whole > 0
                && groups.getOrDefault(item.getValue(), List.of()).stream().anyMatch(e -> current(e.session) && e.consumer.valid() && e.consumer.demand() > 0
                        && (!caps.containsKey(e.session.key().receiver()) || receivers.get(e.session.key().receiver()).whole > 0))) ordered.add(item);
        if (!ordered.isEmpty()) Collections.rotate(ordered, -(int) Math.floorMod(sourceCursor++, ordered.size()));
        for (var item : ordered) dispatch(item.getValue(), groups.getOrDefault(item.getValue(), List.of()), caps);
        sources.values().forEach(source -> source.budget.whole = 0);
        receivers.values().forEach(budget -> budget.whole = 0);
    }
    private void dispatch(Source source, List<Entry> channels, Map<R, Long> caps) {
        Set<Entry> exhausted = new HashSet<>();
        long remaining = source.budget.whole;
        if (remaining == 0 || channels.isEmpty()) return;
        int offset = (int) Math.floorMod(source.cursor, channels.size());
        while (remaining > 0 && exhausted.size() < channels.size()) {
            long[] demand = new long[channels.size()];
            for (int i = 0; i < demand.length; i++) {
                Entry entry = channels.get(i);
                if (exhausted.contains(entry) || !current(entry.session) || !entry.consumer.valid()) continue;
                demand[i] = Math.max(0, entry.consumer.demand());
                R receiver = entry.session.key().receiver();
                if (caps.containsKey(receiver)) demand[i] = Math.min(demand[i], receivers.get(receiver).whole);
            }
            long[] grants = allocate(remaining, demand, offset);
            boolean committed = false;
            int exhaustedBefore = exhausted.size();
            for (int j = 0; j < channels.size(); j++) {
                int i = (offset + j) % channels.size();
                Entry entry = channels.get(i);
                long grant = grants[i];
                if (grant == 0) {
                    if (demand[i] == 0) exhausted.add(entry);
                    continue;
                }
                if (!current(entry.session) || !entry.consumer.valid()) { exhausted.add(entry); continue; }
                // A prior grant can consume shared stock/capacity: prepare against current truth.
                R receiver = entry.session.key().receiver();
                if (caps.containsKey(receiver)) grant = Math.min(grant, receivers.get(receiver).whole);
                grant = Math.min(grant, Math.max(0, entry.consumer.demand()));
                long moved = grant == 0 ? 0 : entry.consumer.deliver(grant);
                entry.allocated = saturatedAdd(entry.allocated, grant);
                entry.transferred = saturatedAdd(entry.transferred, Math.max(0, moved));
                if (moved < 0 || moved > grant) throw new IllegalStateException("Consumer exceeded its grant");
                remaining -= moved;
                if (caps.containsKey(receiver)) receivers.get(receiver).whole -= moved;
                committed |= moved > 0;
                if (moved < grants[i]) exhausted.add(entry);
            }
            if (!committed && exhausted.size() == exhaustedBefore) break;
        }
        if (remaining < source.budget.whole) source.cursor++;
    }
    /** Integer water filling, including redistribution from capped channels and rotating extras. */
    public static long[] allocate(long budget, long[] demand, int cursor) {
        long[] result = new long[demand.length];
        while (budget > 0) {
            int count = 0;
            for (int i = 0; i < demand.length; i++) if (demand[i] > result[i]) count++;
            if (count == 0) break;
            long share = budget / count, extra = budget % count;
            for (int j = 0; j < demand.length; j++) {
                int i = Math.floorMod(cursor + j, demand.length);
                if (demand[i] <= result[i]) continue;
                long offer = share + (extra-- > 0 ? 1 : 0);
                long amount = Math.min(offer, demand[i] - result[i]);
                result[i] += amount; budget -= amount;
            }
        }
        return result;
    }
    public static long saturatedAdd(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }
}
