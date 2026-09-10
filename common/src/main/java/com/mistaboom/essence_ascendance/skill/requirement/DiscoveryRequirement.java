package com.mistaboom.essence_ascendance.skill.requirement;

import com.mistaboom.essence_ascendance.skill.SkillRequirementKind;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Reserved stable metadata for future data-driven discovery state. Until a
 * discovery provider is registered, evaluators must fail this requirement
 * closed rather than silently treating it as complete.
 */
public record DiscoveryRequirement(
        ResourceLocation id,
        ResourceLocation discoveryId,
        String translationKey
) implements SkillRequirement {

    public DiscoveryRequirement {
        Objects.requireNonNull(id, "Requirement ID cannot be null");
        Objects.requireNonNull(discoveryId, "Discovery ID cannot be null");
        if (translationKey == null || translationKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Discovery requirement translation key cannot be blank"
            );
        }
    }

    public DiscoveryRequirement(
            ResourceLocation discoveryId,
            String translationKey
    ) {
        this(discoveryId, discoveryId, translationKey);
    }

    @Override
    public SkillRequirementKind kind() {
        return SkillRequirementKind.DISCOVERY;
    }
}
