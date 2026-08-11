package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;

import java.util.Objects;

public record WeaponChassisResult(
        AscendanceTierDefinition tier,

        double meleeDamageDevelopment,
        double meleeAttackSpeedDevelopment,

        double rangedDamageDevelopment,
        double rangedAttackSpeedDevelopment,

        double magicDamageDevelopment,
        double magicCastSpeedDevelopment,

        WeaponChassisConfig.TierRange tierRange,

        double targetMeleeDamage,
        double targetMeleeAttackSpeed,

        double targetRangedDamage,
        double targetRangedAttackSpeed,

        double targetMagicDamage,
        double targetMagicCastSpeed
) {

    public WeaponChassisResult {

        Objects.requireNonNull(
                tier,
                "Tier cannot be null"
        );

        Objects.requireNonNull(
                tierRange,
                "Weapon chassis tier range cannot be null"
        );


        validateProgress(
                meleeDamageDevelopment
        );

        validateProgress(
                meleeAttackSpeedDevelopment
        );

        validateProgress(
                rangedDamageDevelopment
        );

        validateProgress(
                rangedAttackSpeedDevelopment
        );

        validateProgress(
                magicDamageDevelopment
        );

        validateProgress(
                magicCastSpeedDevelopment
        );


        validateTarget(
                targetMeleeDamage,
                false
        );

        validateTarget(
                targetMeleeAttackSpeed,
                true
        );

        validateTarget(
                targetRangedDamage,
                false
        );

        validateTarget(
                targetRangedAttackSpeed,
                true
        );

        validateTarget(
                targetMagicDamage,
                false
        );

        validateTarget(
                targetMagicCastSpeed,
                true
        );
    }


    /*
     * Full-draw duration once Ranged Attack Speed is actually
     * applied to gameplay.
     */

    public int rangedDrawTicks() {

        return rateToTicks(
                targetRangedAttackSpeed
        );
    }


    /*
     * Cast duration once Magic Cast Speed is actually applied.
     */

    public int magicCastTicks() {

        return rateToTicks(
                targetMagicCastSpeed
        );
    }


    private static int rateToTicks(
            double actionsPerSecond
    ) {

        return Math.max(
                1,
                (int) Math.round(
                        20.0
                                / actionsPerSecond
                )
        );
    }


    private static void validateProgress(
            double value
    ) {

        if (!Double.isFinite(
                value
        )
                || value < 0.0
                || value > 1.0) {

            throw new IllegalArgumentException(
                    "Weapon chassis development must be between 0 and 1"
            );
        }
    }


    private static void validateTarget(
            double value,
            boolean strictlyPositive
    ) {

        if (!Double.isFinite(
                value
        )
                || (
                strictlyPositive
                        ? value <= 0.0
                        : value < 0.0
        )) {

            throw new IllegalArgumentException(
                    "Invalid weapon chassis target: "
                            + value
            );
        }
    }
}