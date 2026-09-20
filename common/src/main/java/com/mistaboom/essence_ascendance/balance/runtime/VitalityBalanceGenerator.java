package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.config.VitalityBalanceSettings;
import com.mistaboom.essence_ascendance.config.VitalityDeathDefianceBalanceSettings;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceSemantics;
import net.minecraft.world.food.FoodConstants;

/** Resolves new sustain parameters from existing semantic weights, native resource units and pack attack pressure. */
final class VitalityBalanceGenerator {
    private VitalityBalanceGenerator() { }
    static VitalityBalanceSettings generate(PackEvidence evidence, BalanceSettings settings) {
        var seed = VitalityBalanceSettings.defaults();
        double window = settings.generation().survivalWindowSeconds();
        double headroom = BuildPowerTargets.rankOneMultiplier(settings, ProgressionBand.ENTRY,
                BuildComposition.Participation.SKILL_FOCUSED) - 1;
        double recoveryWeight = weight(SkillIds.RISING_RECOVERY, CapabilityAxis.REGENERATION);
        double healingWeight = weight(SkillIds.LIFE_STEAL, CapabilityAxis.HEALING);
        double budget = RuntimeReferencePolicy.playerHealth() * headroom / window;
        // Saturated native regeneration can restore one health per ten ticks.
        // Allocate by the actual missing-health curve and semantic availability.
        // This keeps a smooth fractional timer effect from being rounded away at
        // ordinary damage levels while still letting the generated composition
        // guard reduce the result when a real build ceiling requires it.
        double naturalPeak = 20.0 / FoodConstants.HEALTH_TICK_COUNT_SATURATED;
        double recoveryCurveExponent = 1.0 + Math.max(0.0, 1.0 - recoveryWeight);
        double recoveryCurveMean = 1.0 / (recoveryCurveExponent + 1.0);
        double recoveryAvailability = SkillBalanceSemantics.require(SkillIds.RISING_RECOVERY).expectedAvailability();
        double recovery = budget * recoveryWeight / (naturalPeak * recoveryCurveMean * recoveryAvailability);
        double attackPressure = RuntimeReferencePolicy.required(evidence, ProgressionBand.ENTRY, CapabilityAxis.SUSTAINED_DAMAGE);
        double healingAvailability = SkillBalanceSemantics.require(SkillIds.LIFE_STEAL).expectedAvailability();
        // Life Steal is an accepted-damage fraction. Its budget is based on
        // expected repeated-hit availability, rather than spending the entire
        // survival allowance on a permanently maintained maximum chain.
        double maximumSteal = budget * healingWeight / (attackPressure * healingAvailability);
        int hits = seed.lifeSteal().maxChainHits();
        double chainShape = seed.lifeSteal().baseHealingFraction()
                + (hits - 1) * seed.lifeSteal().perHitHealingFraction();
        double stealScale = maximumSteal / chainShape;
        double convenience = weight(SkillIds.FEAST_REFLEX, CapabilityAxis.RECOVERY)
                + weight(SkillIds.FEAST_REFLEX, CapabilityAxis.CONVENIENCE);
        double duration = Math.clamp(1 / (1 + FoodConstants.EXHAUSTION_DROP * convenience * Math.sqrt(settings.overallPower())), .05, 1);
        double resources = weight(SkillIds.INNER_SUSTENANCE, CapabilityAxis.RESOURCE_CONSUMPTION);
        int recoveryTicks = Math.clamp((int) Math.ceil(FoodConstants.HEALTH_TICK_COUNT / (resources * Math.sqrt(settings.overallPower()))), 20, 72_000);
        int combatTicks = Math.clamp((int) Math.ceil(window * 20), 1, 72_000);
        return new VitalityBalanceSettings(new VitalityBalanceSettings.RisingRecovery(recovery, recoveryCurveExponent),
                new VitalityBalanceSettings.LifeSteal(seed.lifeSteal().baseHealingFraction() * stealScale,
                        seed.lifeSteal().perHitHealingFraction() * stealScale, hits, seed.lifeSteal().chainTimeoutTicks()),
                new VitalityBalanceSettings.FeastReflex(duration), new VitalityBalanceSettings.InnerSustenance(
                        combatTicks, recoveryTicks, seed.innerSustenance().hungerPerRecovery(), seed.innerSustenance().saturationPerRecovery()),
                VitalityDamageBalanceGenerator.generate(settings), VitalityWardBalanceGenerator.generate(settings),
                deathDefiance(settings));
    }

    private static VitalityDeathDefianceBalanceSettings deathDefiance(BalanceSettings settings) {
        var second = SkillBalanceSemantics.require(SkillIds.SECOND_WIND);
        var spirit = SkillBalanceSemantics.require(SkillIds.SPIRIT_WALK);
        double window = settings.generation().survivalWindowSeconds();
        double secondHeadroom = SkillGenerationBudget.headroom(settings, SkillIds.SECOND_WIND);
        double spiritHeadroom = SkillGenerationBudget.headroom(settings, SkillIds.SPIRIT_WALK);

        // Active windows are derived from the shared survival horizon and semantic availability rather than
        // a second hand-tuned duration table. Magnitudes consume the same tier headroom used by every skill family.
        double secondWindow = window * Math.sqrt(Math.max(Math.ulp(1.0), second.expectedAvailability()))
                * (1 + secondHeadroom * second.weights().getOrDefault(CapabilityAxis.REGENERATION, 0.0));
        double spiritWindow = window * Math.sqrt(Math.max(Math.ulp(1.0), spirit.expectedAvailability()))
                * (1 + spiritHeadroom * spirit.weights().getOrDefault(CapabilityAxis.CONVENIENCE, 0.0));
        double recovery = Math.clamp(secondHeadroom
                * second.weights().getOrDefault(CapabilityAxis.REGENERATION, 0.0), 0, 16);
        double strength = Math.clamp(secondHeadroom
                * second.weights().getOrDefault(CapabilityAxis.BURST_DAMAGE, 0.0), 0, 16);
        double survivalPower = spiritHeadroom
                * spirit.weights().getOrDefault(CapabilityAxis.BURST_SURVIVAL, 0.0);
        double reform = survivalPower <= 0 ? 0 : survivalPower / (1 + survivalPower);
        return new VitalityDeathDefianceBalanceSettings(
                new VitalityDeathDefianceBalanceSettings.SecondWind(
                        semanticCooldown(second, window), ticks(secondWindow), recovery, strength),
                new VitalityDeathDefianceBalanceSettings.SpiritWalk(
                        semanticCooldown(spirit, window), ticks(spiritWindow), Math.min(Math.nextDown(1.0), reform)));
    }

    private static int semanticCooldown(SkillBalanceSemantics.Descriptor descriptor, double fallbackSeconds) {
        return ticks(descriptor.cooldownSeconds() > 0 ? descriptor.cooldownSeconds() : fallbackSeconds);
    }

    private static int ticks(double seconds) {
        return Math.clamp((int)Math.ceil(seconds * 20), 1, 72_000);
    }
    private static double weight(net.minecraft.resources.ResourceLocation id, CapabilityAxis axis) {
        return SkillBalanceSemantics.require(id).weights().getOrDefault(axis, 0.0);
    }
}
