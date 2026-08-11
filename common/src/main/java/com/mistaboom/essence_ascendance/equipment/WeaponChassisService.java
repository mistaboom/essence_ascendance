package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.progression.StatScalingResult;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public final class WeaponChassisService {

    private WeaponChassisService() {
    }


    /*
     * ============================================================
     * PLAYER QUERY
     * ============================================================
     */

    public static WeaponChassisResult evaluatePotential(
            ServerPlayer player
    ) {

        Objects.requireNonNull(
                player,
                "Player cannot be null"
        );


        PlayerEssenceData playerData =
                EssenceSavedData
                        .get(
                                player.server
                        )
                        .getPlayerData(
                                player.getUUID()
                        );


        return evaluatePotential(
                playerData
        );
    }


    /*
     * ============================================================
     * DATA QUERY
     * ============================================================
     *
     * This service calculates the player's POTENTIAL weapon chassis.
     *
     * It does not inspect held equipment and does not apply Minecraft
     * attributes/projectile damage/cast behavior. Equipment context is
     * resolved separately through EquipmentConduitResolver.
     *
     * This intentionally mirrors ArmorChassisService's separation of:
     *
     *     progression -> potential chassis -> conduit activation
     */

    public static WeaponChassisResult evaluatePotential(
            PlayerEssenceData playerData
    ) {

        Objects.requireNonNull(
                playerData,
                "Player Essence data cannot be null"
        );


        StatScalingResult meleeDamage =
                StatScalingService.evaluate(
                        playerData,
                        EssenceStats.MELEE_DAMAGE
                );


        StatScalingResult meleeAttackSpeed =
                StatScalingService.evaluate(
                        playerData,
                        EssenceStats.MELEE_ATTACK_SPEED
                );


        StatScalingResult rangedDamage =
                StatScalingService.evaluate(
                        playerData,
                        EssenceStats.RANGED_DAMAGE
                );


        StatScalingResult rangedAttackSpeed =
                StatScalingService.evaluate(
                        playerData,
                        EssenceStats.RANGED_ATTACK_SPEED
                );


        StatScalingResult magicDamage =
                StatScalingService.evaluate(
                        playerData,
                        EssenceStats.MAGIC_DAMAGE
                );


        StatScalingResult magicCastSpeed =
                StatScalingService.evaluate(
                        playerData,
                        EssenceStats.MAGIC_CAST_SPEED
                );


        AscendanceTierDefinition tier =
                playerData.getTier();


        WeaponChassisConfig.TierRange range =
                EssenceConfigManager
                        .get()
                        .weaponChassisConfig()
                        .rangeFor(
                                tier
                        );


        return new WeaponChassisResult(
                tier,

                meleeDamage.progression(),
                meleeAttackSpeed.progression(),

                rangedDamage.progression(),
                rangedAttackSpeed.progression(),

                magicDamage.progression(),
                magicCastSpeed.progression(),

                range,

                lerp(
                        range.minMeleeDamage(),
                        range.maxMeleeDamage(),
                        meleeDamage.progression()
                ),

                lerp(
                        range.minMeleeAttackSpeed(),
                        range.maxMeleeAttackSpeed(),
                        meleeAttackSpeed.progression()
                ),

                lerp(
                        range.minRangedDamage(),
                        range.maxRangedDamage(),
                        rangedDamage.progression()
                ),

                lerp(
                        range.minRangedAttackSpeed(),
                        range.maxRangedAttackSpeed(),
                        rangedAttackSpeed.progression()
                ),

                lerp(
                        range.minMagicDamage(),
                        range.maxMagicDamage(),
                        magicDamage.progression()
                ),

                lerp(
                        range.minMagicCastSpeed(),
                        range.maxMagicCastSpeed(),
                        magicCastSpeed.progression()
                )
        );
    }


    private static double lerp(
            double minimum,
            double maximum,
            double development
    ) {

        return minimum
                + (
                maximum
                        - minimum
        )
                * development;
    }
}
