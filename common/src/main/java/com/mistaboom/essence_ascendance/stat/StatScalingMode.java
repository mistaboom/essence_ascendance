package com.mistaboom.essence_ascendance.stat;

public enum StatScalingMode {

    /*
     * Normal stat.
     *
     * Investment produces a bonus through StatScalingService.
     *
     * Examples:
     *
     * movement_speed
     * max_health
     * projectile_speed
     * attack_knockback
     * durability_efficiency
     */
    BONUS,


    /*
     * Chassis-driving stat.
     *
     * Investment controls an absolute equipment property rather
     * than producing an additive bonus.
     *
     * Examples:
     *
     * melee_damage
     * melee_attack_speed
     * ranged_damage
     * ranged_attack_speed
     * magic_damage
     * magic_cast_speed
     */
    CHASSIS
}