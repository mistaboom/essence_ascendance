package com.mistaboom.essence_ascendance.stat;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record StatDefinition(
        ResourceLocation id,
        String displayName,
        StatCategory category,
        StatUnit unit,
        EssenceDefinition essenceType
) {

    public StatDefinition {
        Objects.requireNonNull(id, "Stat ID cannot be null");
        Objects.requireNonNull(category, "Stat category cannot be null");
        Objects.requireNonNull(unit, "Stat unit cannot be null");
        Objects.requireNonNull(essenceType, "Stat Essence type cannot be null");

        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Stat display name cannot be blank");
        }
    }
}
