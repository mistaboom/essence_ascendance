package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.Objects;

public record EquipmentBaselineResult(
        AscendanceTierDefinition tier,
        EquipmentProfileDefinition profile,
        EquipmentBaselineConfig.TierBaseline tierBaseline,
        double armor,
        double toughness,
        double meleeDamage,
        double meleeAttackSpeed,
        double rangedDamage,
        double rangedAttackSpeed,
        double magicDamage,
        double magicCastSpeed,
        double miningSpeed,
        int harvestLevel,
        int durability
) {
    public EquipmentBaselineResult {
        Objects.requireNonNull(tier, "Tier cannot be null");
        Objects.requireNonNull(profile, "Equipment profile cannot be null");
        Objects.requireNonNull(tierBaseline, "Tier baseline cannot be null");
    }

    public double armorForSlot(EquipmentSlot slot) {
        return EquipmentBaselineService.quantizationEnabled()?ArmorStatWeights.physicalPointsForSlot(armor,slot):armor*ArmorStatWeights.weightFor(slot);
    }

    public double toughnessForSlot(EquipmentSlot slot) {
        return EquipmentBaselineService.quantizationEnabled()?ArmorStatWeights.physicalPointsForSlot(toughness,slot):toughness*ArmorStatWeights.weightFor(slot);
    }

    public int rangedDrawTicks() {
        return rateToTicks(rangedAttackSpeed);
    }

    public int magicCastTicks() {
        return rateToTicks(magicCastSpeed);
    }

    public double value(EquipmentBaselineProperty property) {
        return switch (property) {
            case ARMOR -> armor;
            case TOUGHNESS -> toughness;
            case MELEE_DAMAGE -> meleeDamage;
            case MELEE_ATTACK_SPEED -> meleeAttackSpeed;
            case RANGED_DAMAGE -> rangedDamage;
            case RANGED_ATTACK_SPEED -> rangedAttackSpeed;
            case MAGIC_DAMAGE -> magicDamage;
            case MAGIC_CAST_SPEED -> magicCastSpeed;
            case MINING_SPEED -> miningSpeed;
            case DURABILITY -> durability;
        };
    }

    private static int rateToTicks(double actionsPerSecond) {
        if (actionsPerSecond <= 0.0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, (int) Math.round(20.0 / actionsPerSecond));
    }
}
