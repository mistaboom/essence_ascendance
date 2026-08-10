package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ArmorChassisConfig {

    private final double armorDefenseWeight;

    private final double armorVitalityWeight;

    private final double toughnessDefenseWeight;

    private final double toughnessVitalityWeight;

    private final Map<ResourceLocation, TierRange> tierRanges;


    public ArmorChassisConfig(
            double armorDefenseWeight,
            double armorVitalityWeight,
            double toughnessDefenseWeight,
            double toughnessVitalityWeight,
            Map<ResourceLocation, TierRange> tierRanges
    ) {

        validateWeight(
                armorDefenseWeight,
                "armorDefenseWeight"
        );

        validateWeight(
                armorVitalityWeight,
                "armorVitalityWeight"
        );

        validateWeight(
                toughnessDefenseWeight,
                "toughnessDefenseWeight"
        );

        validateWeight(
                toughnessVitalityWeight,
                "toughnessVitalityWeight"
        );


        if (armorDefenseWeight
                + armorVitalityWeight
                <= 0.0) {

            throw new IllegalArgumentException(
                    "Armor development weights cannot both be zero"
            );
        }


        if (toughnessDefenseWeight
                + toughnessVitalityWeight
                <= 0.0) {

            throw new IllegalArgumentException(
                    "Toughness development weights cannot both be zero"
            );
        }


        Objects.requireNonNull(
                tierRanges,
                "Tier ranges cannot be null"
        );


        this.armorDefenseWeight =
                armorDefenseWeight;

        this.armorVitalityWeight =
                armorVitalityWeight;

        this.toughnessDefenseWeight =
                toughnessDefenseWeight;

        this.toughnessVitalityWeight =
                toughnessVitalityWeight;

        this.tierRanges =
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(
                                tierRanges
                        )
                );
    }


    public double armorDefenseWeight() {
        return armorDefenseWeight;
    }


    public double armorVitalityWeight() {
        return armorVitalityWeight;
    }


    public double toughnessDefenseWeight() {
        return toughnessDefenseWeight;
    }


    public double toughnessVitalityWeight() {
        return toughnessVitalityWeight;
    }


    public Map<ResourceLocation, TierRange> tierRanges() {
        return tierRanges;
    }


    public TierRange rangeFor(
            AscendanceTierDefinition tier
    ) {

        TierRange range =
                tierRanges.get(
                        tier.id()
                );


        if (range == null) {

            throw new IllegalStateException(
                    "No armor chassis range exists for tier "
                            + tier.id()
            );
        }


        return range;
    }


    private static void validateWeight(
            double value,
            String name
    ) {

        if (!Double.isFinite(
                value
        )
                || value < 0.0) {

            throw new IllegalArgumentException(
                    name
                            + " must be finite and non-negative"
            );
        }
    }


    public record TierRange(
            double minArmor,
            double maxArmor,
            double minToughness,
            double maxToughness
    ) {

        public TierRange {

            validateRangeValue(
                    minArmor,
                    "minArmor"
            );

            validateRangeValue(
                    maxArmor,
                    "maxArmor"
            );

            validateRangeValue(
                    minToughness,
                    "minToughness"
            );

            validateRangeValue(
                    maxToughness,
                    "maxToughness"
            );


            if (maxArmor < minArmor) {

                throw new IllegalArgumentException(
                        "Maximum armor cannot be below minimum armor"
                );
            }


            if (maxToughness < minToughness) {

                throw new IllegalArgumentException(
                        "Maximum toughness cannot be below minimum toughness"
                );
            }
        }


        private static void validateRangeValue(
                double value,
                String name
        ) {

            if (!Double.isFinite(
                    value
            )
                    || value < 0.0) {

                throw new IllegalArgumentException(
                        name
                                + " must be finite and non-negative"
                );
            }
        }
    }
}