package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class StatConduits {

    private static final Map<
            ResourceLocation,
            Set<EquipmentConduitType>
            > RULES =
            new LinkedHashMap<>();


    static {

        /*
         * ========================================================
         * MELEE
         * ========================================================
         */

        register(
                EssenceStats.MELEE_DAMAGE,
                EquipmentConduitType.MELEE_WEAPON
        );

        register(
                EssenceStats.MELEE_ATTACK_SPEED,
                EquipmentConduitType.MELEE_WEAPON
        );

        register(
                EssenceStats.ATTACK_KNOCKBACK,
                EquipmentConduitType.MELEE_WEAPON
        );


        /*
         * ========================================================
         * RANGED
         * ========================================================
         */

        register(
                EssenceStats.RANGED_DAMAGE,
                EquipmentConduitType.RANGED_WEAPON
        );

        register(
                EssenceStats.RANGED_ATTACK_SPEED,
                EquipmentConduitType.RANGED_WEAPON
        );

        register(
                EssenceStats.PROJECTILE_SPEED,
                EquipmentConduitType.RANGED_WEAPON
        );


        /*
         * ========================================================
         * MAGIC
         * ========================================================
         */

        register(
                EssenceStats.MAGIC_DAMAGE,
                EquipmentConduitType.MAGIC_WEAPON
        );

        register(
                EssenceStats.MAGIC_CAST_SPEED,
                EquipmentConduitType.MAGIC_WEAPON
        );


        /*
         * ========================================================
         * ARMOR-CHANNEL OFFENSE
         * ========================================================
         */

        register(
                EssenceStats.DAMAGE_REFLECTION,
                EquipmentConduitType.ARMOR_SET
        );


        /*
         * ========================================================
         * DEFENSE
         * ========================================================
         */

        register(
                EssenceStats.MELEE_RESISTANCE,
                EquipmentConduitType.ARMOR_SET
        );

        register(
                EssenceStats.RANGED_RESISTANCE,
                EquipmentConduitType.ARMOR_SET
        );

        register(
                EssenceStats.MAGIC_RESISTANCE,
                EquipmentConduitType.ARMOR_SET
        );

        register(
                EssenceStats.FALL_RESISTANCE,
                EquipmentConduitType.ARMOR_SET
        );

        register(
                EssenceStats.KNOCKBACK_RESISTANCE,
                EquipmentConduitType.ARMOR_SET
        );

        register(
                EssenceStats.FIRE_RESISTANCE,
                EquipmentConduitType.ARMOR_SET
        );

        register(
                EssenceStats.EXPLOSION_RESISTANCE,
                EquipmentConduitType.ARMOR_SET
        );

        register(
                EssenceStats.STATUS_RESISTANCE,
                EquipmentConduitType.ARMOR_SET
        );


        /*
         * ========================================================
         * VITALITY
         * ========================================================
         */

        register(
                EssenceStats.MAX_HEALTH,
                EquipmentConduitType.ARMOR_SET
        );

        register(
                EssenceStats.HEALTH_REGENERATION,
                EquipmentConduitType.ARMOR_SET
        );

        register(
                EssenceStats.HEALING_EFFECTIVENESS,
                EquipmentConduitType.ARMOR_SET
        );

        register(
                EssenceStats.HUNGER_EFFICIENCY,
                EquipmentConduitType.ARMOR_SET
        );

        register(
                EssenceStats.BREATH_HOLD,
                EquipmentConduitType.ARMOR_SET
        );


        /*
         * ========================================================
         * MOBILITY
         * ========================================================
         */

        register(
                EssenceStats.MOVEMENT_SPEED,
                EquipmentConduitType.ARMOR_SET
        );

        register(
                EssenceStats.SWIM_SPEED,
                EquipmentConduitType.ARMOR_SET
        );

        register(
                EssenceStats.JUMP_HEIGHT,
                EquipmentConduitType.ARMOR_SET
        );

        register(
                EssenceStats.STEP_HEIGHT,
                EquipmentConduitType.ARMOR_SET
        );

        register(
                EssenceStats.FLIGHT_SPEED,
                EquipmentConduitType.ARMOR_SET
        );


        /*
         * ========================================================
         * GATHERING
         * ========================================================
         */

        register(
                EssenceStats.MINING_SPEED,
                EquipmentConduitType.TOOL
        );

        register(
                EssenceStats.MINING_LEVEL,
                EquipmentConduitType.TOOL
        );

        register(
                EssenceStats.FORTUNE,
                EquipmentConduitType.TOOL
        );


        /*
         * Looting works through all Essence combat channels.
         */

        register(
                EssenceStats.LOOTING,
                EquipmentConduitType.MELEE_WEAPON,
                EquipmentConduitType.RANGED_WEAPON,
                EquipmentConduitType.MAGIC_WEAPON
        );


        /*
         * Reach and XP gain are deliberately player-wide.
         */

        register(
                EssenceStats.REACH,
                EquipmentConduitType.ARMOR_SET
        );

        register(
                EssenceStats.EXPERIENCE_GAIN,
                EquipmentConduitType.ARMOR_SET
        );


        /*
         * ========================================================
         * UTILITY
         * ========================================================
         */

        register(
                EssenceStats.LUCK,
                EquipmentConduitType.ARMOR_SET
        );

        register(
                EssenceStats.SNEAK_SPEED,
                EquipmentConduitType.ARMOR_SET
        );

        register(
                EssenceStats.ITEM_USE_SPEED,
                EquipmentConduitType.ARMOR_SET
        );


        /*
         * Durability efficiency applies to any qualifying
         * Essence Ascendance equipment that uses durability.
         */

        register(
                EssenceStats.DURABILITY_EFFICIENCY,
                EquipmentConduitType.MELEE_WEAPON,
                EquipmentConduitType.RANGED_WEAPON,
                EquipmentConduitType.MAGIC_WEAPON,
                EquipmentConduitType.TOOL
        );
    }


    private StatConduits() {
    }


    public static void init() {

        /*
         * Fail loudly if a future stat is added without deciding
         * which equipment channel activates it.
         */

        for (StatDefinition stat :
                EssenceStatRegistry.values()) {

            if (!RULES.containsKey(
                    stat.id()
            )) {

                throw new IllegalStateException(
                        "No equipment conduit rule exists for stat "
                                + stat.id()
                );
            }
        }


        if (RULES.size()
                != EssenceStatRegistry.size()) {

            throw new IllegalStateException(
                    "Equipment conduit rule count does not match registered stat count"
            );
        }
    }


    public static Set<EquipmentConduitType> validConduitsFor(
            StatDefinition stat
    ) {

        Set<EquipmentConduitType> conduits =
                RULES.get(
                        stat.id()
                );


        if (conduits == null) {

            throw new IllegalStateException(
                    "No equipment conduit rule exists for stat "
                            + stat.id()
            );
        }


        return conduits;
    }


    public static boolean allows(
            StatDefinition stat,
            EquipmentConduitType conduit
    ) {

        return validConduitsFor(
                stat
        ).contains(
                conduit
        );
    }


    private static void register(
            StatDefinition stat,
            EquipmentConduitType... conduits
    ) {

        if (conduits.length == 0) {

            throw new IllegalArgumentException(
                    "Stat conduit rule requires at least one conduit"
            );
        }


        EnumSet<EquipmentConduitType> set =
                EnumSet.noneOf(
                        EquipmentConduitType.class
                );


        Collections.addAll(
                set,
                conduits
        );


        Set<EquipmentConduitType> previous =
                RULES.putIfAbsent(
                        stat.id(),
                        Collections.unmodifiableSet(
                                set
                        )
                );


        if (previous != null) {

            throw new IllegalStateException(
                    "Duplicate equipment conduit rule for stat "
                            + stat.id()
            );
        }
    }
}