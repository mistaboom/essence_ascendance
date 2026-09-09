package com.mistaboom.essence_ascendance.essence;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class EssenceRegistry {

    private static final int REQUIRED_ESSENCE_COUNT = 6;

    private static final Map<ResourceLocation, EssenceDefinition> ESSENCES =
            createRegistry();

    private EssenceRegistry() {
    }

    private static Map<ResourceLocation, EssenceDefinition> createRegistry() {
        Map<ResourceLocation, EssenceDefinition> essences = new LinkedHashMap<>();
        for (EssenceDefinition essence : EssenceTypes.ORDERED) {
            EssenceDefinition duplicate = essences.put(essence.id(), essence);
            if (duplicate != null) {
                throw new IllegalStateException(
                        "Duplicate Essence Ascendance essence ID: " + essence.id()
                );
            }
        }
        if (essences.size() != REQUIRED_ESSENCE_COUNT) {
            throw new IllegalStateException(
                    "Essence Ascendance requires exactly six core Essences"
            );
        }
        return Collections.unmodifiableMap(essences);
    }

    public static Optional<EssenceDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(ESSENCES.get(id));
    }

    public static Collection<EssenceDefinition> values() {
        return ESSENCES.values();
    }

    public static int size() {
        return ESSENCES.size();
    }
}
