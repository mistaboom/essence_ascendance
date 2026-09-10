package com.mistaboom.essence_ascendance.skill;

import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.skill.requirement.SkillRequirement;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable catalog entry. No gameplay-effect handler is stored here. */
public record SkillDefinition(
        ResourceLocation id,
        ResourceLocation essenceId,
        String nameTranslationKey,
        String descriptionTranslationKey,
        ResourceLocation requiredTierId,
        SkillCostBand costBand,
        List<ResourceLocation> prerequisites,
        List<SkillRequirement> requirements,
        ResourceLocation choiceGroup,
        ResourceLocation replacementTarget,
        SkillActivationPolicy activationPolicy,
        int displayOrder,
        SkillLayoutHint layoutHint
) {
    public SkillDefinition {
        Objects.requireNonNull(id, "Skill ID cannot be null");
        Objects.requireNonNull(essenceId, "Skill Essence ID cannot be null");
        Objects.requireNonNull(requiredTierId, "Required tier ID cannot be null");
        Objects.requireNonNull(costBand, "Skill cost band cannot be null");
        Objects.requireNonNull(activationPolicy, "Activation policy cannot be null");
        Objects.requireNonNull(layoutHint, "Layout hint cannot be null");

        if (nameTranslationKey == null || nameTranslationKey.isBlank()) {
            throw new IllegalArgumentException("Skill name translation key cannot be blank");
        }
        if (descriptionTranslationKey == null || descriptionTranslationKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Skill description translation key cannot be blank"
            );
        }
        if (displayOrder < 0) {
            throw new IllegalArgumentException("Skill display order cannot be negative");
        }

        prerequisites = List.copyOf(
                Objects.requireNonNull(prerequisites, "Prerequisites cannot be null")
        );
        requirements = List.copyOf(
                Objects.requireNonNull(requirements, "Requirements cannot be null")
        );

        if (prerequisites.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Prerequisites cannot contain null IDs");
        }
        if (requirements.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Requirements cannot contain null entries");
        }

        Set<ResourceLocation> distinctPrerequisites = new HashSet<>(prerequisites);
        if (distinctPrerequisites.size() != prerequisites.size()) {
            throw new IllegalArgumentException(
                    "Skill " + id + " declares a prerequisite more than once"
            );
        }
    }

    public Optional<ResourceLocation> choiceGroupId() {
        return Optional.ofNullable(choiceGroup);
    }

    public Optional<ResourceLocation> replacementTargetId() {
        return Optional.ofNullable(replacementTarget);
    }

    public boolean isReplacement() {
        return replacementTarget != null;
    }

    public boolean hasLiveRequirements() {
        return requirements.stream().anyMatch(SkillRequirement::live);
    }

    public long cost(BalanceProfileDefinition profile) {
        return costBand.cost(profile, requiredTierId);
    }
}
