package com.mistaboom.essence_ascendance.balance.engine;

import java.util.*;

/** Operation-local material accounting. Branches copy the ledger and commit only successful plans.
 * Stocks are guaranteed items, never expected chance output. A setup item remains owned but can
 * still be consumed by a later recipe; it cannot be borrowed after that consumption. */
public final class AcquisitionLedger {
    private final Map<String, Long> inventory;
    private final Map<String, Long> drawn;
    private final Map<String, Long> reserved;
    public AcquisitionLedger() { inventory = new TreeMap<>(); drawn = new TreeMap<>(); reserved = new TreeMap<>(); }
    private AcquisitionLedger(AcquisitionLedger source) {
        inventory = new TreeMap<>(source.inventory); drawn = new TreeMap<>(source.drawn);
        reserved = new TreeMap<>(source.reserved);
    }
    public AcquisitionLedger copy() { return new AcquisitionLedger(this); }
    public void commit(AcquisitionLedger branch) {
        inventory.clear(); inventory.putAll(branch.inventory); drawn.clear(); drawn.putAll(branch.drawn);
        reserved.clear(); reserved.putAll(branch.reserved);
    }
    public long reserved(String item) { return reserved.getOrDefault(item, 0L); }
    /** A station can serve nested sequential prerequisites, but cannot be consumed
     * as an ingredient/fuel while the enclosing operation still needs it. */
    public void reserve(String item) {
        if (!consume(item, 1)) throw new IllegalStateException("Missing station to reserve");
        reserved.merge(item, 1L, Math::addExact);
    }
    public void release(String item) {
        if (reserved(item) < 1) throw new IllegalStateException("Missing reserved station");
        reserved.put(item, reserved(item) - 1); add(item, 1);
    }
    public long held(String item) { return inventory.getOrDefault(item, 0L); }
    public void add(String item, long quantity) {
        if (quantity < 0) throw new IllegalArgumentException("Negative acquisition quantity");
        inventory.put(item, Math.addExact(held(item), quantity));
    }
    public boolean consume(String item, long quantity) {
        if (quantity < 0) throw new IllegalArgumentException("Negative acquisition quantity");
        if (held(item) < quantity) return false;
        inventory.put(item, held(item) - quantity); return true;
    }
    public long remaining(String source, String item, long total) {
        return Math.max(0, total - drawn.getOrDefault(source + "\n" + item, 0L));
    }
    public boolean draw(String source, String item, long total, long quantity) {
        if (quantity < 0 || quantity > remaining(source, item, total)) return false;
        drawn.merge(source + "\n" + item, quantity, Math::addExact); add(item, quantity); return true;
    }
    public Map<String, Long> inventory() { return Collections.unmodifiableMap(inventory); }
    public Map<String, Long> consumedStocks() { return Collections.unmodifiableMap(drawn); }
}
