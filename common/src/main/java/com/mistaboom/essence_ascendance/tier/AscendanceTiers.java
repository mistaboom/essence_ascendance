package com.mistaboom.essence_ascendance.tier;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.resources.ResourceLocation;

public final class AscendanceTiers {

    public static final AscendanceTierDefinition LATENT =
            register(
                    "latent",
                    "Latent",
                    0,
                    false
            );

    public static final AscendanceTierDefinition DORMANT =
            register(
                    "dormant",
                    "Dormant",
                    1,
                    true
            );

    public static final AscendanceTierDefinition AWAKENED =
            register(
                    "awakened",
                    "Awakened",
                    2,
                    true
            );

    public static final AscendanceTierDefinition RESONANT =
            register(
                    "resonant",
                    "Resonant",
                    3,
                    true
            );

    public static final AscendanceTierDefinition ASCENDANT =
            register(
                    "ascendant",
                    "Ascendant",
                    4,
                    true
            );

    public static final AscendanceTierDefinition TRANSCENDENT =
            register(
                    "transcendent",
                    "Transcendent",
                    5,
                    true
            );

    private AscendanceTiers() {
    }

    private static AscendanceTierDefinition register(
            String path,
            String displayName,
            int order,
            boolean grantsPower
    ) {
        return AscendanceTierRegistry.register(
                ResourceLocation.fromNamespaceAndPath(
                        EssenceAscendance.MOD_ID,
                        path
                ),
                displayName,
                order,
                grantsPower
        );
    }

    public static void init() {
        // Forces static initialization and registration.
    }
}
