package com.mistaboom.essence_ascendance.data;

import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A rank ledger. Each entry is the exact payment for that rank, never a recalculated cost. */
public record SkillPurchase(ResourceLocation essenceId, List<Long> paidCosts) {
    public static final int MAX_RANKS = 64;
    public SkillPurchase {
        Objects.requireNonNull(essenceId, "Paid Essence ID cannot be null");
        paidCosts = List.copyOf(Objects.requireNonNull(paidCosts, "Rank receipts cannot be null"));
        if (paidCosts.isEmpty() || paidCosts.size() > MAX_RANKS) {
            throw new IllegalArgumentException("Skill rank must be between 1 and " + MAX_RANKS);
        }
        long total = 0L;
        for (Long cost : paidCosts) {
            if (cost == null || cost < 0L) throw new IllegalArgumentException("Invalid rank payment");
            total = Math.addExact(total, cost);
        }
    }
    public SkillPurchase(ResourceLocation essenceId, long firstRankCost) { this(essenceId, List.of(firstRankCost)); }
    public int rank() { return paidCosts.size(); }
    public long paidCost() {
        long total = 0L;
        for (long cost : paidCosts) total = Math.addExact(total, cost);
        return total;
    }
    public SkillPurchase append(long cost) {
        List<Long> updated = new ArrayList<>(paidCosts);
        updated.add(cost);
        return new SkillPurchase(essenceId, updated);
    }
    public long refundAbove(int retainedRank) {
        if (retainedRank < 0 || retainedRank > rank()) throw new IllegalArgumentException("Invalid retained rank");
        long refund = 0L;
        for (int i = retainedRank; i < rank(); i++) refund = Math.addExact(refund, paidCosts.get(i));
        return refund;
    }
    /** Rank zero is represented by removing the ledger from the owning map. */
    public SkillPurchase retain(int retainedRank) {
        if (retainedRank < 1 || retainedRank > rank()) throw new IllegalArgumentException("Invalid retained rank");
        return new SkillPurchase(essenceId, paidCosts.subList(0, retainedRank));
    }
}
