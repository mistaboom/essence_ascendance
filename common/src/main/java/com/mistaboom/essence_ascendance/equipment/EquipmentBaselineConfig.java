package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/*
 * Tier-owned physical capability of Ascendance equipment.
 *
 * Player stat investment does not live here. Equipment archetype/profile
 * multipliers are applied later by EquipmentBaselineService.
 */
public final class EquipmentBaselineConfig {

    private final Map<ResourceLocation, TierBaseline> tierBaselines;

    public EquipmentBaselineConfig(
            Map<ResourceLocation, TierBaseline> tierBaselines
    ) {
        Objects.requireNonNull(tierBaselines, "Equipment tier baselines cannot be null");
        this.tierBaselines = Collections.unmodifiableMap(
                new LinkedHashMap<>(tierBaselines)
        );
    }

    public Map<ResourceLocation, TierBaseline> tierBaselines() {
        return tierBaselines;
    }

    public TierBaseline baselineFor(AscendanceTierDefinition tier) {
        Objects.requireNonNull(tier, "Ascendance tier cannot be null");

        TierBaseline baseline = tierBaselines.get(tier.id());
        if (baseline == null) {
            throw new IllegalStateException(
                    "No equipment baseline exists for tier " + tier.id()
            );
        }
        return baseline;
    }

    public record TierBaseline(
            double fullSetArmor,
            double fullSetToughness,
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
        public TierBaseline {
            validateNonNegative(fullSetArmor, "full-set armor");
            validateNonNegative(fullSetToughness, "full-set toughness");
            validateNonNegative(meleeDamage, "melee damage");
            validatePositive(meleeAttackSpeed, "melee attack speed");
            validateNonNegative(rangedDamage, "ranged damage");
            validatePositive(rangedAttackSpeed, "ranged attack speed");
            validateNonNegative(magicDamage, "magic damage");
            validatePositive(magicCastSpeed, "magic cast speed");
            validatePositive(miningSpeed, "mining speed");

            if (harvestLevel < 0) {
                throw new IllegalArgumentException("Harvest level cannot be negative");
            }
            if (durability <= 0) {
                throw new IllegalArgumentException("Durability must be greater than zero");
            }
        }

        public double value(EquipmentBaselineProperty property) {
            return switch (property) {
                case ARMOR -> fullSetArmor;
                case TOUGHNESS -> fullSetToughness;
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

        private static void validateNonNegative(double value, String name) {
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException(name + " must be finite and non-negative");
            }
        }

        private static void validatePositive(double value, String name) {
            if (!Double.isFinite(value) || value <= 0.0) {
                throw new IllegalArgumentException(name + " must be finite and greater than zero");
            }
        }
    }
}
