package com.mistaboom.essence_ascendance.skill;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Ownership status for one permanent skill prerequisite. */
public record SkillPrerequisiteStatus(
        ResourceLocation skillId,
        boolean authoritativeOwned,
        boolean projectedOwned
) {
    public SkillPrerequisiteStatus {
        Objects.requireNonNull(skillId, "Prerequisite skill ID cannot be null");
    }
}
