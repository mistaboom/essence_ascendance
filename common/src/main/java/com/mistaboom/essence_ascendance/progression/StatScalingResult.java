package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;

import java.util.Objects;

public record StatScalingResult(
        StatDefinition stat,
        AscendanceTierDefinition tier,
        long storedInvestment,
        long effectiveInvestment,
        long currentInvestmentCap,
        double progression,
        double currentTierMaximumBonus,
        double transcendentMaximumBonus,
        double scaledBonus
) {

    public StatScalingResult {

        Objects.requireNonNull(
                stat,
                "Stat cannot be null"
        );

        Objects.requireNonNull(
                tier,
                "Tier cannot be null"
        );


        if (storedInvestment < 0L) {
            throw new IllegalArgumentException(
                    "Stored investment cannot be negative"
            );
        }


        if (effectiveInvestment < 0L) {
            throw new IllegalArgumentException(
                    "Effective investment cannot be negative"
            );
        }


        if (currentInvestmentCap < 0L) {
            throw new IllegalArgumentException(
                    "Investment cap cannot be negative"
            );
        }


        if (!Double.isFinite(progression)
                || progression < 0.0
                || progression > 1.0) {

            throw new IllegalArgumentException(
                    "Progression must be between 0.0 and 1.0"
            );
        }


        if (!Double.isFinite(
                transcendentMaximumBonus
        )
                || transcendentMaximumBonus < 0.0) {

            throw new IllegalArgumentException(
                    "Transcendent maximum bonus must be finite and non-negative"
            );
        }


        if (!Double.isFinite(
                currentTierMaximumBonus
        )
                || currentTierMaximumBonus < 0.0) {

            throw new IllegalArgumentException(
                    "Current tier maximum bonus must be finite and non-negative"
            );
        }


        if (!Double.isFinite(
                scaledBonus
        )
                || scaledBonus < 0.0) {

            throw new IllegalArgumentException(
                    "Scaled bonus must be finite and non-negative"
            );
        }
    }


    /*
     * Discrete stats such as Fortune, Looting, and Mining Level
     * should not receive the next whole level until that level has
     * actually been earned.
     */

    public int wholeLevelBonus() {

        if (scaledBonus
                >= Integer.MAX_VALUE) {

            return Integer.MAX_VALUE;
        }


        return (int) Math.floor(
                scaledBonus
        );
    }
}