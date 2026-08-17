package com.mistaboom.essence_ascendance.pylon;

/**
 * The effective contribution of one active Essence Pylon to its linked Crucible.
 * Reservoir capacity is expressed per enabled Essence family, matching the
 * Crucible's existing capacity model.
 */
public record EssencePylonContribution(
        long reservoirCapacityBonus,
        double transferRangeBonus,
        long transferRatePerSecondBonus,
        double dissolutionSpeedBonus,
        int simultaneousItemProcessesBonus
) {
    public EssencePylonContribution {
        if (reservoirCapacityBonus < 0L) {
            throw new IllegalArgumentException("Pylon reservoir bonus cannot be negative");
        }
        if (transferRangeBonus < 0.0D || !Double.isFinite(transferRangeBonus)) {
            throw new IllegalArgumentException("Pylon range bonus must be finite and non-negative");
        }
        if (transferRatePerSecondBonus < 0L) {
            throw new IllegalArgumentException("Pylon transfer-rate bonus cannot be negative");
        }
        if (dissolutionSpeedBonus < 0.0D || !Double.isFinite(dissolutionSpeedBonus)) {
            throw new IllegalArgumentException("Pylon dissolution-speed bonus must be finite and non-negative");
        }
        if (simultaneousItemProcessesBonus < 0) {
            throw new IllegalArgumentException("Pylon simultaneous-processing bonus cannot be negative");
        }
    }
}
