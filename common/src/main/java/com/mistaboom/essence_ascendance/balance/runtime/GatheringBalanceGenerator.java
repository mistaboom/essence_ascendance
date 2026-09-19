package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis;
import com.mistaboom.essence_ascendance.config.GatheringBalanceSettings;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceSemantics;

/** Generates Gathering mining values from the same semantic/headroom model as the other skill families. */
final class GatheringBalanceGenerator {
    private GatheringBalanceGenerator() { }

    static GatheringBalanceSettings generate(BalanceSettings settings) {
        var instinct = SkillBalanceSemantics.require(SkillIds.TOOL_INSTINCT);
        var momentum = SkillBalanceSemantics.require(SkillIds.MINING_MOMENTUM);
        var boon = SkillBalanceSemantics.require(SkillIds.NATURES_BOON);
        double window = settings.generation().survivalWindowSeconds();

        double instinctSpeed = power(settings, SkillIds.TOOL_INSTINCT, CapabilityAxis.MINING_SPEED);
        double momentumSpeed = power(settings, SkillIds.MINING_MOMENTUM, CapabilityAxis.MINING_SPEED);
        double boonYield = power(settings, SkillIds.NATURES_BOON, CapabilityAxis.DROP_YIELD);

        int buildBreaks = Math.clamp((int) Math.ceil(window * momentum.expectedAvailability()), 1, 1_024);
        int timeout = ticks(window * Math.max(Math.ulp(1.0), 1.0 - momentum.expectedAvailability()));
        double crossMaterial = Math.clamp(momentum.expectedAvailability(), 0, 1);
        double boonChance = boonYield <= 0 ? 0 : Math.clamp((boonYield / (1.0 + boonYield)) * boon.expectedAvailability(), 0, 1);

        return new GatheringBalanceSettings(
                new GatheringBalanceSettings.ToolInstinct(Math.clamp(instinctSpeed, 0, 16)),
                new GatheringBalanceSettings.MiningMomentum(Math.clamp(momentumSpeed, 0, 16),
                        buildBreaks, timeout, crossMaterial),
                new GatheringBalanceSettings.NaturesBoon(boonChance));
    }

    private static double power(BalanceSettings settings, net.minecraft.resources.ResourceLocation skill,
                                CapabilityAxis axis) {
        return SkillGenerationBudget.headroom(settings, skill) * SkillBalanceSemantics.require(skill).weights().get(axis);
    }

    private static int ticks(double seconds) {
        return Math.clamp((int) Math.ceil(seconds * 20.0), 1, 72_000);
    }
}
