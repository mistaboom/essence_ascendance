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
        // Preserve the previously generated Metabolic conversion economy independently of Ward's new routing.
        double metabolicUnit = Math.clamp(health * wardBudget / foodReservoir, .000001, 1024);
        var ceiling = SkillBalanceSemantics.require(SkillIds.DAMAGE_CEILING);
        double ceilingWeight = weight(SkillIds.DAMAGE_CEILING, CapabilityAxis.EFFECTIVE_HEALTH);
        double takenFraction = Math.clamp(1 / (1 + ceilingHeadroom * ceilingWeight), .01, 1);
        double conversion = weight(SkillIds.METABOLIC_CONVERSION, CapabilityAxis.CONVERSION);
        double healing = weight(SkillIds.METABOLIC_CONVERSION, CapabilityAxis.HEALING);
        double overflow = Math.clamp(conversion / metabolicUnit, 0, 1024);
        double meal = Math.clamp(metabolicUnit * healing, 0, 1024);
        // Bound against the semantic loss contract, not a gameplay fallback constant.
        if (overflow * meal >= 1) meal = Math.nextDown(1.0) / overflow;
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
                new VitalityDamageBalanceSettings.MetabolicConversion(overflow, meal),
                new VitalityDamageBalanceSettings.PainPurge(queuePerHealing),
                // The requested strict quarter-health trigger is emitted in the generated profile.
                // Gameplay, tooltips and exact overrides consume that same field; ranks/calibration
                // tune the rewards, not this trigger's meaning.
                new VitalityDamageBalanceSettings.Adrenaline(.25, ticks(adrenaline.durationSeconds()), movement, attack,
                        Math.clamp(adrenalineHeadroom * adrenaline.setupRisk(), 0, 1)));
    }
    private static int ticks(double seconds) { return Math.clamp((int)Math.ceil(seconds * 20), 1, 72_000); }
    private static double weight(ResourceLocation id, CapabilityAxis axis) {
        return SkillBalanceSemantics.require(id).weights().getOrDefault(axis, 0.0);
    }
}
