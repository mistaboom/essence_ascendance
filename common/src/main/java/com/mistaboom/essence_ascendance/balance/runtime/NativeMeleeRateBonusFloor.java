package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.*;

/** Native Player.attack partial-charge reference. It makes no assumption about hit frequency or DPS. */
public final class NativeMeleeRateBonusFloor {
    public static final String MARKER = "native_melee_charge_floor";
    // Player.attack samples attackStrengthTicker + .5F. At 40/s even ticker zero is fully charged.
    public static final double MAXIMUM_USEFUL_RATE = 40;
    private NativeMeleeRateBonusFloor() { }

    public record Context(ResourceLocation profile, double baseRate, double applicability) {
        public double rate(double percent) {
            return Attributes.ATTACK_SPEED.value().sanitizeValue(baseRate * (1 + percent * applicability / 100));
        }
        public double maximumPercent() {
            return Math.max(0, (Math.min(MAXIMUM_USEFUL_RATE, Attributes.ATTACK_SPEED.value().sanitizeValue(Double.MAX_VALUE))
                    / baseRate - 1) * 100 / applicability);
        }
        public double outcome(double percent) { return damageFactor(rate(percent), 0); }
    }

    /** Exact float order in Player.getCurrentItemAttackStrengthDelay/getAttackStrengthScale and attack. */
    public static float damageFactor(double rate, int elapsedTicks) {
        float delay = (float)(1.0 / Attributes.ATTACK_SPEED.value().sanitizeValue(rate) * 20.0);
        float readiness = Math.clamp((elapsedTicks + .5F) / delay, 0F, 1F);
        return .2F + readiness * readiness * .8F;
    }

    public static List<Context> contexts(RuntimeBalanceDefinition runtime, AscendanceTierDefinition tier) {
        EquipmentProfiles.init();
        var result = new ArrayList<Context>();
        for (var profile : EquipmentProfileRegistry.values()) {
            double applicability = profile.statStrength(EquipmentActivationType.HELD, EssenceStats.MELEE_ATTACK_SPEED);
            if (profile.baselineMultiplier(EquipmentBaselineProperty.MELEE_DAMAGE) <= 0 || applicability <= 0) continue;
            double rate = EquipmentBaselineService.resolvedValue(runtime.config().equipmentBaselineConfig().baselineFor(tier).meleeAttackSpeed(),
                    profile, EquipmentBaselineProperty.MELEE_ATTACK_SPEED);
            if (rate > 0) result.add(new Context(profile.id(), rate, applicability));
        }
        return result.stream().sorted(Comparator.comparing(Context::profile)).toList();
    }

    private static String key(AscendanceTierDefinition tier, String part) { return "native_melee_" + part + "_" + tier.id().getPath(); }

    static void record(RuntimeBalanceDefinition runtime, Map<String, Double> inputs, List<String> evidence) {
        inputs.put(MARKER, 1.0);
        for (var tier : AscendanceTierRegistry.powerTiers()) {
            var contexts = contexts(runtime, tier);
            if (contexts.isEmpty()) throw new IllegalArgumentException("No supported native melee rate context at " + tier.id());
            inputs.put(key(tier, "count"), (double)contexts.size());
            for (int index = 0; index < contexts.size(); index++) {
                var context = contexts.get(index);
                inputs.put(key(tier, "rate") + "_" + index, context.baseRate());
                inputs.put(key(tier, "applicability") + "_" + index, context.applicability());
            }
            evidence.add("Native melee-rate contexts at " + tier.id() + ": " + contexts);
        }
        evidence.add("Melee attack speed retains authored percentage floors and native partial-charge damage improvement on every "
                + "supported same-tier profile, without assuming an attack frequency. Player.attack samples ticker+.5; at 40 actions/s "
                + "even ticker zero is fully charged. This gameplay ceiling is stricter than the attribute range. Neutral skill speed "
                + "and no external modifiers are the named reference; actual Bonus activation follows the player's tier independently of gear tier.");
    }

}
