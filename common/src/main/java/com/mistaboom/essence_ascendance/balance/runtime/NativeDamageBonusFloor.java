package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.*;

/** Generation/audit reference for damage in the actual supported equipment contexts.
 * Additive damage points are the Bonus unit; equipment upgrades never fund an increment. */
public final class NativeDamageBonusFloor {
    public static final double MINIMUM_DAMAGE_INCREMENT = 1;
    private NativeDamageBonusFloor() { }

    public record Context(ResourceLocation tier, ResourceLocation profile, double baseDamage,
                          double applicability, boolean melee) {
        public double damage(double value) {
            double result = baseDamage + value * applicability;
            return melee ? Attributes.ATTACK_DAMAGE.value().sanitizeValue(result) : result;
        }
        public double valueForOneDamage() { return MINIMUM_DAMAGE_INCREMENT / applicability; }
        public double maximumValue() {
            return melee ? Math.max(0, (Attributes.ATTACK_DAMAGE.value().sanitizeValue(Double.MAX_VALUE) - baseDamage) / applicability) : 1_000_000;
        }
    }

    public static boolean supported(StatDefinition stat) {
        return switch (stat.id().getPath()) {
            case "melee_damage", "ranged_damage", "magic_damage" -> true;
            default -> false;
        };
    }

    public static List<Context> contexts(RuntimeBalanceDefinition runtime, StatDefinition stat) {
        if (!supported(stat)) return List.of();
        EquipmentProfiles.init();
        var property = switch (stat.id().getPath()) {
            case "melee_damage" -> EquipmentBaselineProperty.MELEE_DAMAGE;
            case "ranged_damage" -> EquipmentBaselineProperty.RANGED_DAMAGE;
            default -> EquipmentBaselineProperty.MAGIC_DAMAGE;
        };
        var contexts = new ArrayList<Context>();
        for (var tier : AscendanceTierRegistry.powerTiers()) {
            double base = runtime.config().equipmentBaselineConfig().baselineFor(tier).value(property);
            for (var profile : EquipmentProfileRegistry.values()) {
                // Only native attack consumers fit this model; action weapons resolve their own hit/cadence phases.
                boolean eligible = switch (property) {
                    case MELEE_DAMAGE -> profile.baselineMultiplier(EquipmentBaselineProperty.MELEE_DAMAGE) > 0
                            && profile.baselineMultiplier(EquipmentBaselineProperty.MELEE_ATTACK_SPEED) > 0
                            && profile.weaponCapabilities().melee() == EquipmentWeaponCapabilities.MeleeExecution.NATIVE;
                    case RANGED_DAMAGE -> profile.id().equals(EquipmentProfiles.RANGED_WEAPON.id());
                    case MAGIC_DAMAGE -> profile.id().equals(EquipmentProfiles.MAGIC_CASTER.id());
                    default -> false;
                };
                double strength = profile.statStrength(EquipmentActivationType.HELD, stat);
                double damage = EquipmentBaselineService.resolvedValue(base, profile, property);
                // Flat bonuses add native damage even when the generated physical baseline is zero.
                if (eligible && strength > 0)
                    contexts.add(new Context(tier.id(), profile.id(), damage, strength, property == EquipmentBaselineProperty.MELEE_DAMAGE));
            }
        }
        if (contexts.isEmpty()) throw new IllegalArgumentException("No supported native equipment damage context for " + stat.id());
        return List.copyOf(contexts);
    }
}
