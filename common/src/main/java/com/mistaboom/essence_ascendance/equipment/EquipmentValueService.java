package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.progression.StatScalingResult;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.stat.StatUnit;

import java.util.Objects;

/*
 * Small composition helper for the architecture's final step:
 *
 * baseline x applicable invested percentage bonus.
 *
 * This does not apply Minecraft attributes/events yet. It provides one
 * authoritative calculation that later gameplay hooks and debug verification
 * can share.
 */
public final class EquipmentValueService {

    private EquipmentValueService() {
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

        if (!Double.isFinite(applicabilityStrength) || applicabilityStrength < 0.0) {
            throw new IllegalArgumentException(
                    "Applicability strength must be finite and non-negative"
            );
        }

        if (!Double.isFinite(baseValue) || baseValue < 0.0) {
            throw new IllegalArgumentException(
                    "Base value must be finite and non-negative"
            );
        }

        StatScalingResult scaling = StatScalingService.evaluate(playerData, stat);
        double effectivePercent = scaling.scaledBonus() * applicabilityStrength;

        return baseValue * (1.0 + effectivePercent / 100.0);
    }
}
