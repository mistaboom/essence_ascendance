package com.mistaboom.essence_ascendance.crucible;

/*
 * Immutable effective structure snapshot consumed by every Crucible system.
 *
 * Today EssenceCrucibleStructureService returns only BASE. Later pylons can
 * alter this record in one place instead of scattering structure checks through
 * the block entity, menu, automation, and visual code.
 */
public record EssenceCrucibleStructureStats(
        int usableItemSlots,
        long reservoirCapacity,
        double transferRange,
        long transferRatePerSecond,
        int dissolutionTicksPerItem,
        int simultaneousItemProcesses,
        int automationConnectionPorts,
        int visualTransferStreams,
        int activePylonCount
) {
    public EssenceCrucibleStructureStats {
        if (usableItemSlots < 1) {
            throw new IllegalArgumentException("Crucible must expose at least one usable item slot");
        }
        if (reservoirCapacity < 1L) {
            throw new IllegalArgumentException("Crucible reservoir capacity must be positive");
        }
        if (!(transferRange > 0.0D) || !Double.isFinite(transferRange)) {
            throw new IllegalArgumentException("Crucible transfer range must be finite and positive");
        }
        if (transferRatePerSecond < 1L) {
            throw new IllegalArgumentException("Crucible transfer rate must be positive");
        }
        if (dissolutionTicksPerItem < 1) {
            throw new IllegalArgumentException("Crucible dissolution time must be positive");
        }
        if (simultaneousItemProcesses < 1) {
            throw new IllegalArgumentException("Crucible simultaneous item processes must be positive");
        }
        if (automationConnectionPorts < 0 || visualTransferStreams < 1 || activePylonCount < 0) {
            throw new IllegalArgumentException("Invalid Crucible structure counts");
        }
    }
}
