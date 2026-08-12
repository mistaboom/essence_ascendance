package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/*
 * Resolves tier + equipment archetype into a base physical equipment value.
 * Player investment is deliberately absent from this service.
 */
public final class EquipmentBaselineService {

    private EquipmentBaselineService() {
    }

    public static EquipmentBaselineResult evaluate(
            ServerPlayer player,
            ResourceLocation profileId
    ) {
        Objects.requireNonNull(player, "Player cannot be null");

        PlayerEssenceData playerData =
                EssenceSavedData
                        .get(player.server)
                        .getPlayerData(player.getUUID());

        return evaluate(playerData, profileId);
    }

    public static EquipmentBaselineResult evaluate(
            PlayerEssenceData playerData,
            ResourceLocation profileId
    ) {
        Objects.requireNonNull(playerData, "Player Essence data cannot be null");
        Objects.requireNonNull(profileId, "Equipment profile ID cannot be null");

        EquipmentProfileDefinition profile =
                EquipmentProfileRegistry
                        .get(profileId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Unknown equipment profile: " + profileId
                                )
                        );

        AscendanceTierDefinition tier = playerData.getTier();
        EquipmentBaselineConfig.TierBaseline tierBaseline =
                EssenceConfigManager
                        .get()
                        .equipmentBaselineConfig()
                        .baselineFor(tier);

        return new EquipmentBaselineResult(
                tier,
                profile,
                tierBaseline,
                multiply(tierBaseline.fullSetArmor(), profile, EquipmentBaselineProperty.ARMOR),
                multiply(tierBaseline.fullSetToughness(), profile, EquipmentBaselineProperty.TOUGHNESS),
                multiply(tierBaseline.meleeDamage(), profile, EquipmentBaselineProperty.MELEE_DAMAGE),
                multiply(tierBaseline.meleeAttackSpeed(), profile, EquipmentBaselineProperty.MELEE_ATTACK_SPEED),
                multiply(tierBaseline.rangedDamage(), profile, EquipmentBaselineProperty.RANGED_DAMAGE),
                multiply(tierBaseline.rangedAttackSpeed(), profile, EquipmentBaselineProperty.RANGED_ATTACK_SPEED),
                multiply(tierBaseline.magicDamage(), profile, EquipmentBaselineProperty.MAGIC_DAMAGE),
                multiply(tierBaseline.magicCastSpeed(), profile, EquipmentBaselineProperty.MAGIC_CAST_SPEED),
                multiply(tierBaseline.miningSpeed(), profile, EquipmentBaselineProperty.MINING_SPEED),
                tierBaseline.harvestLevel(),
                (int) Math.max(
                        1,
                        Math.round(
                                multiply(
                                        tierBaseline.durability(),
                                        profile,
                                        EquipmentBaselineProperty.DURABILITY
                                )
                        )
                )
        );
    }

    private static double multiply(
            double base,
            EquipmentProfileDefinition profile,
            EquipmentBaselineProperty property
    ) {
        return base * profile.baselineMultiplier(property);
    }
}
