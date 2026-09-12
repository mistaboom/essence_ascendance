package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
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

    public static EquipmentBaselineResult evaluateForStack(
            PlayerEssenceData playerData,
            ResourceLocation profileId,
            ItemStack stack
    ) {
        Objects.requireNonNull(stack, "Equipment stack cannot be null");
        if (!EquipmentTierData.isAscendanceEquipment(stack)) {
            return evaluate(playerData, profileId);
        }

        if (profileId.equals(EquipmentProfiles.SHIELD.id())) {
            return evaluateForEquipmentTier(playerData, profileId, EquipmentTierData.tier(stack));
        }

        // A Fractured artifact keeps existing as the same tiered ItemStack,
        // but its enhanced physical progression is offline until repaired.
        // Fall back to the mundane Latent baseline rather than deleting the
        // artifact or allowing completed-tier power to remain active.
        if (FracturedEquipmentData.isFractured(stack)) {
            return evaluateWithBaseline(
                    profileId,
                    AscendanceTiers.DORMANT,
                    latentBaseline()
            );
        }

        EquipmentTier itemTier = EquipmentTierData.tier(stack);
        if (itemTier == EquipmentTier.LATENT) {
            return evaluateWithBaseline(
                    profileId,
                    AscendanceTiers.DORMANT,
                    latentBaseline()
            );
        }
        // Native/mundane equipment capability belongs to the artifact itself.
        // Player Ascendance only caps Essence channeling; it must not
        // drag armor, attack damage, mining speed, etc. back down to player tier.
        return evaluateAtTier(playerData, profileId, itemTier.ascendanceTier());
    }


    public static EquipmentBaselineResult evaluateForEquipmentTier(
            PlayerEssenceData playerData,
            ResourceLocation profileId,
            EquipmentTier itemTier
    ) {
        Objects.requireNonNull(itemTier, "Equipment tier cannot be null");
        EquipmentBaselineResult base = itemTier == EquipmentTier.LATENT
                ? evaluateWithBaseline(profileId, AscendanceTiers.DORMANT, latentBaseline())
                : evaluateAtTier(playerData, profileId, itemTier.ascendanceTier());
        if (!profileId.equals(EquipmentProfiles.SHIELD.id())) return base;
        return new EquipmentBaselineResult(base.tier(), base.profile(), base.tierBaseline(),
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, EquipmentShieldService.nativeDurability(itemTier));
    }

    public static EquipmentBaselineResult evaluateAtTier(
            PlayerEssenceData playerData,
            ResourceLocation profileId,
            AscendanceTierDefinition tier
    ) {
        Objects.requireNonNull(playerData, "Player Essence data cannot be null");
        Objects.requireNonNull(tier, "Tier cannot be null");
        return evaluateWithBaseline(
                profileId,
                tier,
                EssenceConfigManager.get().equipmentBaselineConfig().baselineFor(tier)
        );
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
                EssenceConfigManager.get().equipmentBaselineConfig().baselineFor(tier);
        return evaluateWithBaseline(profileId, tier, tierBaseline);
    }

    private static EquipmentBaselineResult evaluateWithBaseline(
            ResourceLocation profileId,
            AscendanceTierDefinition tier,
            EquipmentBaselineConfig.TierBaseline tierBaseline
    ) {
        EquipmentProfileDefinition profile = EquipmentProfileRegistry.get(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown equipment profile: " + profileId));
        if (profileId.equals(EquipmentProfiles.SHIELD.id())) {
            return new EquipmentBaselineResult(tier, profile, tierBaseline,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    EquipmentShieldService.nativeDurability(EquipmentTier.fromAscendanceTier(tier)));
        }
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
                (int) Math.max(1, Math.round(multiply(tierBaseline.durability(), profile, EquipmentBaselineProperty.DURABILITY)))
        );
    }

    private static EquipmentBaselineConfig.TierBaseline latentBaseline() {
        return new EquipmentBaselineConfig.TierBaseline(
                15.0D, 0.0D,
                6.0D, 1.6D,
                6.0D, 1.0D,
                3.0D, 20.0D / 12.0D,
                6.0D,
                2,
                250
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
