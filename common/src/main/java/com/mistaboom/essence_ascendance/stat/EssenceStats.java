package com.mistaboom.essence_ascendance.stat;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.resources.ResourceLocation;

public final class EssenceStats {

    /*
     * ============================================================
     * OFFENSE
     * ============================================================
     */

    public static final StatDefinition MELEE_DAMAGE =
            register(
                    "melee_damage",
                    "Melee Damage",
                    StatCategory.OFFENSE,
                    StatUnit.PERCENT
            );

    public static final StatDefinition MELEE_ATTACK_SPEED =
            register(
                    "melee_attack_speed",
                    "Melee Attack Speed",
                    StatCategory.OFFENSE,
                    StatUnit.PERCENT
            );

    public static final StatDefinition RANGED_DAMAGE =
            register(
                    "ranged_damage",
                    "Ranged Damage",
                    StatCategory.OFFENSE,
                    StatUnit.PERCENT
            );

    public static final StatDefinition RANGED_ATTACK_SPEED =
            register(
                    "ranged_attack_speed",
                    "Ranged Attack Speed",
                    StatCategory.OFFENSE,
                    StatUnit.PERCENT
            );

    public static final StatDefinition PROJECTILE_SPEED =
            register(
                    "projectile_speed",
                    "Projectile Speed",
                    StatCategory.OFFENSE,
                    StatUnit.PERCENT
            );

    public static final StatDefinition MAGIC_DAMAGE =
            register(
                    "magic_damage",
                    "Magic Damage",
                    StatCategory.OFFENSE,
                    StatUnit.PERCENT
            );

    public static final StatDefinition MAGIC_CAST_SPEED =
            register(
                    "magic_cast_speed",
                    "Magic Cast Speed",
                    StatCategory.OFFENSE,
                    StatUnit.PERCENT
            );

    public static final StatDefinition ATTACK_KNOCKBACK =
            register(
                    "attack_knockback",
                    "Attack Knockback",
                    StatCategory.OFFENSE,
                    StatUnit.PERCENT
            );

    public static final StatDefinition DAMAGE_REFLECTION =
            register(
                    "damage_reflection",
                    "Damage Reflection",
                    StatCategory.OFFENSE,
                    StatUnit.PERCENT
            );


    /*
     * ============================================================
     * DEFENSE
     * ============================================================
     */

    public static final StatDefinition MELEE_RESISTANCE =
            register(
                    "melee_resistance",
                    "Melee Resistance",
                    StatCategory.DEFENSE,
                    StatUnit.PERCENT
            );

    public static final StatDefinition RANGED_RESISTANCE =
            register(
                    "ranged_resistance",
                    "Ranged Resistance",
                    StatCategory.DEFENSE,
                    StatUnit.PERCENT
            );

    public static final StatDefinition MAGIC_RESISTANCE =
            register(
                    "magic_resistance",
                    "Magic Resistance",
                    StatCategory.DEFENSE,
                    StatUnit.PERCENT
            );

    public static final StatDefinition FALL_RESISTANCE =
            register(
                    "fall_resistance",
                    "Fall Resistance",
                    StatCategory.DEFENSE,
                    StatUnit.PERCENT
            );

    public static final StatDefinition KNOCKBACK_RESISTANCE =
            register(
                    "knockback_resistance",
                    "Knockback Resistance",
                    StatCategory.DEFENSE,
                    StatUnit.PERCENT
            );

    public static final StatDefinition FIRE_RESISTANCE =
            register(
                    "fire_resistance",
                    "Fire Resistance",
                    StatCategory.DEFENSE,
                    StatUnit.PERCENT
            );

    public static final StatDefinition EXPLOSION_RESISTANCE =
            register(
                    "explosion_resistance",
                    "Explosion Resistance",
                    StatCategory.DEFENSE,
                    StatUnit.PERCENT
            );

    public static final StatDefinition STATUS_RESISTANCE =
            register(
                    "status_resistance",
                    "Status Resistance",
                    StatCategory.DEFENSE,
                    StatUnit.PERCENT
            );


    /*
     * ============================================================
     * VITALITY
     * ============================================================
     */

    public static final StatDefinition MAX_HEALTH =
            register(
                    "max_health",
                    "Maximum Health",
                    StatCategory.VITALITY,
                    StatUnit.HEARTS
            );

