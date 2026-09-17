package com.mistaboom.essence_ascendance.skill.effect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Source-owned shares of one native absorption reservoir. No game objects or balance constants. */
public final class AbsorptionPoolLedger<K> {
    private final Map<K, Pool> pools = new LinkedHashMap<>();
    private static final class Pool { double amount, capacity; }
    public record Drain<K>(double removed, List<K> depleted) {
        public Drain { depleted = List.copyOf(depleted); }
    }
    public List<K> keys() { return List.copyOf(pools.keySet()); }
    public double amount(K key) { Pool pool = pools.get(key); return pool == null ? 0 : pool.amount; }
    public double capacity(K key) { Pool pool = pools.get(key); return pool == null ? 0 : pool.capacity; }
    public double total() { return pools.values().stream().mapToDouble(pool -> pool.amount).sum(); }
    public double external(double nativeAmount) { return Math.max(0, positive(nativeAmount) - total()); }
    public boolean empty() { return pools.isEmpty(); }

    /** Returns only owned points trimmed by the new capacity; this is never a damage break. */
    public double configure(K key, double capacity) {
        java.util.Objects.requireNonNull(key);
        Pool pool = pools.computeIfAbsent(key, ignored -> new Pool());
        double before = pool.amount;
        pool.capacity = positive(capacity);
        pool.amount = Math.min(pool.amount, pool.capacity);
        return before - pool.amount;
    }
    public double grant(K key, double requested) {
        Pool pool = pools.get(key);
        if (pool == null) return 0;
        double granted = Math.min(positive(requested), Math.max(0, pool.capacity - pool.amount));
        pool.amount += granted;
        return granted;
    }
    public double withdraw(K key, double requested) {
        Pool pool = pools.get(key);
        if (pool == null) return 0;
        double removed = Math.min(pool.amount, positive(requested));
        pool.amount -= removed;
        return removed;
    }
    public double remove(K key) { Pool pool = pools.remove(key); return pool == null ? 0 : pool.amount; }

    /** Damage spends owned pools in stable grant-source order before unrelated native absorption. */
    public Drain<K> consume(double nativeLoss) {
        double left = positive(nativeLoss), removed = 0;
        List<K> depleted = new ArrayList<>();
        for (var entry : pools.entrySet()) {
            double before = entry.getValue().amount;
            double spent = Math.min(before, left);
            entry.getValue().amount -= spent;
            left -= spent;
            removed += spent;
            if (before > 0 && entry.getValue().amount == 0) depleted.add(entry.getKey());
            if (left <= 0) break;
        }
        return new Drain<>(removed, depleted);
    }
    /** External edits/cap reductions remove external points first, and never emit a break event. */
    public void fit(double nativeAmount) { consume(Math.max(0, total() - positive(nativeAmount))); }
    public void clear() { pools.clear(); }
    private static double positive(double value) { return Double.isFinite(value) ? Math.max(0, value) : 0; }
}
