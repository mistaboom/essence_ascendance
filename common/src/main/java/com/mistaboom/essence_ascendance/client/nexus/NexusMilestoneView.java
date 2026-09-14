package com.mistaboom.essence_ascendance.client.nexus;

/**
 * A generated mechanical threshold, rendered as a small mark on the existing rail.
 */
public record NexusMilestoneView(
        long threshold,
        double effectFraction,
        boolean unlocked
) {
    public NexusMilestoneView {
        if (threshold < 0L) {
            throw new IllegalArgumentException("Milestone threshold cannot be negative");
        }
        if (!Double.isFinite(effectFraction) || effectFraction < 0 || effectFraction > 1)
            throw new IllegalArgumentException("Milestone effect fraction must be in [0, 1]");
    }
}
