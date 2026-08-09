package com.mistaboom.essence_ascendance.progression;

import net.minecraft.resources.ResourceLocation;

public record MilestoneDefinition(
        ResourceLocation id,
        String displayName,
        ResourceLocation providerId,
        String target
) {

    public MilestoneDefinition {

        if (id == null) {
            throw new IllegalArgumentException(
                    "Milestone ID cannot be null"
            );
        }

        if (displayName == null
                || displayName.isBlank()) {

            throw new IllegalArgumentException(
                    "Milestone display name cannot be blank"
            );
        }

        if (providerId == null) {
            throw new IllegalArgumentException(
                    "Milestone provider ID cannot be null"
            );
        }

        if (target == null
                || target.isBlank()) {

            throw new IllegalArgumentException(
                    "Milestone target cannot be blank"
            );
        }
    }
}