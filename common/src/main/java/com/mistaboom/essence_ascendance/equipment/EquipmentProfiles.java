package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import net.minecraft.resources.ResourceLocation;

public final class EquipmentProfiles {

    public static final EquipmentProfileDefinition ARMOR =
            EquipmentProfileRegistry.register(
                    EquipmentProfileDefinition.builder(
                                    id("armor"),
                                    "Ascendance Armor"
                            )
                            .baseline(EquipmentBaselineProperty.ARMOR)
                            .baseline(EquipmentBaselineProperty.TOUGHNESS)
                            .baseline(EquipmentBaselineProperty.DURABILITY)
                            .stat(EquipmentActivationType.WORN, EssenceStats.DAMAGE_REFLECTION)
                            .stat(EquipmentActivationType.WORN, EssenceStats.MELEE_RESISTANCE)
                            .stat(EquipmentActivationType.WORN, EssenceStats.RANGED_RESISTANCE)
                            .stat(EquipmentActivationType.WORN, EssenceStats.MAGIC_RESISTANCE)
                            .stat(EquipmentActivationType.WORN, EssenceStats.FALL_RESISTANCE)
                            .stat(EquipmentActivationType.WORN, EssenceStats.KNOCKBACK_RESISTANCE)
                            .stat(EquipmentActivationType.WORN, EssenceStats.FIRE_RESISTANCE)
                            .stat(EquipmentActivationType.WORN, EssenceStats.EXPLOSION_RESISTANCE)
                            .stat(EquipmentActivationType.WORN, EssenceStats.STATUS_RESISTANCE)
                            .stat(EquipmentActivationType.WORN, EssenceStats.MAX_HEALTH)
                            .stat(EquipmentActivationType.WORN, EssenceStats.HEALTH_REGENERATION)
                            .stat(EquipmentActivationType.WORN, EssenceStats.HEALING_EFFECTIVENESS)
                            .stat(EquipmentActivationType.WORN, EssenceStats.HUNGER_EFFICIENCY)
                            .stat(EquipmentActivationType.WORN, EssenceStats.BREATH_HOLD)
                            .stat(EquipmentActivationType.WORN, EssenceStats.MOVEMENT_SPEED)
                            .stat(EquipmentActivationType.WORN, EssenceStats.SWIM_SPEED)
                            .stat(EquipmentActivationType.WORN, EssenceStats.JUMP_HEIGHT)
                            .stat(EquipmentActivationType.WORN, EssenceStats.STEP_HEIGHT)
                            .stat(EquipmentActivationType.WORN, EssenceStats.FLIGHT_SPEED)
                            .stat(EquipmentActivationType.WORN, EssenceStats.REACH)
                            .stat(EquipmentActivationType.WORN, EssenceStats.EXPERIENCE_GAIN)
                            .stat(EquipmentActivationType.WORN, EssenceStats.LUCK)
                            .stat(EquipmentActivationType.WORN, EssenceStats.SNEAK_SPEED)
                            .stat(EquipmentActivationType.WORN, EssenceStats.DURABILITY_EFFICIENCY)
                            .build()
            );

    public static final EquipmentProfileDefinition MELEE_WEAPON =
            EquipmentProfileRegistry.register(
                    EquipmentProfileDefinition.builder(
                                    id("melee_weapon"),
                                    "Ascendance Melee Weapon"
                            )
                            .baseline(EquipmentBaselineProperty.MELEE_DAMAGE)
                            .baseline(EquipmentBaselineProperty.MELEE_ATTACK_SPEED)
                            .baseline(EquipmentBaselineProperty.DURABILITY)
                            .stat(EquipmentActivationType.HELD, EssenceStats.MELEE_DAMAGE)
                            .stat(EquipmentActivationType.HELD, EssenceStats.MELEE_ATTACK_SPEED)
                            .stat(EquipmentActivationType.HELD, EssenceStats.ATTACK_KNOCKBACK)
                            .stat(EquipmentActivationType.HELD, EssenceStats.LOOTING)
                            .stat(EquipmentActivationType.HELD, EssenceStats.DURABILITY_EFFICIENCY)
                            .build()
            );

