package com.mistaboom.essence_ascendance.crucible;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;

import java.util.List;

public final class EssenceCrucibleEssences {

    public static final List<EssenceDefinition> ORDERED =
            EssenceTypes.ORDERED;

    private EssenceCrucibleEssences() {
    }

    public static int indexOf(EssenceDefinition essence) {
        for (int i = 0; i < ORDERED.size(); i++) {
            if (ORDERED.get(i).id().equals(essence.id())) {
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

}
