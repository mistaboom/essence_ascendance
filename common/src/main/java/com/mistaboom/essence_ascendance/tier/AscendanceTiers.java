package com.mistaboom.essence_ascendance.tier;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.resources.ResourceLocation;

public final class AscendanceTiers {

    public static final AscendanceTierDefinition DORMANT =
            register(
                    "dormant",
                    "Dormant",
                    0
            );

    public static final AscendanceTierDefinition AWAKENED =
            register(
                    "awakened",
                    "Awakened",
                    1
            );

    public static final AscendanceTierDefinition RESONANT =
            register(
                    "resonant",
                    "Resonant",
                    2
            );

    public static final AscendanceTierDefinition ASCENDANT =
            register(
                    "ascendant",
                    "Ascendant",
                    3
            );

    public static final AscendanceTierDefinition TRANSCENDENT =
            register(
                    "transcendent",
                    "Transcendent",
                    4
            );

    private AscendanceTiers() {
    }

    private static AscendanceTierDefinition register(
            String path,
            String displayName,
            int order
    ) {
        return AscendanceTierRegistry.register(
                ResourceLocation.fromNamespaceAndPath(
                        EssenceAscendance.MOD_ID,
                        path
                ),
                displayName,
                order
        );
    }

    public static void init() {
        // Forces static initialization and registration.
    }
}