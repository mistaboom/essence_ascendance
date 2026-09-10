package com.mistaboom.essence_ascendance.skill.requirement;

import com.mistaboom.essence_ascendance.skill.SkillRequirementKind;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * A live minimum for the total final Bonus allocation in one Essence
 * category. Skill purchases never contribute to this amount.
 */
public record BonusInvestmentRequirement(
        ResourceLocation id,
        ResourceLocation essenceId,
        long minimumInvestment,
        String translationKey
) implements SkillRequirement {

    public BonusInvestmentRequirement {
        Objects.requireNonNull(id, "Requirement ID cannot be null");
        Objects.requireNonNull(essenceId, "Bonus-investment Essence ID cannot be null");
        if (minimumInvestment < 0L) {
            throw new IllegalArgumentException(
                    "Minimum Bonus investment cannot be negative"
            );
        }
        if (translationKey == null || translationKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Bonus-investment requirement translation key cannot be blank"
            );
        }
    }

    @Override
    public SkillRequirementKind kind() {
        return SkillRequirementKind.BONUS_INVESTMENT;
    }

    @Override
    public boolean live() {
        return true;
    }
}
