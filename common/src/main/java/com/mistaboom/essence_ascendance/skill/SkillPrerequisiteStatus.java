package com.mistaboom.essence_ascendance.skill;

import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

/** The paid rank required by one prerequisite, including projected purchases. */
public record SkillPrerequisiteStatus(ResourceLocation skillId, int requiredRank,
                                      int authoritativeRank, int projectedRank) {
    public SkillPrerequisiteStatus {
        Objects.requireNonNull(skillId);
        if (requiredRank < 1 || authoritativeRank < 0 || projectedRank < 0)
            throw new IllegalArgumentException("Invalid prerequisite rank");
    }
    public boolean authoritativeOwned() { return authoritativeRank >= requiredRank; }
    public boolean projectedOwned() { return projectedRank >= requiredRank; }
}
