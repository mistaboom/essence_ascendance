package com.mistaboom.essence_ascendance.skill;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/**
 * A free loadout choice. Membership limits simultaneous effectiveness, never
 * permanent ownership or purchase eligibility.
 */
public record SkillChoiceGroup(
        ResourceLocation id,
        String translationKey,
        List<ResourceLocation> memberIds,
        boolean allowNoSelection
) {
    public SkillChoiceGroup {
        Objects.requireNonNull(id, "Choice-group ID cannot be null");
        if (translationKey == null || translationKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Choice-group translation key cannot be blank"
            );
        }
        memberIds = List.copyOf(
                Objects.requireNonNull(memberIds, "Choice-group members cannot be null")
        );
        if (memberIds.size() < 2) {
            throw new IllegalArgumentException(
                    "Choice group " + id + " must contain at least two skills"
            );
        }
    }
}
