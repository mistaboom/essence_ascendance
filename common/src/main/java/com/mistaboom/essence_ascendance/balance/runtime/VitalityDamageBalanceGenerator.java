package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.config.VitalityDamageBalanceSettings;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceSemantics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodConstants;

/** Conditional reservoir/conversion budgets, derived by the existing pack profile generator.
 * Delay is time, not permanent damage reduction. Percentage reduction costs maximum-health capacity. */
final class VitalityDamageBalanceGenerator {
    private VitalityDamageBalanceGenerator() { }
    static VitalityDamageBalanceSettings generate(BalanceSettings settings) {
        double wardHeadroom = SkillGenerationBudget.headroom(settings, SkillIds.HUNGER_WARD);
        double ceilingHeadroom = SkillGenerationBudget.headroom(settings, SkillIds.DAMAGE_CEILING);
        double adrenalineHeadroom = SkillGenerationBudget.headroom(settings, SkillIds.ADRENALINE);
        double health = RuntimeReferencePolicy.playerHealth();
        double foodReservoir = FoodConstants.MAX_FOOD * 2.0; // full food + full saturation, native units
        double wardBudget = wardHeadroom * weight(SkillIds.HUNGER_WARD, CapabilityAxis.EFFECTIVE_HEALTH);
        double wardShare = Math.min(Math.nextDown(1.0), wardBudget / (1 + wardBudget));
        // Native food/health units set the exchange rate. Power is calibrated via the redirected share,
        // not by reducing each hunger point to a tiny sliver of health protection.
        double healthPerFood = Math.clamp(health / FoodConstants.MAX_FOOD * (1 + wardBudget), .000001, 1024);
        var ceiling = SkillBalanceSemantics.require(SkillIds.DAMAGE_CEILING);
        double ceilingWeight = weight(SkillIds.DAMAGE_CEILING, CapabilityAxis.EFFECTIVE_HEALTH);
        double takenFraction = Math.clamp(1 / (1 + ceilingHeadroom * ceilingWeight), .01, 1);
        var metabolic = metabolicConversion(settings, health / foodReservoir);
        // Requested mirroring ratio is a semantic budget. The complete healing scenarios calibrate it,
        // independently of food type, not a percentage of arbitrary outstanding debt.
        double queuePerHealing = Math.clamp(weight(SkillIds.PAIN_PURGE, CapabilityAxis.SUSTAINED_SURVIVAL), 0, 1);
        var adrenaline = SkillBalanceSemantics.require(SkillIds.ADRENALINE);
        double movement = Math.clamp(adrenalineHeadroom * weight(SkillIds.ADRENALINE, CapabilityAxis.GROUND_SPEED), 0, 10);
        double attack = Math.clamp(adrenalineHeadroom * weight(SkillIds.ADRENALINE, CapabilityAxis.ATTACK_RATE), 0, 10);
        return new VitalityDamageBalanceSettings(
                new VitalityDamageBalanceSettings.HungerWard(healthPerFood, wardShare),
                new VitalityDamageBalanceSettings.StaggeredPain(ticks(SkillBalanceSemantics.require(SkillIds.STAGGERED_PAIN).durationSeconds())),
                new VitalityDamageBalanceSettings.DamageCeiling(takenFraction,
                        ticks(ceiling.durationSeconds())),
                metabolic,
                new VitalityDamageBalanceSettings.PainPurge(queuePerHealing),
                // The requested strict quarter-health trigger is emitted in the generated profile.
                // Gameplay, tooltips and exact overrides consume that same field; ranks/calibration
                // tune the rewards, not this trigger's meaning.
                new VitalityDamageBalanceSettings.Adrenaline(.25, ticks(adrenaline.durationSeconds()), movement, attack,
                        Math.clamp(adrenalineHeadroom * adrenaline.setupRisk(), 0, 1)));
    }
    private static VitalityDamageBalanceSettings.MetabolicConversion metabolicConversion(
            BalanceSettings settings, double healthPerFoodUnit) {
        // Both benefits grow with this skill's allocation. Inverting a budget made lower-power
        // profiles restore more food, then conflict with the useful healing floor at publication.
        double headroom = SkillGenerationBudget.headroom(settings, SkillIds.METABOLIC_CONVERSION);
        double food = Math.clamp(headroom * weight(SkillIds.METABOLIC_CONVERSION, CapabilityAxis.CONVERSION)
                / healthPerFoodUnit, 0, 1024);
        double healing = Math.clamp(headroom * weight(SkillIds.METABOLIC_CONVERSION, CapabilityAxis.HEALING)
                * healthPerFoodUnit, 0, 1024);
        // Reserve the pair's native safety envelope before calibration. Floors still publish last;
        // their overage never re-enters the allocation or reduces another skill/parameter.
        var floors = new com.google.gson.Gson().toJsonTree(java.util.Map.of("vitality", java.util.Map.of(
                "damage", java.util.Map.of("metabolicConversion",
                        new VitalityDamageBalanceSettings.MetabolicConversion(0, 0))))).getAsJsonObject();
        com.mistaboom.essence_ascendance.skill.ProgressionRequirements.skill(SkillIds.METABOLIC_CONVERSION)
                .outcomes().forEach(outcome -> outcome.grantFirst(floors));
        var pair = floors.getAsJsonObject("vitality").getAsJsonObject("damage").getAsJsonObject("metabolicConversion");
        double foodFloor = pair.get("foodPointsPerOverflowHealth").getAsDouble();
        double healingFloor = pair.get("healthPerNutrition").getAsDouble();
        if (foodFloor * healingFloor >= 1)
            throw new IllegalArgumentException("Metabolic Conversion meaningful floors must permit a lossy resource round trip");
        double limit = Math.nextDown(1.0);
        double scale = Math.min(1, Math.min(Math.sqrt(limit / (food * healing)),
                Math.min(limit / (food * healingFloor), limit / (healing * foodFloor))));
        // Multiplication can round an otherwise strict upper bound to one.
        while (Math.max(foodFloor, food * scale) * Math.max(healingFloor, healing * scale) >= 1)
            scale = Math.nextDown(scale);
        return new VitalityDamageBalanceSettings.MetabolicConversion(food * scale, healing * scale);
    }
    private static int ticks(double seconds) { return Math.clamp((int)Math.ceil(seconds * 20), 1, 72_000); }
    private static double weight(ResourceLocation id, CapabilityAxis axis) {
        return SkillBalanceSemantics.require(id).weights().getOrDefault(axis, 0.0);
    }
}
