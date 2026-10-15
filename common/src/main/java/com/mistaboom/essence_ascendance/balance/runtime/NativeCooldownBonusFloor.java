package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.progression.MeaningfulProgression;
import com.mistaboom.essence_ascendance.skill.ProgressionRequirements;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.tier.*;

import java.util.*;

/** Whole draw/cast ticks in an unchanged active-tier weapon context; no assumed attack uptime. */
public final class NativeCooldownBonusFloor {
    public static final String MARKER = "native_cooldown_tick_floor";
    private NativeCooldownBonusFloor() { }

    public static boolean supported(StatDefinition stat) {
        return stat.id().getPath().equals("ranged_attack_speed") || stat.id().getPath().equals("magic_cast_speed");
    }
    public static double rate(RuntimeBalanceDefinition runtime, StatDefinition stat, AscendanceTierDefinition tier) {
        if (!supported(stat)) throw new IllegalArgumentException("No native whole-cooldown context for " + stat.id());
        EquipmentProfiles.init();
        boolean ranged = stat.id().getPath().equals("ranged_attack_speed");
        var profile = ranged ? EquipmentProfiles.RANGED_WEAPON : EquipmentProfiles.MAGIC_CASTER;
        var property = ranged ? EquipmentBaselineProperty.RANGED_ATTACK_SPEED : EquipmentBaselineProperty.MAGIC_CAST_SPEED;
        return EquipmentBaselineService.resolvedValue(runtime.config().equipmentBaselineConfig().baselineFor(tier).value(property),
                profile, property);
    }
    public static int ticks(double baseRate, double percentage) {
        return EquipmentWeaponService.rateToTicks(baseRate * (1 + percentage / 100));
    }
    static String key(AscendanceTierDefinition tier) { return "native_cooldown_rate_" + tier.id().getPath(); }

    static void record(RuntimeBalanceDefinition runtime, StatDefinition stat, Map<String, Double> inputs, List<String> evidence) {
        if (!supported(stat)) return;
        inputs.put(MARKER, 1.0);
        for (var tier : AscendanceTierRegistry.powerTiers()) inputs.put(key(tier), rate(runtime, stat, tier));
        evidence.add("Completed endpoints must save at least one native whole draw/cast tick as well as the authored percentage "
                + "floor, using the same active-tier weapon and EquipmentWeaponService.rateToTicks. Skill speed is neutral. "
                + "Equipment below the player's tier caps Bonus activation; equipment upgrades do not fund the increment. "
                + "A one-tick tier cannot supply another cooldown state; placement uses an earlier useful span when possible. "
                + "A first-state timing floor may exceed the nominal budget without changing prices or sibling allocations.");
    }

    record Plan(int start, List<Double> values) { }
    interface NativeState {
        boolean improves(AscendanceTierDefinition tier, double previous, double value);
        double ceiling(AscendanceTierDefinition tier);
    }

    static Plan select(BonusTrackDefinition nominal, ProgressionRequirements.Bonus required,
                       List<Double> candidates, List<AscendanceTierDefinition> tiers, int earliest, int preferred) {
        return select(nominal, required, candidates, tiers, earliest, preferred, new NativeState() {
            public boolean improves(AscendanceTierDefinition tier, double previous, double value) {
                double rate = nominal.inputs().get(key(tier));
                return ticks(rate, value) < ticks(rate, previous);
            }
            public double ceiling(AscendanceTierDefinition tier) {
                return nominal.inputs().getOrDefault("native_maximum_effect", 1_000_000.0);
            }
        });
    }

    /** Shared placement/state selection for native equipment mechanics with tier-dependent headroom. */
    static Plan select(BonusTrackDefinition nominal, ProgressionRequirements.Bonus required,
                       List<Double> candidates, List<AscendanceTierDefinition> tiers, int earliest, int preferred, NativeState nativeState) {
        if (nominal.inputs().containsKey("configured_maximum")) {
            int endOrder = nominal.inputs().get("configured_max_tier_order").intValue();
            var boundedTiers = tiers.stream().filter(tier -> tier.order() <= endOrder).toList();
            var boundedState = new NativeState() {
                public boolean improves(AscendanceTierDefinition tier, double previous, double value) {
                    return nativeState.improves(tier, previous, value);
                }
                public double ceiling(AscendanceTierDefinition tier) {
                    return Math.min(nominal.inputs().get("configured_maximum"), nativeState.ceiling(tier));
                }
            };
            var values = configuredAt(nominal, required, boundedTiers, preferred, boundedState);
            if (values.isEmpty()) throw new IllegalArgumentException("Configured first tier has no native headroom: " + nominal.statId());
            return new Plan(preferred, values);
        }
        // The generic final-tier preference is soft; native headroom is a hard requirement. Search outward
        // without deleting a supported track simply because stronger late equipment is already one tick.
        for (int distance = 0; distance < tiers.size(); distance++) {
            for (int start : distance == 0 ? new int[]{preferred} : new int[]{preferred - distance, preferred + distance}) {
                if (start < earliest || start >= tiers.size()) continue;
                var values = at(nominal, required, candidates, tiers, start, nativeState);
                if (!values.isEmpty()) return new Plan(start, values);
            }
        }
        throw new IllegalArgumentException("Cannot publish " + nominal.statId()
                + ": no supported powered tier can satisfy the authored floor and native improvement together; actual native contexts="
                + nominal.inputs().entrySet().stream().filter(entry -> entry.getKey().startsWith("native_cooldown_rate_")
                        || entry.getKey().startsWith("native_melee_rate_")).toList());
    }