    public static final StatDefinition HEALTH_REGENERATION =
            register(
                    "health_regeneration",
                    "Health Regeneration",
                    StatCategory.VITALITY,
                    StatUnit.HEARTS_PER_SECOND
            );

    public static final StatDefinition HEALING_EFFECTIVENESS =
            register(
                    "healing_effectiveness",
                    "Healing Effectiveness",
                    StatCategory.VITALITY,
                    StatUnit.PERCENT
            );

    public static final StatDefinition HUNGER_EFFICIENCY =
            register(
                    "hunger_efficiency",
                    "Hunger Efficiency",
                    StatCategory.VITALITY,
                    StatUnit.PERCENT
            );

    public static final StatDefinition BREATH_HOLD =
            register(
                    "breath_hold",
                    "Breath Hold",
                    StatCategory.VITALITY,
                    StatUnit.SECONDS
            );


    /*
     * ============================================================
     * MOBILITY
     * ============================================================
     */

    public static final StatDefinition MOVEMENT_SPEED =
            register(
                    "movement_speed",
                    "Movement Speed",
                    StatCategory.MOBILITY,
                    StatUnit.PERCENT
            );

    public static final StatDefinition SWIM_SPEED =
            register(
                    "swim_speed",
                    "Swim Speed",
                    StatCategory.MOBILITY,
                    StatUnit.PERCENT
            );

    public static final StatDefinition JUMP_HEIGHT =
            register(
                    "jump_height",
                    "Jump Height",
                    StatCategory.MOBILITY,
                    StatUnit.PERCENT
            );

    public static final StatDefinition STEP_HEIGHT =
            register(
                    "step_height",
                    "Step Height",
                    StatCategory.MOBILITY,
                    StatUnit.BLOCKS
            );

    public static final StatDefinition FLIGHT_SPEED =
            register(
                    "flight_speed",
                    "Flight Speed",
                    StatCategory.MOBILITY,
                    StatUnit.PERCENT
            );

    /*
     * ============================================================
     * GATHERING
     * ============================================================
     */

    public static final StatDefinition MINING_SPEED =
            register(
                    "mining_speed",
                    "Mining Speed",
                    StatCategory.GATHERING,
                    StatUnit.PERCENT
            );

    public static final StatDefinition MINING_LEVEL =
            register(
                    "mining_level",
                    "Mining Level",
                    StatCategory.GATHERING,
                    StatUnit.LEVELS
            );

    public static final StatDefinition FORTUNE =
            register(
                    "fortune",
                    "Fortune",
                    StatCategory.GATHERING,
                    StatUnit.LEVELS
            );

    public static final StatDefinition LOOTING =
            register(
                    "looting",
                    "Looting",
                    StatCategory.GATHERING,
                    StatUnit.LEVELS
            );

    public static final StatDefinition REACH =
            register(
                    "reach",
                    "Reach",
                    StatCategory.GATHERING,
                    StatUnit.BLOCKS
            );

    public static final StatDefinition EXPERIENCE_GAIN =
            register(
                    "experience_gain",
                    "Experience Gain",
                    StatCategory.GATHERING,
                    StatUnit.PERCENT
            );


    /*
     * ============================================================
     * UTILITY
     * ============================================================
     */

    public static final StatDefinition LUCK =
            register(
                    "luck",
                    "Luck",
                    StatCategory.UTILITY,
                    StatUnit.FLAT
            );

    public static final StatDefinition SNEAK_SPEED =
            register(
                    "sneak_speed",
                    "Sneak Speed",
                    StatCategory.UTILITY,
                    StatUnit.PERCENT
            );

    public static final StatDefinition ITEM_USE_SPEED =
            register(
                    "item_use_speed",
                    "Item Use Speed",
                    StatCategory.UTILITY,
                    StatUnit.PERCENT
            );

    public static final StatDefinition DURABILITY_EFFICIENCY =
            register(
                    "durability_efficiency",
                    "Durability Efficiency",
                    StatCategory.UTILITY,
                    StatUnit.PERCENT
            );


    private EssenceStats() {
    }

    private static StatDefinition register(
            String path,
            String displayName,
            StatCategory category,
            StatUnit unit
    ) {
        return EssenceStatRegistry.register(
                ResourceLocation.fromNamespaceAndPath(
                        EssenceAscendance.MOD_ID,
                        path
                ),
                displayName,
                category,
                unit
        );
    }

    public static void init() {
        // Calling this method forces Java to initialize this class,
        // registering all built-in Essence Ascendance stats.
    }
}