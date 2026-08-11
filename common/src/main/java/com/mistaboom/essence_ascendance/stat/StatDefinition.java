package com.mistaboom.essence_ascendance.stat;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record StatDefinition(
        ResourceLocation id,
        String displayName,
        StatCategory category,
        StatUnit unit,
        EssenceDefinition essenceType,
        StatScalingMode scalingMode
) {

    /*
     * Backwards-compatible constructor for ordinary BONUS stats.
     *
     * This keeps existing registrations and any future simple stat
     * definitions concise.
     */
    public StatDefinition(
            ResourceLocation id,
            String displayName,
            StatCategory category,
            StatUnit unit,
            EssenceDefinition essenceType
    ) {

        this(
                id,
                displayName,
                category,
                unit,
                essenceType,
                StatScalingMode.BONUS
        );
    }


    public StatDefinition {

        Objects.requireNonNull(
                id,
                "Stat ID cannot be null"
        );

        Objects.requireNonNull(
                displayName,
                "Stat display name cannot be null"
        );

        Objects.requireNonNull(
                category,
                "Stat category cannot be null"
        );

        Objects.requireNonNull(
                unit,
                "Stat unit cannot be null"
        );

        Objects.requireNonNull(
                essenceType,
                "Stat Essence type cannot be null"
        );

        Objects.requireNonNull(
                scalingMode,
                "Stat scaling mode cannot be null"
        );
    }
}