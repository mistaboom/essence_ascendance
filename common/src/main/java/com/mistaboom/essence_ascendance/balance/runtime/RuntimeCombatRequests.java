package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineConfig;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import java.util.List;

/** Native damage requests before composition limits, distinct from dimensionless percentages. */
final class RuntimeCombatRequests {
    private RuntimeCombatRequests() { }
    // Shared policy: request at least half a matching apex weapon hit. Small catalogs
    // retain the authored request; the existing measured composition limits still apply.
    private static final double BONUS_HIT_SHARE = .5;
    // Flat skill defaults describe a ten-damage reference hit. This is a policy unit,
    // not a discovered item, mod ID, progression claim or multiplier for percentages.
    private static final double SKILL_REFERENCE_HIT = 10;

    static double bonus(StatDefinition stat, double authored, EquipmentBaselineConfig.TierBaseline apex) {
        if (BonusSemantics.require(stat).response() != BonusSemantics.Response.FLAT_DAMAGE) return authored;
        double hit = switch (stat.id().getPath()) {
            case "melee_damage" -> apex.meleeDamage();
            case "ranged_damage" -> apex.rangedDamage();
            case "magic_damage" -> apex.magicDamage();
            default -> throw new IllegalArgumentException("Missing flat-damage equipment family: " + stat.id());
        };
        return Math.max(authored, hit * BONUS_HIT_SHARE);
    }

    static void scaleFlatSkillDamage(JsonObject effects, EquipmentBaselineConfig.TierBaseline apex) {
        // These procs have one native amount shared by all three eligible weapon
        // families. A strong sword alone cannot inflate a proc also used by a weak bow.
        double hit = Math.min(apex.meleeDamage(), Math.min(apex.rangedDamage(), apex.magicDamage()));
        double factor = Math.max(1, hit / SKILL_REFERENCE_HIT);
        for (String path : List.of("combustion.damage", "shatter.shardDamage", "staticCharge.lightningDamage")) {
            String[] parts = path.split("\\.");
            var parent = effects.getAsJsonObject(parts[0]);
            parent.addProperty(parts[1], Math.min(1024, parent.get(parts[1]).getAsDouble() * factor));
        }
    }
}
