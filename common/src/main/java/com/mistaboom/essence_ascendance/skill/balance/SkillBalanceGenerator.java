package com.mistaboom.essence_ascendance.skill.balance;

import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Generated economy prices and bounded rank projections use one registered curve per skill. */
public final class SkillBalanceGenerator {
    private SkillBalanceGenerator() { }

    /**
     * Reserves headroom for repeat ranks using reachable implemented loadouts.
     * Non-numeric access (for example shield bypass) remains a policy decision;
     * lowering a damage multiplier cannot remove that capability.
     */
    public static double projectedPowerScale(Map<CapabilityAxis, Double> skillAxisBudgets,
                                             double requestedScale) {
        if (!Double.isFinite(requestedScale) || requestedScale <= 0)
            throw new IllegalArgumentException("Invalid requested skill scale");
        Map<ResourceLocation, Integer> stressRanks = new TreeMap<>();
        SkillRegistry.values().forEach(skill -> stressRanks.put(skill.id(), skill.rankPolicy().projectionRanks()));
        Map<ResourceLocation, Integer> firstRanks = new TreeMap<>();
        SkillRegistry.values().forEach(skill -> firstRanks.put(skill.id(), 1));
        double attenuation = 1.0;
        for (var tier : com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry.powerTiers()) {
            for (var targetRanks : java.util.List.of(firstRanks, stressRanks)) {
                var projection = SkillLoadoutProjection.project(SkillRegistry.values(), tier.id(),
                        targetRanks, (id, rank) -> SkillRegistry.require(id).rankPolicy().curve().power(rank) * requestedScale,
                        skillAxisBudgets, false);
                Map<CapabilityAxis, Double> capabilities = new java.util.EnumMap<>(CapabilityAxis.class);
                projection.scenarios().forEach(scenario -> scenario.capabilityPressure()
                        .forEach((axis, amount) -> capabilities.merge(axis, amount, Math::max)));
                for (var entry : projection.conservativeUpperBounds().entrySet()) {
                    Double budget = skillAxisBudgets.get(entry.getKey());
                    if (budget == null) continue;
                    double fixedCapability = capabilities.getOrDefault(entry.getKey(), 0.0);
                    double scalablePressure = Math.max(0, entry.getValue() - fixedCapability);
                    double scalableBudget = budget - fixedCapability;
                    if (scalablePressure > 0 && scalableBudget > 0)
                        attenuation = Math.min(attenuation, scalableBudget / scalablePressure);
                }
            }
        }
        return Math.max(0.000001, requestedScale * attenuation);
    }
    public static Map<String, SkillBalanceRuntime.ResolvedSkill> generate(
            Map<ResourceLocation, Long> tierCosts, double costPressure) {
        return generate(tierCosts, Map.of(), costPressure);
    }
    public static Map<String, SkillBalanceRuntime.ResolvedSkill> generate(
            Map<ResourceLocation, Long> tierCosts, Map<ResourceLocation, Double> essenceCostFactors,
            double costPressure) {
        if (!Double.isFinite(costPressure) || costPressure <= 0)
            throw new IllegalArgumentException("Skill generation scales must be positive");
        Map<String, SkillBalanceRuntime.ResolvedSkill> result = new TreeMap<>();
        for (var skill : SkillRegistry.values()) {
            Long tierCost = tierCosts.get(skill.requiredTierId());
            if (tierCost == null) throw new IllegalArgumentException("Missing skill tier budget: " + skill.requiredTierId());
            double categoryFactor = essenceCostFactors.getOrDefault(skill.essenceId(), 1.0);
            if (!Double.isFinite(categoryFactor) || categoryFactor <= 0)
                throw new IllegalArgumentException("Invalid category skill cost factor");
            double rawCost = Math.ceil(skill.costBand().cost(tierCost) * costPressure * categoryFactor);
            if (!Double.isFinite(rawCost) || rawCost >= Long.MAX_VALUE) throw new ArithmeticException("Skill price overflow");
            long firstCost = Math.max(1L, (long) rawCost);
            var ranks = new ArrayList<SkillBalanceRuntime.ResolvedRank>();
            for (int rank = 1; rank <= skill.rankPolicy().projectionRanks(); rank++) {
                ranks.add(new SkillBalanceRuntime.ResolvedRank(rank,
                        skill.rankPolicy().curve().cost(firstCost, rank),
                        skill.rankPolicy().curve().power(rank)));
            }
            result.put(skill.id().toString(), new SkillBalanceRuntime.ResolvedSkill(skill.maximumRank(), ranks));
        }
        SkillBalanceRuntime.validate(result);
        return Collections.unmodifiableMap(result);
    }
}
