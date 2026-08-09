package com.mistaboom.essence_ascendance.essence;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class EssenceRegistry {

    private static final Map<ResourceLocation, EssenceDefinition> ESSENCES =
            new LinkedHashMap<>();

    private EssenceRegistry() {
    }

    public static EssenceDefinition register(
            ResourceLocation id,
            String displayName,
            EssenceFamily family
    ) {
        if (ESSENCES.containsKey(id)) {
            throw new IllegalArgumentException(
                    "Duplicate Essence Ascendance essence ID: " + id
            );
        }

        EssenceDefinition essence =
                new EssenceDefinition(id, displayName, family);

        ESSENCES.put(id, essence);

        return essence;
    }

    public static Optional<EssenceDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(ESSENCES.get(id));
    }

    public static Collection<EssenceDefinition> values() {
        return Collections.unmodifiableCollection(ESSENCES.values());
    }

    public static int size() {
        return ESSENCES.size();
    }
}