package com.mistaboom.essence_ascendance.progression;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class AscendanceAdvancementRegistry {

    private static final Map<
            ResourceLocation,
            AscendanceAdvancementDefinition
            > ADVANCEMENTS =
            new LinkedHashMap<>();

    private static final Map<
            ResourceLocation,
            AscendanceAdvancementDefinition
            > BY_FROM_TIER =
            new LinkedHashMap<>();


    private AscendanceAdvancementRegistry() {
    }


    public static AscendanceAdvancementDefinition register(
            AscendanceAdvancementDefinition advancement
    ) {

        if (ADVANCEMENTS.containsKey(
                advancement.id()
        )) {
            throw new IllegalArgumentException(
                    "Duplicate Ascendance advancement ID: "
                            + advancement.id()
            );
        }

        if (BY_FROM_TIER.containsKey(
                advancement.fromTierId()
        )) {
            throw new IllegalArgumentException(
                    "Multiple Ascendance advancements originate from tier: "
                            + advancement.fromTierId()
            );
        }

        ADVANCEMENTS.put(
                advancement.id(),
                advancement
        );

        BY_FROM_TIER.put(
                advancement.fromTierId(),
                advancement
        );

        return advancement;
    }


    public static Optional<AscendanceAdvancementDefinition> get(
            ResourceLocation id
    ) {
        return Optional.ofNullable(
                ADVANCEMENTS.get(id)
        );
    }


    public static Optional<AscendanceAdvancementDefinition> getForTier(
            ResourceLocation tierId
    ) {
        return Optional.ofNullable(
                BY_FROM_TIER.get(tierId)
        );
    }


    public static Collection<AscendanceAdvancementDefinition> values() {
        return Collections.unmodifiableCollection(
                ADVANCEMENTS.values()
        );
    }


    public static int size() {
        return ADVANCEMENTS.size();
    }
}