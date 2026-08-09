package com.mistaboom.essence_ascendance.progression;

import java.util.List;

public record MilestoneProgress(
        MilestoneRequirement requirement,
        boolean resolvable,
        boolean complete,
        List<MilestoneProgress> children
) {

    public MilestoneProgress {

        if (requirement == null) {
            throw new IllegalArgumentException(
                    "Milestone requirement cannot be null"
            );
        }

        children =
                children == null
                        ? List.of()
                        : List.copyOf(children);
    }


    public boolean isLeaf() {
        return requirement
                instanceof MilestoneRequirement.Milestone;
    }
}