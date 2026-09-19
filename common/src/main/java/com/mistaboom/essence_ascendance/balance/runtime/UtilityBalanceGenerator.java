package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis;
import com.mistaboom.essence_ascendance.config.UtilityBalanceSettings;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceSemantics;
import net.minecraft.world.food.FoodConstants;

/** Generates Utility sensing and maintenance values from the shared semantic/headroom model. */
final class UtilityBalanceGenerator {
    private UtilityBalanceGenerator() { }

    static UtilityBalanceSettings generate(BalanceSettings settings) {
        var threat = SkillBalanceSemantics.require(SkillIds.THREAT_SENSE);
        var ledger = SkillBalanceSemantics.require(SkillIds.HUNTERS_LEDGER);
        var waylight = SkillBalanceSemantics.require(SkillIds.WAYLIGHT);
        var restful = SkillBalanceSemantics.require(SkillIds.RESTFUL_MENDING);
        double window = settings.generation().survivalWindowSeconds();
        double ledgerInformation = power(settings, SkillIds.HUNTERS_LEDGER, CapabilityAxis.INFORMATION);

        int memoryTicks = ticks(window * ledger.expectedAvailability() * (1.0 + Math.max(0, ledgerInformation)));

        double restfulRepair = power(settings, SkillIds.RESTFUL_MENDING, CapabilityAxis.REPAIR);
        int restfulIdleTicks = ticks(window * Math.max(Math.ulp(1.0), 1.0 - restful.expectedAvailability()));
        double restfulFractionPerSecond = odds(restfulRepair) / window;

        double metabolicRepair = power(settings, SkillIds.METABOLIC_MENDING, CapabilityAxis.REPAIR);
        // A full hunger bar worth of intrinsic nutrition/saturation pressure maps to the generated repair headroom.
        double metabolicFractionPerFoodPoint = odds(metabolicRepair) / FoodConstants.MAX_FOOD;

        double masterworkDurability = power(settings, SkillIds.MASTERWORK_TEMPERING, CapabilityAxis.DURABILITY);
        double masterworkConversion = power(settings, SkillIds.MASTERWORK_TEMPERING, CapabilityAxis.CONVERSION);
        double masterworkThroughput = power(settings, SkillIds.MASTERWORK_TEMPERING, CapabilityAxis.THROUGHPUT);
        double maximumOverdurability = odds(masterworkDurability);
        double reinforcementPerUnit = Math.min(maximumOverdurability, odds(masterworkConversion));
        double performanceBonus = odds(masterworkThroughput);

        return new UtilityBalanceSettings(
                new UtilityBalanceSettings.ThreatSense(Math.clamp(threat.rangeBlocks(), 0, 128)),
                new UtilityBalanceSettings.HuntersLedger(memoryTicks),
                new UtilityBalanceSettings.Waylight(Math.clamp(waylight.areaRadiusBlocks(), 0, 128)),
                new UtilityBalanceSettings.RestfulMending(restfulIdleTicks,
                        Math.clamp(restfulFractionPerSecond, 0, 1)),
                new UtilityBalanceSettings.MetabolicMending(Math.clamp(metabolicFractionPerFoodPoint, 0, 1)),
                new UtilityBalanceSettings.MasterworkTempering(
                        Math.clamp(reinforcementPerUnit, 0, 1),
                        Math.clamp(maximumOverdurability, 0, 1),
                        Math.clamp(performanceBonus, 0, 1)));
    }

    private static double power(BalanceSettings settings, net.minecraft.resources.ResourceLocation skill,
                                CapabilityAxis axis) {
        return SkillGenerationBudget.headroom(settings, skill) * SkillBalanceSemantics.require(skill).weights().get(axis);
    }

    private static double odds(double power) {
        return power <= 0 ? 0 : power / (1.0 + power);
    }

    private static int ticks(double seconds) {
        return Math.clamp((int) Math.ceil(seconds * 20.0), 1, 72_000);
    }
}
