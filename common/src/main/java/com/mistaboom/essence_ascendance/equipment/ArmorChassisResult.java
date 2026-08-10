package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.progression.CategoryDevelopment;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;

import java.util.Objects;

public record ArmorChassisResult(
        AscendanceTierDefinition tier,
        CategoryDevelopment defenseDevelopment,
        CategoryDevelopment vitalityDevelopment,
        ArmorChassisConfig.TierRange tierRange,
        double armorProgress,
        double toughnessProgress,
        double targetArmor,
        double targetToughness
) {

    public ArmorChassisResult {

        Objects.requireNonNull(
                tier,
                "Tier cannot be null"
        );

        Objects.requireNonNull(
                defenseDevelopment,
                "Defense development cannot be null"
        );

        Objects.requireNonNull(
                vitalityDevelopment,
                "Vitality development cannot be null"
        );

        Objects.requireNonNull(
                tierRange,
                "Tier range cannot be null"
        );


        validateProgress(
                armorProgress,
                "armorProgress"
        );

        validateProgress(
                toughnessProgress,
                "toughnessProgress"
        );


        if (!Double.isFinite(
                targetArmor
        )
                || targetArmor < 0.0) {

            throw new IllegalArgumentException(
                    "Target armor must be finite and non-negative"
            );
        }


        if (!Double.isFinite(
                targetToughness
        )
                || targetToughness < 0.0) {

            throw new IllegalArgumentException(
                    "Target toughness must be finite and non-negative"
            );
        }
    }


    private static void validateProgress(
            double value,
            String name
    ) {

        if (!Double.isFinite(
                value
        )
                || value < 0.0
                || value > 1.0) {

            throw new IllegalArgumentException(
                    name
                            + " must be between 0.0 and 1.0"
            );
        }
    }
}