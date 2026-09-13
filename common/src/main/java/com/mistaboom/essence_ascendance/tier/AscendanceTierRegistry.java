package com.mistaboom.essence_ascendance.tier;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class AscendanceTierRegistry {

    private static final Map<ResourceLocation, AscendanceTierDefinition> TIERS =
            new LinkedHashMap<>();

    private AscendanceTierRegistry() {
    }

    public static AscendanceTierDefinition register(
            ResourceLocation id,
            String displayName,
            int order
    ) {
        return register(id, displayName, order, true);
    }

    public static AscendanceTierDefinition register(
            ResourceLocation id,
            String displayName,
            int order,
            boolean grantsPower
    ) {
        if (TIERS.containsKey(id)) {
            throw new IllegalArgumentException(
                    "Duplicate Ascendance tier ID: " + id
            );
        }

        boolean duplicateOrder = TIERS.values()
                .stream()
                .anyMatch(tier -> tier.order() == order);

        if (duplicateOrder) {
            throw new IllegalArgumentException(
                    "Duplicate Ascendance tier order: " + order
            );
        }

        AscendanceTierDefinition tier =
                new AscendanceTierDefinition(
                        id,
                        displayName,
                        order,
                        grantsPower
                );

        TIERS.put(id, tier);

        return tier;
    }

    public static Optional<AscendanceTierDefinition> get(
            ResourceLocation id
    ) {
        return Optional.ofNullable(TIERS.get(id));
    }

    public static Collection<AscendanceTierDefinition> values() {
        return Collections.unmodifiableCollection(TIERS.values());
    }

    /** Tiers that expose Bonus, skill, and generated player-power curves. */
    public static Collection<AscendanceTierDefinition> powerTiers() {
        return TIERS.values().stream().filter(AscendanceTierDefinition::grantsPower).toList();
    }

    public static int size() {
        return TIERS.size();
    }

    public static Optional<AscendanceTierDefinition> getNext(
            AscendanceTierDefinition current
    ) {
        return TIERS.values()
                .stream()
                .filter(tier ->
                        tier.order() == current.order() + 1
                )
                .findFirst();
    }
}
