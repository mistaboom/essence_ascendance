package com.mistaboom.essence_ascendance.skill.requirement;

import com.mistaboom.essence_ascendance.skill.SkillRequirementKind;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record PermanentMilestoneRequirement(
        ResourceLocation id,
        ResourceLocation milestoneId,
        String translationKey
) implements SkillRequirement {

    public PermanentMilestoneRequirement {
        Objects.requireNonNull(id, "Requirement ID cannot be null");
        Objects.requireNonNull(milestoneId, "Milestone ID cannot be null");
        if (translationKey == null || translationKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Milestone requirement translation key cannot be blank"
            );
        }
    }

    public PermanentMilestoneRequirement(
            ResourceLocation milestoneId,
            String translationKey
    ) {
        this(milestoneId, milestoneId, translationKey);
    }

    @Override
    public SkillRequirementKind kind() {
        return SkillRequirementKind.PERMANENT_MILESTONE;
    }
}
