package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;

public final class StatScalingDefaults {

    private static final Map<ResourceLocation, Double> VALUES =
            new LinkedHashMap<>();


    private StatScalingDefaults() {
    }


    static {

        /*
         * ========================================================
         * OFFENSE
         * ========================================================
         *
         * PERCENT values are written as human-readable percentages.
         *
         * 100.0 = +100%
         * 50.0  = +50%
         */

        register(
                "melee_damage",
                100.0
        );

        register(
                "melee_attack_speed",
                50.0
        );

        register(
                "ranged_damage",
                100.0
        );

        register(
                "ranged_attack_speed",
                50.0
        );

        register(
                "projectile_speed",
                50.0
        );

        register(
                "magic_damage",
                100.0
        );

        register(
                "magic_cast_speed",
                50.0
        );

        register(
                "attack_knockback",
                100.0
        );

        register(
                "damage_reflection",
                25.0
        );


        /*
         * ========================================================
         * DEFENSE
         * ========================================================
         */

        register(
                "melee_resistance",
                30.0
        );

        register(
                "ranged_resistance",
                30.0
        );

        register(
                "magic_resistance",
                30.0
        );

        register(
                "fall_resistance",
                50.0
        );

        register(
                "knockback_resistance",
                50.0
        );

        register(
                "fire_resistance",
                50.0
        );

        register(
                "explosion_resistance",
                40.0
        );

        register(
                "status_resistance",
                30.0
        );


        /*
         * ========================================================
         * VITALITY
         * ========================================================
         */

        register(
                "max_health",
                20.0
        );

        register(
                "health_regeneration",
                1.0
        );

        register(
                "healing_effectiveness",
                50.0
        );

        register(
                "hunger_efficiency",
                50.0
        );

        register(
                "breath_hold",
                60.0
        );


        /*
         * ========================================================
         * MOBILITY
         * ========================================================
         */

        register(
                "movement_speed",
                50.0
        );

        register(
                "swim_speed",
                100.0
        );

        register(
                "jump_height",
                50.0
        );

        register(
                "step_height",
                1.0
        );

        register(
                "flight_speed",
                50.0
        );


        /*
         * ========================================================
         * GATHERING
         * ========================================================
         */

        register(
                "mining_speed",
                100.0
        );

        register(
                "mining_level",
                2.0
        );

        register(
                "fortune",
                3.0
        );

        register(
                "looting",
                3.0
        );

        register(
                "reach",
                4.0
        );

        register(
                "experience_gain",
                100.0
        );


        /*
         * ========================================================
         * UTILITY
         * ========================================================
         */

        register(
                "luck",
                5.0
        );

        register(
                "sneak_speed",
                50.0
        );

        register(
                "item_use_speed",
                50.0
        );

        register(
                "durability_efficiency",
                50.0
        );
    }


    public static OptionalDouble get(
            ResourceLocation statId
    ) {

        Double value =
                VALUES.get(
                        statId
                );


        if (value == null) {
            return OptionalDouble.empty();
        }


        return OptionalDouble.of(
                value
        );
    }


    public static Map<ResourceLocation, Double> values() {

        return Collections.unmodifiableMap(
                VALUES
        );
    }


    private static void register(
            String path,
            double transcendentMaxBonus
    ) {

        if (!Double.isFinite(
                transcendentMaxBonus
        )) {

            throw new IllegalArgumentException(
                    "Stat bonus must be finite"
            );
        }


        if (transcendentMaxBonus < 0.0) {

            throw new IllegalArgumentException(
                    "Stat bonus cannot be negative"
            );
        }


        ResourceLocation id =
                ResourceLocation.fromNamespaceAndPath(
                        EssenceAscendance.MOD_ID,
                        path
                );


        Double previous =
                VALUES.putIfAbsent(
                        id,
                        transcendentMaxBonus
                );


        if (previous != null) {

            throw new IllegalStateException(
                    "Duplicate stat scaling default: "
                            + id
            );
        }
    }
}