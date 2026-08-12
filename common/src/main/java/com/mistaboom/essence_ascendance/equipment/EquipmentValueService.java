package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.progression.StatScalingResult;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.stat.StatUnit;

import java.util.Objects;

/*
 * Composition helpers for the architecture's final calculation step.
 *
 * StatScalingService owns how much of a stat the player has earned.
 * Equipment applicability owns whether that stat applies in this context and
 * at what strength. This class combines those two concepts without knowing
 * anything about a particular Minecraft gameplay hook.
 */
public final class EquipmentValueService {

    private EquipmentValueService() {
    }

    /*
     * Returns the stat's earned gameplay bonus after equipment applicability
     * has been applied. The returned unit is the stat's own StatUnit:
     * percentage points, hearts, blocks, levels, etc.
     */
    public static double scaledBonus(
            PlayerEssenceData playerData,
            StatDefinition stat,
            double applicabilityStrength
    ) {
        Objects.requireNonNull(playerData, "Player Essence data cannot be null");
        Objects.requireNonNull(stat, "Stat cannot be null");

        if (!Double.isFinite(applicabilityStrength) || applicabilityStrength < 0.0) {
            throw new IllegalArgumentException(
                    "Applicability strength must be finite and non-negative"
            );
        }

        StatScalingResult scaling = StatScalingService.evaluate(playerData, stat);
        return scaling.scaledBonus() * applicabilityStrength;
    }

    public static double applyPercentBonus(
            PlayerEssenceData playerData,
            StatDefinition stat,
            double applicabilityStrength,
            double baseValue
    ) {
        Objects.requireNonNull(playerData, "Player Essence data cannot be null");
        Objects.requireNonNull(stat, "Stat cannot be null");

        if (stat.unit() != StatUnit.PERCENT) {
            throw new IllegalArgumentException(
                    "Stat " + stat.id() + " is not a percentage stat"
            );
        }

        if (!Double.isFinite(baseValue) || baseValue < 0.0) {
            throw new IllegalArgumentException(
                    "Base value must be finite and non-negative"
            );
        }

        double effectivePercent = scaledBonus(
                playerData,
                stat,
                applicabilityStrength
        );

        return baseValue * (1.0 + effectivePercent / 100.0);
    }
}
