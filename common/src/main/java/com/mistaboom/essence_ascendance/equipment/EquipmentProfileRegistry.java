package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class EquipmentProfileRegistry {

    private static final Map<ResourceLocation, EquipmentProfileDefinition> PROFILES =
            new LinkedHashMap<>();

    private EquipmentProfileRegistry() {
    }

    public static EquipmentProfileDefinition register(EquipmentProfileDefinition profile) {
        if (PROFILES.containsKey(profile.id())) {
            throw new IllegalArgumentException(
                    "Duplicate equipment profile ID: " + profile.id()
            );
        }
        PROFILES.put(profile.id(), profile);
        return profile;
    }

    public static Optional<EquipmentProfileDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(PROFILES.get(id));
    }

    public static Collection<EquipmentProfileDefinition> values() {
        return Collections.unmodifiableCollection(PROFILES.values());
    }

    public static int size() {
        return PROFILES.size();
    }
}