    /** Find the least native effect needed at each configured tier, reserving the exact target for completion. */
    private static List<Double> configuredAt(BonusTrackDefinition nominal, ProgressionRequirements.Bonus required,
            List<AscendanceTierDefinition> tiers, int start, NativeState nativeState) {
        double quantum = RuntimeValueQuantization.statStep(nominal.unit());
        double maximum = nominal.inputs().get("configured_maximum");
        var values = new ArrayList<Double>();
        double previous = 0;
        for (int index = start; index < tiers.size(); index++) {
            var tier = tiers.get(index);
            double floor = values.isEmpty() ? required.firstStateFloor() : previous + required.tierImprovementFloor();
            double ceiling = nativeState.ceiling(tier);
            if (ceiling + 1e-9 < floor || !nativeState.improves(tier, previous, ceiling))
                throw new IllegalArgumentException("Configured native span is exhausted at " + tier.id() + " for " + nominal.statId());
            long low = (long)Math.ceil((floor - 1e-10) / quantum), high = (long)Math.floor((ceiling + 1e-10) / quantum);
            if (low > high || !nativeState.improves(tier, previous, high * quantum))
                throw new IllegalArgumentException("Configured native span cannot reach a quantized state at " + tier.id());
            while (low < high) {
                long middle = low + (high - low) / 2;
                if (nativeState.improves(tier, previous, middle * quantum)) high = middle;
                else low = middle + 1;
            }
            double value = index == tiers.size() - 1 ? maximum : low * quantum;
            if (value + 1e-9 < floor || value > ceiling + 1e-9 || !nativeState.improves(tier, previous, value)
                    || Math.abs(RuntimeValueQuantization.down(value, quantum) - value) > 1e-7)
                throw new IllegalArgumentException("Configured bonus_at_max cannot complete at " + tier.id() + " for " + nominal.statId());
            values.add(value); previous = value;
        }
        return List.copyOf(values);
    }

    private static List<Double> at(BonusTrackDefinition nominal, ProgressionRequirements.Bonus required,
                                   List<Double> candidates, List<AscendanceTierDefinition> tiers, int start, NativeState nativeState) {
        var tier = tiers.get(start);
        double quantum = RuntimeValueQuantization.statStep(nominal.unit());
        double ceiling = nativeState.ceiling(tier);
        if (ceiling < required.firstStateFloor() || !nativeState.improves(tier, 0, ceiling)) return List.of();
        double first = MeaningfulProgression.first(candidates.getFirst(), required.firstStateFloor(), quantum, ceiling);
        if (!nativeState.improves(tier, 0, first)) {
            // Find the first usable native boundary on the saved percentage grid. This is floor overage,
            // not a new allocation or an assumption that the nominal budget can weaken the minimum.
            long low = Math.round(first / quantum), high = (long)Math.floor(ceiling / quantum);
            if (!nativeState.improves(tier, 0, high * quantum)) return List.of();
            while (low < high) {
                long middle = low + (high - low) / 2;
                if (nativeState.improves(tier, 0, middle * quantum)) high = middle;
                else low = middle + 1;
            }
            first = low * quantum;
        }
        var values = new ArrayList<Double>();
        values.add(first);
        for (int index = 1; index < candidates.size() && start + values.size() < tiers.size(); index++) {
            double previous = values.getLast();
            tier = tiers.get(start + values.size());
            ceiling = nativeState.ceiling(tier);
            if (!nativeState.improves(tier, previous, ceiling)) break;
            double value = RuntimeValueQuantization.down(Math.min(candidates.get(index), ceiling), quantum);
            if (MeaningfulProgression.improves(previous, value, required.tierImprovementFloor())
                    && nativeState.improves(tier, previous, value)) values.add(value);
        }
        return List.copyOf(values);
    }
}
