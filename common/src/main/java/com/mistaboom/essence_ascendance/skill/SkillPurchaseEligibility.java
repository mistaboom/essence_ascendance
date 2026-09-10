package com.mistaboom.essence_ascendance.skill;

import java.util.List;
import java.util.Objects;

/** Purchase-gate result evaluated against the complete projected transaction. */
public record SkillPurchaseEligibility(
        boolean tierSatisfied,
        boolean prerequisitesSatisfied,
        boolean requirementsSatisfied,
        List<SkillPrerequisiteStatus> prerequisites,
        List<SkillRequirementStatus> requirements
) {
    public SkillPurchaseEligibility {
        prerequisites = List.copyOf(Objects.requireNonNull(
                prerequisites,
                "Prerequisite statuses cannot be null"
        ));
        requirements = List.copyOf(Objects.requireNonNull(
                requirements,
                "Requirement statuses cannot be null"
        ));
    }

    public boolean satisfied() {
        return tierSatisfied && prerequisitesSatisfied && requirementsSatisfied;
    }
}
