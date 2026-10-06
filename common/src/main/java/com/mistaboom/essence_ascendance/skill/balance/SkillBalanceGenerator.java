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
        var intrinsic = new com.google.gson.Gson().toJsonTree(
                com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings.defaults()).getAsJsonObject();
        for (var skill : SkillRegistry.values()) {
            Long tierCost = tierCosts.get(skill.requiredTierId());
            if (tierCost == null) throw new IllegalArgumentException("Missing skill tier budget: " + skill.requiredTierId());
            double categoryFactor = essenceCostFactors.getOrDefault(skill.essenceId(), 1.0);
            if (!Double.isFinite(categoryFactor) || categoryFactor <= 0)
                throw new IllegalArgumentException("Invalid category skill cost factor");
            var mechanical = SkillProgressionPolicy.evaluate(skill, SkillBalanceSemantics.require(skill.id()), intrinsic);
            long firstCost = SkillProgressionPolicy.price(tierCost, mechanical.utility(), categoryFactor, costPressure, 1, 0);
            var ranks = new ArrayList<SkillBalanceRuntime.ResolvedRank>();
            for (int rank = 1; rank <= skill.rankPolicy().projectionRanks(); rank++) {
                ranks.add(new SkillBalanceRuntime.ResolvedRank(rank,
                        skill.rankPolicy().curve().cost(firstCost, rank),
                        skill.rankPolicy().curve().power(rank)));
            }
            result.put(skill.id().toString(), new SkillBalanceRuntime.ResolvedSkill(1, ranks, skill.requiredTierId()));
        }
        SkillBalanceRuntime.validate(result);
        return Collections.unmodifiableMap(result);
    }

    /** Final publication happens after nominal allocation/calibration. No accepted floor overage is fed back. */
    public static com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition publishMeaningful(
            com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition nominal) {
        return publishMeaningful(nominal, Map.of());
    }
    public static com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition publishMeaningful(
            com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition nominal,
            Map<ResourceLocation, com.mistaboom.essence_ascendance.skill.ProgressionRequirements.Skill> requirementsBySkill) {
        var json = nominal.toJson();
        var base = json.getAsJsonObject("effects").deepCopy();
        var firstEffects = base.deepCopy();
        var gson = new com.google.gson.GsonBuilder().registerTypeAdapter(ResourceLocation.class,
                new com.mistaboom.essence_ascendance.balance.config.ResourceLocationJsonAdapter()).create();
        var curves = new TreeMap<String, SkillBalanceRuntime.ResolvedSkill>();
        for (var skill : SkillRegistry.values()) {
            var requirements = requirementsBySkill.getOrDefault(skill.id(), skill.progressionRequirements());
            var source = nominal.skillCurves().get(skill.id().toString());
            var owned = new java.util.TreeSet<String>();
            var probe = gson.toJsonTree(SkillRankEffectScaling.apply(nominal.config().skillEffects(),
                    Map.of(skill.id(), 2), (id, rank) -> 2)).getAsJsonObject();
            changedPaths(base, probe, "", owned);
            requirements.outcomes().forEach(outcome -> owned.add(outcome.path()));
            var ranks = new ArrayList<SkillBalanceRuntime.ResolvedRank>();
            com.google.gson.JsonObject previous = null;
            for (var candidate : source.ranks()) {
                if (!ranks.isEmpty() && (!SkillRankEffectScaling.supports(skill.id())
                        || skill.id().equals(com.mistaboom.essence_ascendance.skill.SkillIds.UNTETHERED_FLIGHT))) break;
                var effects = gson.toJsonTree(SkillRankEffectScaling.apply(nominal.config().skillEffects(),
                        Map.of(skill.id(), candidate.rank()), (id, rank) -> candidate.powerMultiplier())).getAsJsonObject();
                requirements.outcomes().forEach(outcome -> outcome.quantize(effects));
                if (ranks.isEmpty()) requirements.outcomes().forEach(outcome -> outcome.grantFirst(effects));
                else {
                    // Fixed first-state capabilities persist; scalable allocations are never bumped to manufacture ranks.
                    requirements.outcomes().stream().filter(o -> o.later() == 0).forEach(o -> o.grantFirst(effects));
                    boolean meaningful = false, all = true, regressed = false;
                    for (var outcome : requirements.outcomes()) {
                        double before = outcome.measure(previous), after = outcome.measure(effects);
                        regressed |= after + 1e-9 < before;
                        if (outcome.later() > 0) {
                            boolean improves = com.mistaboom.essence_ascendance.progression.MeaningfulProgression.improves(before, after, outcome.later());
                            meaningful |= improves; all &= improves;
                        }
                    }
                    if (regressed || !meaningful || (!requirements.alternativeImprovements() && !all)) continue;
                }
                // Constructors enforce real cross-parameter/native constraints, including conversion no-feedback.
                try { gson.fromJson(effects, com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings.class).validate(); }
                catch (IllegalArgumentException invalid) { if (ranks.isEmpty()) throw invalid; else continue; }
                var parameters = new TreeMap<String, Double>();
                for (String path : owned) parameters.put(path, com.mistaboom.essence_ascendance.skill.ProgressionRequirements.read(effects, path));
                if (ranks.isEmpty()) parameters.forEach((path, value) ->
                        com.mistaboom.essence_ascendance.skill.ProgressionRequirements.write(firstEffects, path, value));
                ranks.add(new SkillBalanceRuntime.ResolvedRank(ranks.size() + 1, candidate.cost(), candidate.powerMultiplier(), parameters));
                previous = effects;
                if (skill.rankPolicy().maximumRank() > 0 && ranks.size() >= skill.rankPolicy().maximumRank()) break;
            }
            curves.put(skill.id().toString(), new SkillBalanceRuntime.ResolvedSkill(ranks.size(), ranks, source.requiredTierId()));
        }
        json.add("effects", firstEffects);
        json.add("skillCurves", gson.toJsonTree(curves));
        json.getAsJsonObject("composition").addProperty("meaningful_progression", 1);
        return com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition.fromJson(json);
    }
    public static void validatePublished(com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings base,
            Map<String, SkillBalanceRuntime.ResolvedSkill> curves) {
        var gson = new com.google.gson.Gson();
        for (var skill : SkillRegistry.values()) {
            var curve = curves.get(skill.id().toString());
            if (curve.maximumRank() != curve.ranks().size()) throw new IllegalArgumentException("Unpurchasable filler ranks: " + skill.id());
            if ((!SkillRankEffectScaling.supports(skill.id()) || skill.id().equals(com.mistaboom.essence_ascendance.skill.SkillIds.UNTETHERED_FLIGHT))
                    && curve.maximumRank() != 1) throw new IllegalArgumentException("Binary skill has extra ranks: " + skill.id());
            var requirements = skill.progressionRequirements();
            var baseline = gson.toJsonTree(base).getAsJsonObject();
            var allowed = new java.util.TreeSet<String>();
            for (var reference : java.util.List.of(base, com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings.defaults())) {
                var probe = gson.toJsonTree(SkillRankEffectScaling.apply(reference,
                        Map.of(skill.id(), 2), (id, rank) -> 2)).getAsJsonObject();
                changedPaths(gson.toJsonTree(reference).getAsJsonObject(), probe, "", allowed);
            }
            requirements.outcomes().forEach(outcome -> allowed.add(outcome.path()));
            com.google.gson.JsonObject previous = null;
            for (var rank : curve.ranks()) {
                if (!allowed.containsAll(rank.parameters().keySet()))
                    throw new IllegalArgumentException("Foreign native rank parameter: " + skill.id());
                if (rank.rank() == 1) for (var parameter : rank.parameters().entrySet()) {
                    if (Math.abs(com.mistaboom.essence_ascendance.skill.ProgressionRequirements.read(baseline, parameter.getKey())
                            - parameter.getValue()) > 1e-9)
                        throw new IllegalArgumentException("Rank-one effect mirror disagrees: " + skill.id());
                }
                if (!rank.parameters().keySet().equals(curve.ranks().getFirst().parameters().keySet()))
                    throw new IllegalArgumentException("Rank parameter ownership changed: " + skill.id());
                var effects = gson.toJsonTree(base).getAsJsonObject();
                rank.parameters().forEach((path, value) -> com.mistaboom.essence_ascendance.skill.ProgressionRequirements.write(effects, path, value));
                boolean any = false, all = true;
                for (var outcome : requirements.outcomes()) {
                    if (!rank.parameters().containsKey(outcome.path())) throw new IllegalArgumentException("Missing native rank outcome: " + skill.id());
                    double value = com.mistaboom.essence_ascendance.skill.ProgressionRequirements.read(effects, outcome.path());
                    if (Math.abs(value - Math.rint(value / outcome.quantum()) * outcome.quantum()) > Math.max(1e-9, 4 * Math.ulp(value)))
                        throw new IllegalArgumentException("Native rank parameter is off its gameplay grid: " + skill.id());
                    if (value < outcome.minimum() - 1e-9 || value > outcome.maximum() + 1e-9 || outcome.measure(effects) + 1e-9 < outcome.first())
                        throw new IllegalArgumentException("Rank floor/ceiling violated: " + skill.id() + "/" + outcome.path());
                    if (previous != null) {
                        if (outcome.measure(effects) + 1e-9 < outcome.measure(previous)) throw new IllegalArgumentException("Rank outcome regressed: " + skill.id());
                        if (outcome.later() > 0) {
                            boolean improves = com.mistaboom.essence_ascendance.progression.MeaningfulProgression.improves(outcome.measure(previous), outcome.measure(effects), outcome.later());
                            any |= improves; all &= improves;
                        }
                    }
                }
                if (previous != null && (!any || (!requirements.alternativeImprovements() && !all)))
                    throw new IllegalArgumentException("Sub-floor rank improvement: " + skill.id());
                gson.fromJson(effects, com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings.class).validate();
                previous = effects;
            }
        }
    }
    private static void changedPaths(com.google.gson.JsonObject base, com.google.gson.JsonObject candidate,
                                     String prefix, java.util.Set<String> paths) {
        for (var entry : base.entrySet()) {
            String path = prefix + entry.getKey();
            var next = candidate.get(entry.getKey());
            if (entry.getValue().isJsonObject()) changedPaths(entry.getValue().getAsJsonObject(), next.getAsJsonObject(), path + "/", paths);
            else if (!entry.getValue().equals(next)) paths.add(path);
        }
    }
}
