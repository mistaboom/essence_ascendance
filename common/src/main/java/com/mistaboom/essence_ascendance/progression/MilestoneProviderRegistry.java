package com.mistaboom.essence_ascendance.progression;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class MilestoneProviderRegistry {

    private static final Map<ResourceLocation, MilestoneProvider> PROVIDERS =
            new LinkedHashMap<>();


    private MilestoneProviderRegistry() {
    }


    public static void register(
            ResourceLocation id,
            MilestoneProvider provider
    ) {
        if (PROVIDERS.containsKey(id)) {
            throw new IllegalArgumentException(
                    "Duplicate milestone provider ID: "
                            + id
            );
        }

        PROVIDERS.put(
                id,
                provider
        );
    }


    public static Optional<MilestoneProvider> get(
            ResourceLocation id
    ) {
        return Optional.ofNullable(
                PROVIDERS.get(id)
        );
    }


    public static Map<ResourceLocation, MilestoneProvider> values() {
        return Collections.unmodifiableMap(
                PROVIDERS
        );
    }


    public static int size() {
        return PROVIDERS.size();
    }
}