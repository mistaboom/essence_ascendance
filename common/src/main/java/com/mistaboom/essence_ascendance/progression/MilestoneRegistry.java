package com.mistaboom.essence_ascendance.progression;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class MilestoneRegistry {

    private static final Map<ResourceLocation, MilestoneDefinition> MILESTONES =
            new LinkedHashMap<>();


    private MilestoneRegistry() {
    }


    public static MilestoneDefinition register(
            MilestoneDefinition milestone
    ) {
        ResourceLocation id =
                milestone.id();

        if (MILESTONES.containsKey(id)) {
            throw new IllegalArgumentException(
                    "Duplicate milestone ID: "
                            + id
            );
        }

        MILESTONES.put(
                id,
                milestone
        );

        return milestone;
    }


    public static Optional<MilestoneDefinition> get(
            ResourceLocation id
    ) {
        return Optional.ofNullable(
                MILESTONES.get(id)
        );
    }


    public static Collection<MilestoneDefinition> values() {
        return Collections.unmodifiableCollection(
                MILESTONES.values()
        );
    }


    public static int size() {
        return MILESTONES.size();
    }
}