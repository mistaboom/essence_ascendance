package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.equipment.*;
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
                + "Actual Bonus activation follows the player's tier independently of gear tier; equipment upgrades do not fund the increment. "
                + "A one-tick tier cannot supply another cooldown state; placement uses an earlier useful span when possible. "
                + "A first-state timing floor may exceed the nominal budget without changing prices or sibling allocations.");
    }

}
