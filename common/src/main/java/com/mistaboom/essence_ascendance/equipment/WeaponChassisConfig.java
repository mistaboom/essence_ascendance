package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class WeaponChassisConfig {

    private final Map<ResourceLocation, TierRange> tierRanges;


    public WeaponChassisConfig(
            Map<ResourceLocation, TierRange> tierRanges
    ) {

        Objects.requireNonNull(
                tierRanges,
                "Weapon chassis tier ranges cannot be null"
        );


        this.tierRanges =
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(
                                tierRanges
                        )
                );
    }


    public Map<ResourceLocation, TierRange> tierRanges() {

        return tierRanges;
    }


    public TierRange rangeFor(
            AscendanceTierDefinition tier
    ) {

        Objects.requireNonNull(
                tier,
                "Ascendance tier cannot be null"
        );


        TierRange range =
                tierRanges.get(
                        tier.id()
                );


        if (range == null) {

            throw new IllegalStateException(
                    "No weapon chassis range exists for tier "
                            + tier.id()
            );
        }


        return range;
    }


    public record TierRange(
            double minMeleeDamage,
            double maxMeleeDamage,

            double minMeleeAttackSpeed,
            double maxMeleeAttackSpeed,

            double minRangedDamage,
            double maxRangedDamage,

            double minRangedAttackSpeed,
            double maxRangedAttackSpeed,

            double minMagicDamage,
            double maxMagicDamage,

            double minMagicCastSpeed,
            double maxMagicCastSpeed
    ) {

        public TierRange {

            validateRange(
                    "melee damage",
                    minMeleeDamage,
                    maxMeleeDamage,
                    false
            );

            validateRange(
                    "melee attack speed",
                    minMeleeAttackSpeed,
                    maxMeleeAttackSpeed,
                    true
            );

            validateRange(
                    "ranged damage",
                    minRangedDamage,
                    maxRangedDamage,
                    false
            );

            validateRange(
                    "ranged attack speed",
                    minRangedAttackSpeed,
                    maxRangedAttackSpeed,
                    true
            );

            validateRange(
                    "magic damage",
                    minMagicDamage,
                    maxMagicDamage,
                    false
            );

            validateRange(
                    "magic cast speed",
                    minMagicCastSpeed,
                    maxMagicCastSpeed,
                    true
            );
        }


        private static void validateRange(
                String name,
                double minimum,
                double maximum,
                boolean strictlyPositive
        ) {

            if (!Double.isFinite(
                    minimum
            )
                    || !Double.isFinite(
                    maximum
            )) {

                throw new IllegalArgumentException(
                        name
                                + " range must be finite"
                );
            }


            if (strictlyPositive
                    ? minimum <= 0.0
                    : minimum < 0.0) {

                throw new IllegalArgumentException(
                        name
                                + " minimum is invalid: "
                                + minimum
                );
            }


            if (maximum < minimum) {

                throw new IllegalArgumentException(
                        name
                                + " maximum cannot be below minimum"
                );
            }
        }
    }
}