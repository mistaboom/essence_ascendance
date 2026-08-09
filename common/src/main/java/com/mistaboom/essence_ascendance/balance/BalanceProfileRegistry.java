package com.mistaboom.essence_ascendance.balance;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class BalanceProfileRegistry {

    private static final Map<
            ResourceLocation,
            BalanceProfileDefinition
            > PROFILES =
            new LinkedHashMap<>();


    private BalanceProfileRegistry() {
    }


    public static BalanceProfileDefinition register(
            BalanceProfileDefinition profile
    ) {
        ResourceLocation id =
                profile.id();

        if (PROFILES.containsKey(id)) {
            throw new IllegalArgumentException(
                    "Duplicate balance profile ID: "
                            + id
            );
        }

        PROFILES.put(
                id,
                profile
        );

        return profile;
    }


    public static Optional<BalanceProfileDefinition> get(
            ResourceLocation id
    ) {
        return Optional.ofNullable(
                PROFILES.get(id)
        );
    }


    public static Collection<BalanceProfileDefinition> values() {
        return Collections.unmodifiableCollection(
                PROFILES.values()
        );
    }


    public static int size() {
        return PROFILES.size();
    }
}