    public static final EquipmentProfileDefinition RANGED_WEAPON =
            EquipmentProfileRegistry.register(
                    EquipmentProfileDefinition.builder(
                                    id("ranged_weapon"),
                                    "Ascendance Ranged Weapon"
                            )
                            .baseline(EquipmentBaselineProperty.RANGED_DAMAGE)
                            .baseline(EquipmentBaselineProperty.RANGED_ATTACK_SPEED)
                            .baseline(EquipmentBaselineProperty.DURABILITY)
                            .stat(EquipmentActivationType.HELD, EssenceStats.RANGED_DAMAGE)
                            .stat(EquipmentActivationType.HELD, EssenceStats.RANGED_ATTACK_SPEED)
                            .stat(EquipmentActivationType.HELD, EssenceStats.PROJECTILE_SPEED)
                            .stat(EquipmentActivationType.HELD, EssenceStats.LOOTING)
                            .stat(EquipmentActivationType.HELD, EssenceStats.DURABILITY_EFFICIENCY)
                            .build()
            );

    public static final EquipmentProfileDefinition MAGIC_CASTER =
            EquipmentProfileRegistry.register(
                    EquipmentProfileDefinition.builder(
                                    id("magic_caster"),
                                    "Ascendance Caster"
                            )
                            .baseline(EquipmentBaselineProperty.MAGIC_DAMAGE)
                            .baseline(EquipmentBaselineProperty.MAGIC_CAST_SPEED)
                            .baseline(EquipmentBaselineProperty.DURABILITY)
                            .stat(EquipmentActivationType.HELD, EssenceStats.MAGIC_DAMAGE)
                            .stat(EquipmentActivationType.HELD, EssenceStats.MAGIC_CAST_SPEED)
                            .stat(EquipmentActivationType.HELD, EssenceStats.LOOTING)
                            .stat(EquipmentActivationType.HELD, EssenceStats.DURABILITY_EFFICIENCY)
                            .build()
            );



    /*
     * ============================================================
     * ASCENDANCE TOOLS
     * ============================================================
     *
     * All four tools receive the same player-stat applicability.
     * Their inherent combat character is expressed only through independent
     * archetype baseline multipliers below. This keeps "does this stat apply?"
     * separate from "what kind of tool is this?".
     */

    public static final EquipmentProfileDefinition PICKAXE =
            registerToolProfile(
                    "pickaxe",
                    "Ascendance Pickaxe",
                    0.65,
                    0.90
            );

    public static final EquipmentProfileDefinition AXE =
            registerToolProfile(
                    "axe",
                    "Ascendance Axe",
                    1.10,
                    0.70
            );

    public static final EquipmentProfileDefinition SHOVEL =
            registerToolProfile(
                    "shovel",
                    "Ascendance Shovel",
                    0.75,
                    0.80
            );

    public static final EquipmentProfileDefinition HOE =
            registerToolProfile(
                    "hoe",
                    "Ascendance Hoe",
                    0.40,
                    1.20
            );

    private EquipmentProfiles() {
    }


    private static EquipmentProfileDefinition registerToolProfile(
            String path,
            String displayName,
            double meleeDamageMultiplier,
            double meleeAttackSpeedMultiplier
    ) {
        return EquipmentProfileRegistry.register(
                EquipmentProfileDefinition.builder(
                                id(path),
                                displayName
                        )
                        .baseline(
                                EquipmentBaselineProperty.MELEE_DAMAGE,
                                meleeDamageMultiplier
                        )
                        .baseline(
                                EquipmentBaselineProperty.MELEE_ATTACK_SPEED,
                                meleeAttackSpeedMultiplier
                        )
                        .baseline(EquipmentBaselineProperty.MINING_SPEED)
                        .baseline(EquipmentBaselineProperty.DURABILITY)
                        .stat(EquipmentActivationType.HELD, EssenceStats.MINING_SPEED)
                        .stat(EquipmentActivationType.HELD, EssenceStats.FORTUNE)
                        .stat(EquipmentActivationType.HELD, EssenceStats.REACH)
                        .stat(EquipmentActivationType.HELD, EssenceStats.EXPERIENCE_GAIN)
                        .stat(EquipmentActivationType.HELD, EssenceStats.MELEE_DAMAGE)
                        .stat(EquipmentActivationType.HELD, EssenceStats.MELEE_ATTACK_SPEED)
                        .stat(EquipmentActivationType.HELD, EssenceStats.ATTACK_KNOCKBACK)
                        .stat(EquipmentActivationType.HELD, EssenceStats.LOOTING)
                        .stat(EquipmentActivationType.HELD, EssenceStats.DURABILITY_EFFICIENCY)
                        .build()
        );
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                EssenceAscendance.MOD_ID,
                path
        );
    }

    public static void init() {
        // Forces static initialization and registration.
    }
}
