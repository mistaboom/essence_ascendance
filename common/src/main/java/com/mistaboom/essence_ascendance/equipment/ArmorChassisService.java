package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.progression.CategoryDevelopment;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.stat.StatCategory;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public final class ArmorChassisService {

    private ArmorChassisService() {
    }


    /*
     * ============================================================
     * POTENTIAL CHASSIS VALUE
     * ============================================================
     *
     * This calculates what the full Ascendance armor chassis would
     * provide if ARMOR_SET were active.
     *
     * It deliberately does NOT check equipment and does NOT apply
     * Minecraft attributes.
     *
     * Equipment activation comes later.
     */

    public static ArmorChassisResult evaluatePotential(
            ServerPlayer player
    ) {

        Objects.requireNonNull(
                player,
                "Player cannot be null"
        );


        PlayerEssenceData playerData =
                EssenceSavedData
                        .get(player.server)
                        .getPlayerData(
                                player.getUUID()
                        );


        return evaluatePotential(
                playerData
        );
    }


    public static ArmorChassisResult evaluatePotential(
            PlayerEssenceData playerData
    ) {

        Objects.requireNonNull(
                playerData,
                "Player Essence data cannot be null"
        );


        CategoryDevelopment defense =
                StatScalingService.evaluateCategory(
                        playerData,
                        StatCategory.DEFENSE
                );


        CategoryDevelopment vitality =
                StatScalingService.evaluateCategory(
                        playerData,
                        StatCategory.VITALITY
                );


        ArmorChassisConfig config =
                EssenceConfigManager
                        .get()
                        .armorChassisConfig();


        AscendanceTierDefinition tier =
                playerData.getTier();


        ArmorChassisConfig.TierRange range =
                config.rangeFor(
                        tier
                );


        double armorProgress =
                weightedProgress(
                        defense.development(),
                        vitality.development(),
                        config.armorDefenseWeight(),
                        config.armorVitalityWeight()
                );


        double toughnessProgress =
                weightedProgress(
                        defense.development(),
                        vitality.development(),
                        config.toughnessDefenseWeight(),
                        config.toughnessVitalityWeight()
                );


        double targetArmor =
                lerp(
                        range.minArmor(),
                        range.maxArmor(),
                        armorProgress
                );


        double targetToughness =
                lerp(
                        range.minToughness(),
                        range.maxToughness(),
                        toughnessProgress
                );


        return new ArmorChassisResult(
                tier,
                defense,
                vitality,
                range,
                armorProgress,
                toughnessProgress,
                targetArmor,
                targetToughness
        );
    }


    private static double weightedProgress(
            double defenseDevelopment,
            double vitalityDevelopment,
            double defenseWeight,
            double vitalityWeight
    ) {

        double totalWeight =
                defenseWeight
                        + vitalityWeight;


        if (totalWeight <= 0.0) {

            throw new IllegalStateException(
                    "Armor chassis development weights cannot total zero"
            );
        }


        double value =
                (
                        defenseDevelopment
                                * defenseWeight
                                + vitalityDevelopment
                                * vitalityWeight
                )
                        / totalWeight;


        return clamp01(
                value
        );
    }


    private static double lerp(
            double minimum,
            double maximum,
            double progress
    ) {

        return minimum
                + (
                maximum
                        - minimum
        )
                * progress;
    }


    private static double clamp01(
            double value
    ) {

        return Math.max(
                0.0,
                Math.min(
                        1.0,
                        value
                )
        );
    }
}