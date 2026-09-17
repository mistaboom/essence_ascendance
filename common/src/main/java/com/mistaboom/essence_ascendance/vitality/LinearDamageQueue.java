package com.mistaboom.essence_ascendance.vitality;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded, source-preserving linear obligations. Advancing is in online ticks, never wall-clock time. */
public final class LinearDamageQueue<K> {
    public static final int MAX_ENTRIES = 256; // memory safety, not balance; excess is applied immediately
    public static final int MAX_TICKS = 72_000;
    public record Debt<K>(K source, double remaining, int ticks) {
        public Debt {
            Objects.requireNonNull(source);
            if (!Double.isFinite(remaining) || remaining <= 0 || ticks < 1 || ticks > MAX_TICKS)
                throw new IllegalArgumentException("Invalid delayed damage obligation");
        }
    }
    public record Payment<K>(K source, double amount) { }
    private static final class Entry<K> {
        final K source;
        double remaining;
        int ticks;
        Entry(K source, double remaining, int ticks) { this.source = source; this.remaining = remaining; this.ticks = ticks; }
        Debt<K> snapshot() { return new Debt<>(source, remaining, ticks); }
    }
    private final List<Entry<K>> entries = new ArrayList<>();
    /** Returns false instead of dropping debt when the safety bound is reached. */
    public boolean enqueue(K source, double amount, int ticks) {
        new Debt<>(source, amount, ticks); // validate before mutation
        Entry<K> next = new Entry<>(source, amount, ticks);
        for (int i = 0; i < entries.size(); i++) {
            Entry<K> current = entries.get(i);
            if (current.ticks == ticks && current.source.equals(source)) {
                double merged = current.remaining + amount;
                if (!Double.isFinite(merged)) return false;
                current.remaining = merged;
                return true;
            }
        }
        if (entries.size() >= MAX_ENTRIES) return false;
        entries.add(next);
        return true;
    }
    /** Advances only accepted native payments. Rejection pauses the original obligation without losing it.
     * The callback may clear the ledger on death; snapshot iteration never resurrects that cleared debt. */
    public void settle(java.util.function.Predicate<Payment<K>> accepted) {
        settleAmount(payment -> accepted.test(payment) ? payment.amount() : 0);
    }
    /** A loader/mod may reduce an accepted payment; only the measured amount is forgiven. */
    public void settleAmount(java.util.function.ToDoubleFunction<Payment<K>> paid) {
        for (Entry<K> debt : List.copyOf(entries)) {
            if (!entries.contains(debt)) continue;
            double payment = debt.remaining / debt.ticks;
            double actual = paid.applyAsDouble(new Payment<>(debt.source, payment));
            if (!Double.isFinite(actual) || actual <= 0) continue;
            actual = Math.min(payment, actual);
            int index = entries.indexOf(debt);
            if (index < 0) continue;
            double remaining = Math.max(0, debt.remaining - actual);
            if (remaining == 0) entries.remove(index);
            else { debt.remaining = remaining; debt.ticks = Math.max(1, debt.ticks - 1); }
        }
    }
    /** Pure-model convenience: all payments accepted. */
    public List<Payment<K>> advance() {
        List<Payment<K>> payments = new ArrayList<>(entries.size());
        settle(payment -> { payments.add(payment); return true; });
        return List.copyOf(payments);
    }
    /** Proportional cancellation preserves the remaining payout cadence of every source. */
    public double purge(double fraction) {
        if (!Double.isFinite(fraction) || fraction < 0 || fraction > 1) throw new IllegalArgumentException("Invalid purge fraction");
        double before = total();
        if (fraction == 1) entries.clear();
        else if (fraction > 0) {
            for (int i = entries.size() - 1; i >= 0; i--) {
                Entry<K> debt = entries.get(i);
                double remaining = debt.remaining * (1 - fraction);
                if (remaining <= 0) entries.remove(i);
                else debt.remaining = remaining;
            }
        }
        return Math.max(0, before - total());
    }
    /** Cancel a measured healing amount, distributing it across all sources without moving deadlines. */
    public double recover(double amount) {
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Invalid recovery amount");
        double total = total();
        if (amount == 0 || total == 0) return 0;
        return purge(Math.min(1, amount / total));
    }
    public double total() { return entries.stream().mapToDouble(d -> d.remaining).sum(); }
    public double nextPayment() { return entries.stream().mapToDouble(d -> d.remaining / d.ticks).sum(); }
    public int ticksRemaining() { return entries.stream().mapToInt(d -> d.ticks).max().orElse(0); }
    public List<Debt<K>> snapshot() { return entries.stream().map(Entry::snapshot).toList(); }
    public boolean isEmpty() { return entries.isEmpty(); }
    public void clear() { entries.clear(); }
}
