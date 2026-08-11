package com.mistaboom.essence_ascendance.stat;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class EssenceStatRegistry {

    private static final Map<ResourceLocation, StatDefinition> STATS =
            new LinkedHashMap<>();


    private EssenceStatRegistry() {
    }


    /*
     * ============================================================
     * NORMAL BONUS STAT
     * ============================================================
     */

    public static StatDefinition register(
            ResourceLocation id,
            String displayName,
            StatCategory category,
            StatUnit unit,
            EssenceDefinition essenceType
    ) {

        return register(
                id,
                displayName,
                category,
                unit,
                essenceType,
                StatScalingMode.BONUS
        );
    }


    /*
     * ============================================================
     * EXPLICIT SCALING MODE
     * ============================================================
     */

    public static StatDefinition register(
            ResourceLocation id,
            String displayName,
            StatCategory category,
            StatUnit unit,
            EssenceDefinition essenceType,
            StatScalingMode scalingMode
    ) {

        if (STATS.containsKey(
                id
        )) {

            throw new IllegalArgumentException(
                    "Duplicate Essence Ascendance stat ID: "
                            + id
            );
        }


        StatDefinition stat =
                new StatDefinition(
                        id,
                        displayName,
                        category,
                        unit,
                        essenceType,
                        scalingMode
                );


        STATS.put(
                id,
                stat
        );


        return stat;
    }


    public static Optional<StatDefinition> get(
            ResourceLocation id
    ) {

        return Optional.ofNullable(
                STATS.get(
                        id
                )
        );
    }


    public static Collection<StatDefinition> values() {

        return Collections.unmodifiableCollection(
                STATS.values()
        );
    }


    public static int size() {

        return STATS.size();
    }
}