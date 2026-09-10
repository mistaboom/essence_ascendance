package com.mistaboom.essence_ascendance.skill;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Current and projected completion for one declarative skill requirement. */
public record SkillRequirementStatus(
        ResourceLocation requirementId,
        SkillRequirementKind kind,
        String translationKey,
        boolean live,
        boolean authoritativeSatisfied,
        boolean projectedSatisfied
) {
    public SkillRequirementStatus {
        Objects.requireNonNull(requirementId, "Requirement ID cannot be null");
        Objects.requireNonNull(kind, "Requirement kind cannot be null");
        if (translationKey == null || translationKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Requirement translation key cannot be blank"
            );
        }
    }
}
