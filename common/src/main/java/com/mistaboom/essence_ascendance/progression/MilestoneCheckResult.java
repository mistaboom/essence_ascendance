package com.mistaboom.essence_ascendance.progression;

public record MilestoneCheckResult(
        boolean resolvable,
        boolean complete
) {

    public MilestoneCheckResult {

        if (complete && !resolvable) {
            throw new IllegalArgumentException(
                    "A completed milestone must also be resolvable"
            );
        }
    }


    public static MilestoneCheckResult completed() {
        return new MilestoneCheckResult(
                true,
                true
        );
    }


    public static MilestoneCheckResult incomplete() {
        return new MilestoneCheckResult(
                true,
                false
        );
    }


    public static MilestoneCheckResult unresolved() {
        return new MilestoneCheckResult(
                false,
                false
        );
    }
}