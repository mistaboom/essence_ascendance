package com.mistaboom.essence_ascendance.skill.requirement;

import com.mistaboom.essence_ascendance.skill.SkillRequirementKind;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record PlayerAttunementRequirement(
        ResourceLocation id,
        ResourceLocation attunementId,
        String translationKey
) implements SkillRequirement {

    public PlayerAttunementRequirement {
        Objects.requireNonNull(id, "Requirement ID cannot be null");
        Objects.requireNonNull(attunementId, "Attunement ID cannot be null");
        requireTranslationKey(translationKey);
    }

    public PlayerAttunementRequirement(
            ResourceLocation attunementId,
            String translationKey
    ) {
        this(attunementId, attunementId, translationKey);
    }

    @Override
    public SkillRequirementKind kind() {
        return SkillRequirementKind.PLAYER_ATTUNEMENT;
    }

    private static void requireTranslationKey(String translationKey) {
        if (translationKey == null || translationKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Attunement requirement translation key cannot be blank"
            );
        }
    }
}
