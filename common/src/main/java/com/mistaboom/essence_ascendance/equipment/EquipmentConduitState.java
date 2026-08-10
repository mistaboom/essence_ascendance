package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.stat.StatDefinition;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record EquipmentConduitState(
        Map<EquipmentConduitType, Double> conduitStrengths
) {

    public EquipmentConduitState {

        Objects.requireNonNull(
                conduitStrengths,
                "Conduit strengths cannot be null"
        );


        Map<EquipmentConduitType, Double> validated =
                new EnumMap<>(
                        EquipmentConduitType.class
                );


        for (Map.Entry<EquipmentConduitType, Double> entry :
                conduitStrengths.entrySet()) {

            EquipmentConduitType conduit =
                    Objects.requireNonNull(
                            entry.getKey(),
                            "Conduit type cannot be null"
                    );


            Double strengthObject =
                    Objects.requireNonNull(
                            entry.getValue(),
                            "Conduit strength cannot be null"
                    );


            double strength =
                    strengthObject;


            if (!Double.isFinite(
                    strength
            )) {

                throw new IllegalArgumentException(
                        "Conduit strength for "
                                + conduit
                                + " must be finite"
                );
            }


            if (strength < 0.0
                    || strength > 1.0) {

                throw new IllegalArgumentException(
                        "Conduit strength for "
                                + conduit
                                + " must be between 0.0 and 1.0"
                );
            }


            /*
             * Zero-strength conduits do not need to be stored.
             */

            if (strength > 0.0) {

                validated.put(
                        conduit,
                        strength
                );
            }
        }


        conduitStrengths =
                Collections.unmodifiableMap(
                        validated
                );
    }


    /*
     * ============================================================
     * FACTORIES
     * ============================================================
     */

    public static EquipmentConduitState none() {

        return new EquipmentConduitState(
                Map.of()
        );
    }


    /*
     * Creates one or more fully-active conduits.
     *
     * Useful for weapons/tools, which normally operate as binary
     * conduit channels.
     */

    public static EquipmentConduitState of(
            EquipmentConduitType... conduits
    ) {

        Map<EquipmentConduitType, Double> strengths =
                new EnumMap<>(
                        EquipmentConduitType.class
                );


        for (EquipmentConduitType conduit :
                conduits) {

            strengths.put(
                    Objects.requireNonNull(
                            conduit,
                            "Conduit cannot be null"
                    ),
                    1.0
            );
        }


        return new EquipmentConduitState(
                strengths
        );
    }


    public static EquipmentConduitState ofStrength(
            EquipmentConduitType conduit,
            double strength
    ) {

        return new EquipmentConduitState(
                Map.of(
                        conduit,
                        strength
                )
        );
    }


    /*
     * ============================================================
     * CONDUIT ACCESS
     * ============================================================
     */

    public double strength(
            EquipmentConduitType conduit
    ) {

        return conduitStrengths.getOrDefault(
                conduit,
                0.0
        );
    }


    public boolean isActive(
            EquipmentConduitType conduit
    ) {

        return strength(
                conduit
        ) > 0.0;
    }


    /*
     * Returns the effective conduit strength for a stat.
     *
     * IMPORTANT:
     *
     * If a stat allows multiple conduits, we use the strongest
     * currently-active conduit rather than adding them together.
     *
     * Example:
     *
     * Looting can work through melee, ranged, or magic.
     *
     * Having multiple qualifying equipment contexts must NOT
     * multiply or double-dip the Looting bonus.
     */

    public double activationStrength(
            StatDefinition stat
    ) {

        double strongest =
                0.0;


        for (EquipmentConduitType conduit :
                StatConduits.validConduitsFor(
                        stat
                )) {

            strongest =
                    Math.max(
                            strongest,
                            strength(
                                    conduit
                            )
                    );
        }


        return strongest;
    }


    public boolean activates(
            StatDefinition stat
    ) {

        return activationStrength(
                stat
        ) > 0.0;
    }


    /*
     * Convenience method for future gameplay effect code.
     *
     * Full scaled stat bonus:
     *
     *     +20% movement speed
     *
     * Wearing 55% worth of Ascendance armor:
     *
     *     +20% × 0.55 = +11%
     */

    public double applyTo(
            StatDefinition stat,
            double fullBonus
    ) {

        return fullBonus
                * activationStrength(
                stat
        );
    }
}