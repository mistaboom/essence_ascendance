package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis;
import com.mistaboom.essence_ascendance.config.GatheringBalanceSettings;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceSemantics;

/** Generates Gathering values from the same semantic/headroom model as the other skill families. */
final class GatheringBalanceGenerator {
    private GatheringBalanceGenerator() { }

    static GatheringBalanceSettings generate(BalanceSettings settings) {
        var momentum = SkillBalanceSemantics.require(SkillIds.MINING_MOMENTUM);
        var boon = SkillBalanceSemantics.require(SkillIds.NATURES_BOON);
        var oreSight = SkillBalanceSemantics.require(SkillIds.ORE_SIGHT);
        var treasureSense = SkillBalanceSemantics.require(SkillIds.TREASURE_SENSE);
        var huntersStudy = SkillBalanceSemantics.require(SkillIds.HUNTERS_STUDY);
        var bloom = SkillBalanceSemantics.require(SkillIds.ESSENCE_BLOOM);
        var verdant = SkillBalanceSemantics.require(SkillIds.VERDANT_STRIDE);
        var herdkeeper = SkillBalanceSemantics.require(SkillIds.HERDKEEPER);
        var animalGift = SkillBalanceSemantics.require(SkillIds.ANIMAL_GIFT);
        double window = settings.generation().survivalWindowSeconds();

        double instinctSpeed = power(settings, SkillIds.TOOL_INSTINCT, CapabilityAxis.MINING_SPEED);
        double momentumSpeed = power(settings, SkillIds.MINING_MOMENTUM, CapabilityAxis.MINING_SPEED);
        double boonYield = power(settings, SkillIds.NATURES_BOON, CapabilityAxis.DROP_YIELD);
        double hunterYield = power(settings, SkillIds.HUNTERS_STUDY, CapabilityAxis.DROP_YIELD);
        double bloomConversion = power(settings, SkillIds.ESSENCE_BLOOM, CapabilityAxis.CONVERSION);
        double bloomExperience = power(settings, SkillIds.ESSENCE_BLOOM, CapabilityAxis.EXPERIENCE);
        double verdantYield = power(settings, SkillIds.VERDANT_STRIDE, CapabilityAxis.CROP_YIELD);
        double herdThroughput = power(settings, SkillIds.HERDKEEPER, CapabilityAxis.THROUGHPUT);
        double giftYield = power(settings, SkillIds.ANIMAL_GIFT, CapabilityAxis.DROP_YIELD);
        double giftAutomation = power(settings, SkillIds.ANIMAL_GIFT, CapabilityAxis.AUTOMATION_INTERACTION);
        double fishingThroughput = power(settings, SkillIds.FISHING_INSTINCT, CapabilityAxis.THROUGHPUT);
        double fishingYield = power(settings, SkillIds.FISHING_INSTINCT, CapabilityAxis.DROP_YIELD);

        int buildBreaks = Math.clamp((int) Math.ceil(window * momentum.expectedAvailability()), 1, 1_024);
        int timeout = ticks(window * Math.max(Math.ulp(1.0), 1.0 - momentum.expectedAvailability()));
        double crossMaterial = Math.clamp(momentum.expectedAvailability(), 0, 1);
        double boonChance = odds(boonYield) * boon.expectedAvailability();

        int studyKills = Math.clamp((int) Math.ceil(window * huntersStudy.expectedAvailability()), 1, 1_024);
        double bloomPressure = bloomConversion + bloomExperience;
        double bloomChance = Math.clamp(odds(bloomPressure) * bloom.expectedAvailability(), 0, 1);
        double bonusXpFraction = odds(bloomExperience);

        int verdantPulse = ticks(window * Math.max(Math.ulp(1.0), verdant.expectedAvailability()));
        double verdantChance = Math.clamp(odds(verdantYield) * verdant.expectedAvailability(), 0, 1);
        double breedingRecovery = 1.0 + Math.max(0, herdThroughput);
        double giftPressure = giftYield + giftAutomation;
        int giftPulse = ticks(window * Math.max(Math.ulp(1.0), animalGift.expectedAvailability()));
        double giftChance = Math.clamp(odds(giftPressure) * animalGift.expectedAvailability(), 0, 1);

        return new GatheringBalanceSettings(
                new GatheringBalanceSettings.ToolInstinct(Math.clamp(instinctSpeed, 0, 16)),
                new GatheringBalanceSettings.MiningMomentum(Math.clamp(momentumSpeed, 0, 16),
                        buildBreaks, timeout, crossMaterial),
                new GatheringBalanceSettings.NaturesBoon(Math.clamp(boonChance, 0, 1)),
                new GatheringBalanceSettings.Survey(Math.clamp(oreSight.rangeBlocks(), 0, 128)),
                new GatheringBalanceSettings.Survey(Math.clamp(treasureSense.rangeBlocks(), 0, 128)),
                new GatheringBalanceSettings.HuntersStudy(studyKills, Math.clamp(hunterYield, 0, 255)),
                new GatheringBalanceSettings.EssenceBloom(bloomChance,
                        Math.clamp(bloomConversion, 0, 1_024), Math.clamp(bonusXpFraction, 0, 16)),
                new GatheringBalanceSettings.VerdantStride(Math.clamp(verdant.areaRadiusBlocks(), 0, 128),
                        verdantPulse, verdantChance),
                new GatheringBalanceSettings.Herdkeeper(Math.clamp(herdkeeper.areaRadiusBlocks(), 0, 128),
                        Math.clamp(breedingRecovery, 1, 128)),
                new GatheringBalanceSettings.AnimalGift(Math.clamp(animalGift.areaRadiusBlocks(), 0, 128),
                        giftPulse, giftChance),
                new GatheringBalanceSettings.FishingInstinct(Math.clamp(1.0 + fishingThroughput, 1, 128),
                        Math.clamp(1.0 + fishingThroughput, 1, 128), Math.clamp(fishingYield, 0, 255)));
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
