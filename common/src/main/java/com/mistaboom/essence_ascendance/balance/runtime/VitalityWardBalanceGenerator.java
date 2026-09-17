package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis;
import com.mistaboom.essence_ascendance.config.VitalityWardBalanceSettings;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceSemantics;

/** Ward capacity is a finite survival budget, not permanently available damage reduction or an invented kill rate. */
final class VitalityWardBalanceGenerator {
    private VitalityWardBalanceGenerator() { }
    static VitalityWardBalanceSettings generate(BalanceSettings settings) {
        var soul = SkillBalanceSemantics.require(SkillIds.SOUL_WARD);
        var deep = SkillBalanceSemantics.require(SkillIds.DEEP_WARD);
        var shatter = SkillBalanceSemantics.require(SkillIds.SHATTERING_WARD);
        double window = settings.generation().survivalWindowSeconds();
        double capacity = SkillGenerationBudget.headroom(settings, SkillIds.SOUL_WARD)
                * soul.weights().getOrDefault(CapabilityAxis.EFFECTIVE_HEALTH, 0.0);
        double extra = SkillGenerationBudget.headroom(settings, SkillIds.DEEP_WARD)
                * deep.weights().getOrDefault(CapabilityAxis.EFFECTIVE_HEALTH, 0.0);
        double healing = SkillGenerationBudget.headroom(settings, SkillIds.SHATTERING_WARD)
                * shatter.weights().getOrDefault(CapabilityAxis.REGENERATION, 0.0) / window;
        double control = shatter.weights().getOrDefault(CapabilityAxis.CROWD_CONTROL, 0.0);
        // Area-target count is part of the shared semantic descriptor and its capability accounting.
        int targets = shatter.contributions().stream().mapToInt(value -> value.targets()).max().orElse(1);
        return new VitalityWardBalanceSettings(
                new VitalityWardBalanceSettings.SoulWard(Math.clamp(capacity * soul.expectedAvailability(), 0, 1),
                        Math.clamp(capacity, 0, 1024), ticks(window / soul.expectedAvailability())),
                new VitalityWardBalanceSettings.DeepWard(Math.clamp(extra, 0, 1024), ticks(window),
                        ticks(window / deep.expectedAvailability())),
                new VitalityWardBalanceSettings.ShatteringWard(shatter.areaRadiusBlocks(), targets,
                        Math.clamp(control * Math.sqrt(settings.overallPower()), 0, 1024),
                        Math.clamp(healing, 0, 1024), ticks(window * shatter.expectedAvailability())));
    }
    private static int ticks(double seconds) { return Math.clamp((int) Math.ceil(seconds * 20), 1, 72_000); }
}
