package com.mistaboom.essence_ascendance.client.nexus;

import java.util.Objects;

/**
 * Presentation hook for future per-track abilities/passives/milestones.
 * Current built-in tracks intentionally provide an empty milestone list.
 */
public record NexusMilestoneView(
        long threshold,
        String type,
        String title,
        String description,
        boolean unlocked
) {
    public NexusMilestoneView {
        if (threshold < 0L) {
            throw new IllegalArgumentException("Milestone threshold cannot be negative");
        }
        Objects.requireNonNull(type, "Milestone type cannot be null");
        Objects.requireNonNull(title, "Milestone title cannot be null");
        Objects.requireNonNull(description, "Milestone description cannot be null");
    }
}
