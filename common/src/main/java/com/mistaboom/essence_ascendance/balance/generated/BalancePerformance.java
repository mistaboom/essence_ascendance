package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.Gson;
import com.mistaboom.essence_ascendance.EssenceAscendance;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Bounded operation diagnostics. Never owns balance objects, worlds, players, or file contents. */
public final class BalancePerformance {
    private static final ThreadLocal<Operation> CURRENT = new ThreadLocal<>();
    private static final String SESSION = UUID.randomUUID().toString().substring(0, 8);
    private static final AtomicLong IDS = new AtomicLong();
    private static final Gson JSON = new Gson();
    private static final int MAX_FIELDS = 128, MAX_PHASES = 256, MAX_PROGRESS = 128;
    private static final long PROGRESS_INTERVAL = TimeUnit.SECONDS.toNanos(1);
    private static volatile Snapshot lastSnapshot;
    private static final Scope NOOP = () -> { };

    private BalancePerformance() { }

    private static final class Heartbeats {
        private static final java.util.concurrent.ScheduledThreadPoolExecutor TIMER = create();
        private static java.util.concurrent.ScheduledThreadPoolExecutor create() {
            var timer = new java.util.concurrent.ScheduledThreadPoolExecutor(1, work -> {
                    Thread thread = new Thread(work, "essence-balance-performance");
                    thread.setDaemon(true);
                    return thread;
                });
            timer.setRemoveOnCancelPolicy(true);
            return timer;
        }
    }

    public static Operation begin(String type, String trigger) {
        Operation operation = begin(type, trigger, System::nanoTime,
                line -> EssenceAscendance.LOGGER.info("Balance performance {}", line));
        operation.heartbeat = Heartbeats.TIMER.scheduleAtFixedRate(operation::heartbeat, 10, 10, TimeUnit.SECONDS);
        return operation;
    }

    // Deterministic seam: tests use a fake monotonic clock and no background work.
    static Operation begin(String type, String trigger, LongSupplier clock, Consumer<String> output) {
        Operation parent = CURRENT.get();
        Operation operation = new Operation(type, trigger, clock, output, parent);
        if (parent != null) operation.field(operation.details, "parent_operation_id", parent.id);
        CURRENT.set(operation);
        operation.emit("begin", operation.start);
        return operation;
    }

