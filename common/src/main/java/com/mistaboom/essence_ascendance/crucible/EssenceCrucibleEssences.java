package com.mistaboom.essence_ascendance.crucible;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;

import java.util.List;

public final class EssenceCrucibleEssences {

    /*
     * Core Crucible processing/channeling currently operates on Attribute
     * Essence only. Keep ORDERED as the compatibility alias used by the
     * existing machine logic.
     */
    public static final List<EssenceDefinition> ATTRIBUTE_ORDERED = List.of(
            EssenceTypes.OFFENSE,
            EssenceTypes.DEFENSE,
            EssenceTypes.VITALITY,
            EssenceTypes.MOBILITY,
            EssenceTypes.GATHERING,
            EssenceTypes.UTILITY
    );

    public static final List<EssenceDefinition> ORDERED =
            ATTRIBUTE_ORDERED;

    /*
     * Skill Essence remains registered/persistent even while its presentation
     * feature flag is disabled. This list is used only for optional UI/state
     * presentation in the current core mod.
     */
    public static final List<EssenceDefinition> SKILL_ORDERED = List.of(
            EssenceTypes.PYRE,
            EssenceTypes.FLOW,
            EssenceTypes.TERRA,
            EssenceTypes.GALE,
            EssenceTypes.BODY,
            EssenceTypes.MIND,
            EssenceTypes.SPIRIT,
            EssenceTypes.RADIANCE,
            EssenceTypes.VOID
    );

    public static final List<EssenceDefinition> ALL_ORDERED = List.of(
            EssenceTypes.OFFENSE,
            EssenceTypes.DEFENSE,
            EssenceTypes.VITALITY,
            EssenceTypes.MOBILITY,
            EssenceTypes.GATHERING,
            EssenceTypes.UTILITY,
            EssenceTypes.PYRE,
            EssenceTypes.FLOW,
            EssenceTypes.TERRA,
            EssenceTypes.GALE,
            EssenceTypes.BODY,
            EssenceTypes.MIND,
            EssenceTypes.SPIRIT,
            EssenceTypes.RADIANCE,
            EssenceTypes.VOID
    );

    private EssenceCrucibleEssences() {
    }

    public static List<EssenceDefinition> enabledOrdered(
            boolean skillEssencesEnabled
    ) {
        return skillEssencesEnabled
                ? ALL_ORDERED
                : ATTRIBUTE_ORDERED;
    }

    public static int indexOf(EssenceDefinition essence) {
        for (int i = 0; i < ATTRIBUTE_ORDERED.size(); i++) {
            if (ATTRIBUTE_ORDERED.get(i).id().equals(essence.id())) {
                return i;
            }
        }
        return -1;
    }

    public static String shortName(int index) {
        return switch (index) {
            case 0 -> "Offense";
            case 1 -> "Defense";
            case 2 -> "Vitality";
            case 3 -> "Mobility";
            case 4 -> "Gathering";
            case 5 -> "Utility";
            default -> "Unknown";
        };
    }

    public static String skillShortName(int index) {
        return switch (index) {
            case 0 -> "Pyre";
            case 1 -> "Flow";
            case 2 -> "Terra";
            case 3 -> "Gale";
            case 4 -> "Body";
            case 5 -> "Mind";
            case 6 -> "Spirit";
            case 7 -> "Radiance";
            case 8 -> "Void";
            default -> "Unknown";
        };
    }
}
