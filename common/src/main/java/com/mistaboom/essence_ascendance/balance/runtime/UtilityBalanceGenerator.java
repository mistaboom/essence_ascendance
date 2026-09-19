package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis;
import com.mistaboom.essence_ascendance.config.UtilityBalanceSettings;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceSemantics;

/** Generates Utility sensing values from the shared semantic/headroom model. */
final class UtilityBalanceGenerator {
    private UtilityBalanceGenerator() { }

    static UtilityBalanceSettings generate(BalanceSettings settings) {
        var threat = SkillBalanceSemantics.require(SkillIds.THREAT_SENSE);
        var ledger = SkillBalanceSemantics.require(SkillIds.HUNTERS_LEDGER);
        var waylight = SkillBalanceSemantics.require(SkillIds.WAYLIGHT);
        double window = settings.generation().survivalWindowSeconds();
        double ledgerInformation = power(settings, SkillIds.HUNTERS_LEDGER, CapabilityAxis.INFORMATION);

        // Memory is generated from the shared encounter window and the skill's actual
        // information headroom. It is deliberately not a gameplay-side constant.
        int memoryTicks = ticks(window * ledger.expectedAvailability() * (1.0 + Math.max(0, ledgerInformation)));

        return new UtilityBalanceSettings(
                new UtilityBalanceSettings.ThreatSense(Math.clamp(threat.rangeBlocks(), 0, 128)),
                new UtilityBalanceSettings.HuntersLedger(memoryTicks),
                new UtilityBalanceSettings.Waylight(Math.clamp(waylight.areaRadiusBlocks(), 0, 128)));
    }

    private static double power(BalanceSettings settings, net.minecraft.resources.ResourceLocation skill,
                                CapabilityAxis axis) {
        return SkillGenerationBudget.headroom(settings, skill) * SkillBalanceSemantics.require(skill).weights().get(axis);
    }

    private static int ticks(double seconds) {
        return Math.clamp((int) Math.ceil(seconds * 20.0), 1, 72_000);
    }
}