    public static Scope phase(String name) {
        Operation operation = CURRENT.get();
        return operation == null ? NOOP : operation.enter(name);
    }
    public static void count(String name, long count) {
        Operation operation = CURRENT.get();
        if (operation != null) operation.field(operation.counts, name, count);
    }
    public static void increment(String name) {
        Operation operation = CURRENT.get();
        if (operation != null) operation.increment(name);
    }
    public static void flag(String name, boolean value) {
        Operation operation = CURRENT.get();
        if (operation != null) operation.field(operation.flags, name, value);
    }
    public static void detail(String name, String value) {
        Operation operation = CURRENT.get();
        if (operation != null) operation.field(operation.details, name, bounded(value));
    }
    public static Snapshot currentSnapshot() {
        Operation operation = CURRENT.get();
        return operation == null ? null : operation.snapshot();
    }
    public static Snapshot lastSnapshot() { return lastSnapshot; }
    private static String bounded(String text) {
        if (text == null) return "";
        return text.length() <= 256 ? text : text.substring(0, 256);
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable { @Override void close(); }
    public record PhaseTiming(String path, long invocations, long inclusiveNanos, long exclusiveNanos) { }
    public record Snapshot(String id, String operation, String trigger, String startedAt, String outcome,
                           boolean finished, long elapsedNanos, long unattributedNanos,
                           String activePhase, String lastEnteredPhase, String failureType, String failurePhase,
                           List<PhaseTiming> phases, Map<String, Long> counts, Map<String, Boolean> flags,
                           Map<String, String> details, int suppressedProgressEvents) { }

    public static final class Operation implements AutoCloseable {
        private final String id = SESSION + "-" + IDS.incrementAndGet();
        private final String trigger, startedAt = Instant.now().toString();
        private String type, outcome = "running", lastEntered = "", failureType = "", failurePhase = "";
        private final Operation parent;
        private final LongSupplier clock;
        private final Consumer<String> output;
        private final long start;
        private long ended, lastProgress, rootPhaseNanos;
        private boolean finished;
        private int progressEvents, suppressedProgress;
        private final ArrayDeque<Frame> stack = new ArrayDeque<>();
        private final Map<String, Totals> phases = new LinkedHashMap<>();
        private final Map<String, Long> counts = new LinkedHashMap<>();
        private final Map<String, Boolean> flags = new LinkedHashMap<>();
        private final Map<String, String> details = new LinkedHashMap<>();
        private ScheduledFuture<?> heartbeat;

        private Operation(String type, String trigger, LongSupplier clock, Consumer<String> output, Operation parent) {
            this.type = bounded(type); this.trigger = bounded(trigger); this.clock = clock; this.output = output;
            this.parent = parent;
            this.start = clock.getAsLong(); this.lastProgress = start;
        }
        public synchronized void type(String type) { this.type = bounded(type); }
        public synchronized void complete(String outcome) { this.outcome = bounded(outcome); }
        public synchronized void fail(Throwable failure) {
            outcome = "failed";
            failureType = failure.getClass().getName();
            if (failurePhase.isEmpty()) failurePhase = stack.isEmpty() ? lastEntered : stack.peek().path;
        }
        private synchronized <T> void field(Map<String, T> fields, String name, T value) {
            String key = bounded(name);
            if (fields.size() < MAX_FIELDS || fields.containsKey(key)) fields.put(key, value);
        }
        private synchronized void increment(String name) {
            String key = bounded(name);
            if (counts.size() < MAX_FIELDS || counts.containsKey(key)) counts.merge(key, 1L, Long::sum);
        }
        private synchronized Scope enter(String name) {
            String component = bounded(name);
            String path = stack.isEmpty() ? component : stack.peek().path + "/" + component;
            if (!phases.containsKey(path) && phases.size() >= MAX_PHASES) return NOOP;
            if (stack.size() >= 32) return NOOP;
            phases.computeIfAbsent(path, ignored -> new Totals());
            long now = clock.getAsLong();
            Frame frame = new Frame(path, now);
            stack.push(frame); lastEntered = path;
            progress("phase", now);
            return () -> leave(frame);
        }
        private synchronized void leave(Frame frame) {
            if (frame.closed) return;
            if (stack.peek() != frame) throw new IllegalStateException("Balance phase scopes closed out of order");
            frame.closed = true; stack.pop();
            long duration = Math.max(0, clock.getAsLong() - frame.start);
            Totals totals = phases.get(frame.path);
            totals.calls++; totals.inclusive += duration; totals.exclusive += Math.max(0, duration - frame.children);
            if (stack.isEmpty()) rootPhaseNanos += duration; else stack.peek().children += duration;
        }
        private synchronized void heartbeat() {
            if (!finished) progress("heartbeat", clock.getAsLong());
        }
        private void progress(String event, long now) {
            if (progressEvents >= MAX_PROGRESS || now - lastProgress < PROGRESS_INTERVAL) {
                suppressedProgress++; return;
            }
            progressEvents++; lastProgress = now;
            emit(event, now);
        }
        private void emit(String event, long now) {
            // Progress is small and bounded; detailed timings appear once in the final record.
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("event", event); record.put("id", id); record.put("operation", type); record.put("trigger", trigger);
            record.put("elapsedMillis", Math.max(0, now - start) / 1_000_000L);
            record.put("activePhase", stack.isEmpty() ? "" : stack.peek().path);
            if (event.equals("begin")) record.put("startedAt", startedAt);
            if (event.equals("end") || event.equals("failure")) record.put("result", snapshot());
            try { output.accept(JSON.toJson(record)); }
            catch (RuntimeException ignored) { /* Diagnostics cannot invalidate balance authority. */ }
        }
        public synchronized Snapshot snapshot() {
            long now = finished ? ended : clock.getAsLong();
            List<PhaseTiming> timings = new ArrayList<>();
            phases.forEach((path, totals) -> timings.add(new PhaseTiming(path, totals.calls, totals.inclusive, totals.exclusive)));
            return new Snapshot(id, type, trigger, startedAt, outcome, finished, Math.max(0, now - start),
                    Math.max(0, now - start - rootPhaseNanos), stack.isEmpty() ? "" : stack.peek().path,
                    lastEntered, failureType, failurePhase, List.copyOf(timings), Map.copyOf(counts), Map.copyOf(flags),
                    Map.copyOf(details), suppressedProgress);
        }
        @Override public synchronized void close() {
            if (finished) return;
            if (!stack.isEmpty()) throw new IllegalStateException("Unclosed balance phase scopes");
            if (outcome.equals("running")) outcome = "incomplete";
            ended = clock.getAsLong(); finished = true;
            if (heartbeat != null) heartbeat.cancel(false);
            lastSnapshot = snapshot();
            emit(failureType.isEmpty() ? "end" : "failure", ended);
            if (CURRENT.get() == this) {
                if (parent == null) CURRENT.remove(); else CURRENT.set(parent);
            }
        }
    }
    private static final class Frame {
        final String path;
        final long start;
        long children;
        boolean closed;
        Frame(String path, long start) { this.path = path; this.start = start; }
    }
    private static final class Totals { long calls, inclusive, exclusive; }
}
