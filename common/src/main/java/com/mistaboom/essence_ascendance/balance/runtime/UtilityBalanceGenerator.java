package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis;
import com.mistaboom.essence_ascendance.config.UtilityBalanceSettings;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceSemantics;
import net.minecraft.world.food.FoodConstants;

/** Generates Utility gameplay values from the shared semantic/headroom model. */
final class UtilityBalanceGenerator {
    private UtilityBalanceGenerator() { }

    static UtilityBalanceSettings generate(BalanceSettings settings) {
        var threat = SkillBalanceSemantics.require(SkillIds.THREAT_SENSE);
        var ledger = SkillBalanceSemantics.require(SkillIds.HUNTERS_LEDGER);
        var waylight = SkillBalanceSemantics.require(SkillIds.WAYLIGHT);
        var restful = SkillBalanceSemantics.require(SkillIds.RESTFUL_MENDING);
        var village = SkillBalanceSemantics.require(SkillIds.VILLAGE_PATRON);
        var bonded = SkillBalanceSemantics.require(SkillIds.BONDED_COMPANION);
        var relay = SkillBalanceSemantics.require(SkillIds.POTION_RELAY);
        var sanctuary = SkillBalanceSemantics.require(SkillIds.SANCTUARY);
        var industrious = SkillBalanceSemantics.require(SkillIds.INDUSTRIOUS_PRESENCE);
        var containment = SkillBalanceSemantics.require(SkillIds.CONTAINMENT_FIELD);
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

        double villageDiscount = odds(power(settings, SkillIds.VILLAGE_PATRON, CapabilityAxis.CONVERSION));
        double villageRestockRate = odds(power(settings, SkillIds.VILLAGE_PATRON, CapabilityAxis.THROUGHPUT));
        int villageRestockTicks = ticks(window / Math.max(Math.ulp(1.0), villageRestockRate));

        double bondedStats = odds(power(settings, SkillIds.BONDED_COMPANION, CapabilityAxis.SUSTAINED_DAMAGE));
        double bondedTeleport = odds(power(settings, SkillIds.BONDED_COMPANION, CapabilityAxis.TELEPORTATION));
        double bondedCatchup = bonded.rangeBlocks() / Math.sqrt(Math.max(Math.ulp(1.0), bondedTeleport));

        double alchemicalAmplification = odds(power(settings, SkillIds.ALCHEMICAL_AMPLIFICATION, CapabilityAxis.RESOURCE_CONSUMPTION));
        int amplificationDiminishingWindow = ticks(settings.generation().bossEncounterSeconds());

        double relayDuration = odds(power(settings, SkillIds.POTION_RELAY, CapabilityAxis.CONVENIENCE));
        int relayTargets = relay.contributions().stream().mapToInt(SkillBalanceSemantics.Contribution::targets).max().orElse(1);

        double sanctuaryConvenience = power(settings, SkillIds.SANCTUARY, CapabilityAxis.CONVENIENCE);
        int sanctuaryDisengageTicks = ticks(window / Math.max(Math.ulp(1.0), 1.0 + Math.max(0, sanctuaryConvenience)));

        double industriousThroughput = power(settings, SkillIds.INDUSTRIOUS_PRESENCE, CapabilityAxis.THROUGHPUT);
        double industriousMultiplier = 1.0 + Math.max(0, industriousThroughput);

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
                        Math.clamp(performanceBonus, 0, 1)),
                new UtilityBalanceSettings.VillagePatron(
                        Math.clamp(villageDiscount, 0, 1),
                        Math.clamp(village.areaRadiusBlocks(), 0, 128),
                        villageRestockTicks),
                new UtilityBalanceSettings.BondedCompanion(
                        Math.clamp(bondedStats, 0, 1),
                        Math.clamp(bondedCatchup, bonded.rangeBlocks(), 128)),
                new UtilityBalanceSettings.AlchemicalAmplification(
                        Math.clamp(alchemicalAmplification, 0, 1),
                        amplificationDiminishingWindow),
                new UtilityBalanceSettings.PotionRelay(
                        Math.clamp(relay.areaRadiusBlocks(), 0, 128),
                        Math.clamp(relayDuration, 0, 1),
                        relayTargets),
                new UtilityBalanceSettings.Sanctuary(
                        Math.clamp(sanctuary.areaRadiusBlocks(), 0, 128),
                        sanctuaryDisengageTicks),
                new UtilityBalanceSettings.IndustriousPresence(
                        Math.clamp(industrious.areaRadiusBlocks(), 0, 128),
                        Math.clamp(industriousMultiplier, 1, 128)),
                new UtilityBalanceSettings.ContainmentField(
                        Math.clamp(containment.areaRadiusBlocks(), 0, 128)));
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
