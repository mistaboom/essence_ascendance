package com.mistaboom.essence_ascendance.essence;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.resources.ResourceLocation;

public final class EssenceTypes {

    /*
     * ============================================================
     * ATTRIBUTE ESSENCES
     * ============================================================
     */

    public static final EssenceDefinition OFFENSE =
            register(
                    "offense",
                    "Offense Essence",
                    EssenceFamily.ATTRIBUTE
            );

    public static final EssenceDefinition DEFENSE =
            register(
                    "defense",
                    "Defense Essence",
                    EssenceFamily.ATTRIBUTE
            );

    public static final EssenceDefinition VITALITY =
            register(
                    "vitality",
                    "Vitality Essence",
                    EssenceFamily.ATTRIBUTE
            );

    public static final EssenceDefinition MOBILITY =
            register(
                    "mobility",
                    "Mobility Essence",
                    EssenceFamily.ATTRIBUTE
            );

    public static final EssenceDefinition GATHERING =
            register(
                    "gathering",
                    "Gathering Essence",
                    EssenceFamily.ATTRIBUTE
            );

    public static final EssenceDefinition UTILITY =
            register(
                    "utility",
                    "Utility Essence",
                    EssenceFamily.ATTRIBUTE
            );


    /*
     * ============================================================
     * SKILL ESSENCES
     * ============================================================
     */

    public static final EssenceDefinition GALE =
            register(
                    "gale",
                    "Gale Essence",
                    EssenceFamily.SKILL
            );

    public static final EssenceDefinition BODY =
            register(
                    "body",
                    "Body Essence",
                    EssenceFamily.SKILL
            );

    public static final EssenceDefinition MIND =
            register(
                    "mind",
                    "Mind Essence",
                    EssenceFamily.SKILL
            );

    public static final EssenceDefinition SPIRIT =
            register(
                    "spirit",
                    "Spirit Essence",
                    EssenceFamily.SKILL
            );

    public static final EssenceDefinition RADIANCE =
            register(
                    "radiance",
                    "Radiance Essence",
                    EssenceFamily.SKILL
            );

    public static final EssenceDefinition VOID =
            register(
                    "void",
                    "Void Essence",
                    EssenceFamily.SKILL
            );


    private EssenceTypes() {
    }

    private static EssenceDefinition register(
            String path,
            String displayName,
            EssenceFamily family
    ) {
        return EssenceRegistry.register(
                ResourceLocation.fromNamespaceAndPath(
                        EssenceAscendance.MOD_ID,
                        path
                ),
                displayName,
                family
        );
    }

    public static void init() {
        // Calling this method forces Java to initialize this class,
        // registering all built-in Essence types.
    }
}