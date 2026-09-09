package com.mistaboom.essence_ascendance.essence;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class EssenceTypes {

    public static final EssenceDefinition OFFENSE =
            register(
                    "offense",
                    "Offense Essence"
            );

    public static final EssenceDefinition DEFENSE =
            register(
                    "defense",
                    "Defense Essence"
            );

    public static final EssenceDefinition VITALITY =
            register(
                    "vitality",
                    "Vitality Essence"
            );

    public static final EssenceDefinition MOBILITY =
            register(
                    "mobility",
                    "Mobility Essence"
            );

    public static final EssenceDefinition GATHERING =
            register(
                    "gathering",
                    "Gathering Essence"
            );

    public static final EssenceDefinition UTILITY =
            register(
                    "utility",
                    "Utility Essence"
            );

    /** Canonical order used by machines, synchronization, and presentation. */
    public static final List<EssenceDefinition> ORDERED = List.of(
            OFFENSE,
            DEFENSE,
            VITALITY,
            MOBILITY,
            GATHERING,
            UTILITY
    );

    private EssenceTypes() {
    }

    private static EssenceDefinition register(
            String path,
            String displayName
    ) {
        return new EssenceDefinition(
                ResourceLocation.fromNamespaceAndPath(
                        EssenceAscendance.MOD_ID,
                        path
                ),
                displayName
        );
    }

    public static void init() {
        // Calling this method forces Java to initialize this class,
        // creating all six built-in Essence types.
    }
}
