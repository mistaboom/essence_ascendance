package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.Gson;
import com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.balance.*;
import com.mistaboom.essence_ascendance.stat.*;
import com.mistaboom.essence_ascendance.gathering.GatheringSurveyService;
import com.mistaboom.essence_ascendance.tier.*;
import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import java.util.*;

/** Permanent complete-state contracts, including differential tests after full publication. */
public final class MeaningfulProgressionTest {
    private static int checks;
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); EssenceTypes.init(); EssenceStats.init(); AscendanceTiers.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init();
        check(MeaningfulProgression.select(List.of(1.0, 24.0, 25.0), 10, 5, 1, 100).equals(List.of(10.0,24.0)),
                "First floor; +14 preserved; +1 rejected; no subdivision");
        check(MeaningfulProgression.select(List.of(1.0, 24.0, 25.0), 12, 5, 1, 100).equals(List.of(12.0,24.0)),
                "Ignored overage cannot lower or increase later nominal allocations");
        check(MeaningfulProgression.select(List.of(.1, .49, .51), .5, .5, .5, 1).equals(List.of(.5)), "Quantization collapse");
        check(MeaningfulProgression.select(List.of(.01, 500_000.0, 1_000_000.0), 1, 5, 1, 1_000_000)
                        .equals(List.of(1.0, 500_000.0, 1_000_000.0)),
                "Very large legal changes remain intact; a lower-bound floor cannot normalize or subdivide them");
        rejects(() -> MeaningfulProgression.first(1, 5, 1, 4));
        var runtime = RuntimeBalanceDefinition.bootstrap();
        SkillBalanceRuntime.install(runtime.skillCurves());
        SkillBalanceGenerator.validatePublished(runtime.config().skillEffects(), runtime.skillCurves());
        var origin = BlockPos.ZERO;
        var boundary = new BlockPos(20, 0, 0);
        check(!GatheringSurveyService.withinRange(origin, boundary, 19.8),
                "Fractional Gathering survey radius must not round up to an extra block");
        check(GatheringSurveyService.withinRange(origin, boundary, 20.0),
                "Gathering survey includes its exact boundary");
        check(runtime.skillCurves().values().stream().map(SkillBalanceRuntime.ResolvedSkill::maximumRank).distinct().count() > 1,
                "Generated max ranks must reflect mechanic and meaningful allocations");
        for (var skill : SkillRegistry.values()) {
            var curve = runtime.skillCurves().get(skill.id().toString());
            check(skill.maximumRank() == curve.maximumRank() && curve.maximumRank() == curve.ranks().size(), "One rank authority");
            check(curve.ranks().getFirst().cost() > 0, "Generated price");
            for (var rank : curve.ranks()) {
                var actual = new Gson().toJsonTree(SkillRankEffectScaling.applyResolved(runtime.config().skillEffects(),
                        Map.of(skill.id(), rank.rank()), runtime.skillCurves())).getAsJsonObject();
                for (var outcome : skill.progressionRequirements().outcomes())
                    check(outcome.measure(actual) + 1e-9 >= outcome.first(), "Actual gameplay first floor: " + skill.id());
            }
        }
        check(SkillRegistry.require(SkillIds.UNTETHERED_FLIGHT).maximumRank() == 1, "Nonlimiting recharge cannot create a flight rank");
        check(SkillRegistry.require(SkillIds.PURE_STATE).maximumRank() == 1, "Binary immunity has one rank");
        var latent = SkillEvaluationContext.committed(AscendanceTiers.LATENT.id(), Map.of(), Map.of(), Set.of(), Set.of(), Map.of());
        for (var skill : SkillRegistry.values()) check(!SkillStateEvaluator.evaluatePurchaseEligibility(skill, latent).satisfied(),
                "Latent skill purchase forbidden: " + skill.id());
        var maxRanks = new TreeMap<net.minecraft.resources.ResourceLocation,Integer>();
        SkillRegistry.values().forEach(skill -> maxRanks.put(skill.id(), skill.maximumRank()));
        var projection = SkillLoadoutProjection.project(SkillRegistry.values(), AscendanceTiers.TRANSCENDENT.id(),
                maxRanks, (id, rank) -> runtime.skillCurves().get(id.toString()).ranks().get(rank-1).powerMultiplier(), Map.of(), false);
        for (var scenario : projection.scenarios()) {
            var groups = new HashSet<net.minecraft.resources.ResourceLocation>();
            for (var id : scenario.activeRanks().keySet()) {
                var definition = SkillRegistry.require(id);
                definition.choiceGroupId().ifPresent(group -> check(groups.add(group), "Generated maximum ranks preserve exclusion groups"));
                if (definition.replacementTarget() != null) check(!scenario.activeRanks().containsKey(definition.replacementTarget()),
                        "Generated maximum ranks preserve replacement suppression");
            }
        }
        for (var track : runtime.config().balanceProfile().bonusTracks().values()) {
            if (track.applicability() == BonusTrackDefinition.Applicability.UNAVAILABLE) continue;
            var requirements = track.progressionRequirements();
            var values = track.activeValues();
            check(values.getFirst() + 1e-9 >= requirements.firstStateFloor(), "Bonus first floor " + track.statId());
            for (int i = 1; i < values.size(); i++) check(MeaningfulProgression.improves(values.get(i-1), values.get(i), requirements.tierImprovementFloor()), "Bonus later floor");
            check(values.size() == track.activeStateCount() && values.getLast() == track.maximumEffect(), "Generated count and endpoint");
            boolean completed = false;
            for (var point : track.checkpoints()) {
                if (point.purchasable()) {
                    check(!completed, "Contiguous active window");
                    var stat = EssenceStatRegistry.get(track.statId()).orElseThrow();
                    var tier = AscendanceTierRegistry.get(point.tierId()).orElseThrow();
                    double before = StatScalingService.realizedProgressionForInvestment(stat, point.cumulativeCap()-1, tier, runtime.config().balanceProfile());
                    double at = StatScalingService.realizedProgressionForInvestment(stat, point.cumulativeCap(), tier, runtime.config().balanceProfile());
                    check(at == point.effectFraction() && at > before && (point.cumulativeCap() <= 1 || before > 0),
                            "Fractional investment reaches the unchanged meaningful tier target");
                } else if (point.available()) completed = true;
            }
            var snapshot = com.mistaboom.essence_ascendance.network.BonusTrackSnapshot.from(track, runtime.config().balanceProfile());
            check(snapshot.tierPositions().getFirst() == 0 && snapshot.tierPositions().getLast() == 1, "Latent has no purchase band");
            check(track.checkpoint(AscendanceTiers.LATENT.id()).cumulativeCap() == 0
                    && track.checkpoint(AscendanceTiers.LATENT.id()).effectFraction() == 0, "Latent remains onboarding only");
            var stat = EssenceStatRegistry.get(track.statId()).orElseThrow();
            check(!TierInvestmentPolicy.validTarget(stat, AscendanceTiers.LATENT, runtime.config().balanceProfile(), 0, 1), "Latent purchase rejected by server policy");
        }
        differential(runtime);
        bonusDifferential(runtime);
        everyChoice(runtime);
        var damaged = runtime.toJson();
        var selected = runtime.skillCurves().entrySet().stream().filter(e -> e.getValue().maximumRank() > 1).findFirst().orElseThrow();
        var ranks = damaged.getAsJsonObject("skillCurves").getAsJsonObject(selected.getKey()).getAsJsonArray("ranks");
        ranks.get(1).getAsJsonObject().add("parameters", ranks.get(0).getAsJsonObject().get("parameters").deepCopy());
        rejects(() -> RuntimeBalanceDefinition.fromJson(damaged));
        var foreign = runtime.toJson();
        foreign.getAsJsonObject("skillCurves").getAsJsonObject(SkillIds.COMBUSTION.toString()).getAsJsonArray("ranks")
                .forEach(rank -> rank.getAsJsonObject().getAsJsonObject("parameters").addProperty("deathDefiance/cooldownTicks", 100));
        rejects(() -> RuntimeBalanceDefinition.fromJson(foreign));
        var extraState = runtime.toJson();
        var bonus = extraState.getAsJsonObject("balanceProfile").getAsJsonObject("bonusTracks")
                .getAsJsonObject(EssenceStats.MELEE_DAMAGE.id().toString());
        var extraSnaps = new com.google.gson.JsonArray();
        extraSnaps.add(0); extraSnaps.add(.000001);
        bonus.getAsJsonArray("snapPoints").asList().stream().skip(1).forEach(extraSnaps::add);
        bonus.add("snapPoints", extraSnaps);
        rejects(() -> RuntimeBalanceDefinition.fromJson(extraState));
        SkillBalanceRuntime.clear();
        System.out.println("MeaningfulProgressionTest: " + checks + " complete-state, native-floor, authority, quantization and non-compensation checks PASS");
    }
    private static void everyChoice(RuntimeBalanceDefinition runtime) {
        var milestones = MilestoneRegistry.values().stream().map(MilestoneDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        for (boolean maximum : List.of(false, true)) for (boolean funded : List.of(false, true)) {
            var ranks = new TreeMap<net.minecraft.resources.ResourceLocation, Integer>();
            SkillRegistry.values().forEach(skill -> ranks.put(skill.id(), maximum ? skill.maximumRank() : 1));
            var bonuses = new TreeMap<net.minecraft.resources.ResourceLocation, Long>();
            if (funded) EssenceStatRegistry.values().forEach(stat -> bonuses.put(stat.id(),
                    runtime.config().balanceProfile().getInvestmentCap(AscendanceTiers.TRANSCENDENT, stat)));
            for (var group : SkillRegistry.choiceGroups()) for (var selected : group.memberIds()) {
                var plan = SkillStateEvaluator.activationPlan(selected, ranks, Map.of()).orElseThrow();
                var selections = new HashMap<>(plan.selectableAssignments());
                for (boolean enabled : List.of(true, false, true)) {
                    if (enabled) selections.put(group.id(), selected); else selections.remove(group.id());
                    var context = SkillEvaluationContext.committed(AscendanceTiers.TRANSCENDENT.id(),
                            ranks, selections, milestones, Set.of(), bonuses);
                    var results = SkillStateEvaluator.evaluateAll(context);
                    check(results.get(selected).effective() == enabled, "Choice activation survives rank/bonus/toggle matrix: " + selected);
                    for (var sibling : group.memberIds()) if (!sibling.equals(selected))
                        check(!results.get(sibling).effective(), "Excluded sibling cannot borrow rank, shared bonus or selection: " + sibling);
                    for (var skill : SkillRegistry.values()) if (results.get(skill.id()).effective()) {
                        for (var parent : skill.prerequisiteRanks(ranks.get(skill.id())).keySet()) {
                            if (!parent.equals(skill.replacementTarget())) check(results.get(parent).effective(),
                                    "Descendant cannot leak an inactive signature mechanic: " + skill.id());
                        }
                        if (skill.isReplacement()) check(!results.get(skill.replacementTarget()).effective(),
                                "Replacement cannot reactivate its base signature mechanic");
                    }
                }
            }
        }
    }
    private static void differential(RuntimeBalanceDefinition runtime) {
        var gson = com.mistaboom.essence_ascendance.balance.generated.BalanceDocument.GSON;
        var json = runtime.toJson();
        json.getAsJsonObject("composition").remove("meaningful_progression");
        var caps = new TreeMap<net.minecraft.resources.ResourceLocation,Long>();
        AscendanceTierRegistry.values().forEach(t -> caps.put(t.id(), 1000L * (t.order()+1)));
        json.add("skillCurves",gson.toJsonTree(SkillBalanceGenerator.generate(caps,1)));
        var nominal = RuntimeBalanceDefinition.fromJson(json);
        var original = SkillBalanceGenerator.publishMeaningful(nominal);
        var requirement = SkillRegistry.require(SkillIds.COMBUSTION).progressionRequirements();
        var floor = requirement.outcomes().getFirst();
        var increased = new ProgressionRequirements.Outcome(floor.path(), 14, floor.later(), floor.scale(), floor.offset(),
                floor.minimum(),floor.maximum(),floor.quantum(),floor.multiplyBy(),floor.divideBy(),floor.addFrom(),floor.addScale());
        var changed = new ProgressionRequirements.Skill(requirement.home(),requirement.firstStateFloor(),requirement.additionalImprovementFloor(),
                requirement.endpointPolicy(),requirement.mechanicalConstraints(),requirement.rankModel(),requirement.domains(),requirement.bonusCompatibility(),List.of(increased),false);
        var published = SkillBalanceGenerator.publishMeaningful(nominal,Map.of(SkillIds.COMBUSTION,changed));
        var expected = original.toJson(); var actual = published.toJson();
        expected.getAsJsonObject("effects").getAsJsonObject("combustion").remove("damage");
        actual.getAsJsonObject("effects").getAsJsonObject("combustion").remove("damage");
        expected.getAsJsonObject("skillCurves").remove(SkillIds.COMBUSTION.toString());
        actual.getAsJsonObject("skillCurves").remove(SkillIds.COMBUSTION.toString());
        check(expected.equals(actual),"One skill floor cannot change another parameter, skill, bonus, synergy, cost, equipment, attunement or global allocation");
        check(original.skillCurves().get(SkillIds.COMBUSTION.toString()).ranks().getFirst().cost()
                == published.skillCurves().get(SkillIds.COMBUSTION.toString()).ranks().getFirst().cost(),"Floor overage never recovered in price");
        var strong = nominal.toJson();
        strong.getAsJsonObject("skillCurves").add(SkillIds.COMBUSTION.toString(), gson.toJsonTree(
                new SkillBalanceRuntime.ResolvedSkill(1, List.of(new SkillBalanceRuntime.ResolvedRank(1, 100, 1),
                        new SkillBalanceRuntime.ResolvedRank(2, 200, 8)))));
        var strongResult = SkillBalanceGenerator.publishMeaningful(RuntimeBalanceDefinition.fromJson(strong));
        var strongCurve = strongResult.skillCurves().get(SkillIds.COMBUSTION.toString());
        check(strongCurve.maximumRank() == 2 && strongCurve.ranks().getLast().parameters().get("combustion/damage")
                >= 8 * nominal.config().skillEffects().combustion().damage() - 1e-6,
                "Strong generated skill step remains intact and is not subdivided");
    }
    private static void bonusDifferential(RuntimeBalanceDefinition runtime) {
        var original = BonusTrackGenerator.publishMeaningful(runtime);
        var id = EssenceStats.MELEE_DAMAGE.id();
        var r = com.mistaboom.essence_ascendance.skill.ProgressionRequirements.bonus(id);
        var changed = new ProgressionRequirements.Bonus(r.home(), r.firstStateFloor()+10, r.tierImprovementFloor(),r.unit(),
                r.quantization(),r.mechanicalCap(),r.earliestConstraint(),r.latestConstraint(),r.powerDesirability(),r.compatibility());
        var increased = BonusTrackGenerator.publishMeaningful(runtime, Map.of(id,changed));
        var before = original.toJson(); var after = increased.toJson();
        before.getAsJsonObject("statMaxBonuses").remove(id.toString()); after.getAsJsonObject("statMaxBonuses").remove(id.toString());
        for (String field : List.of("bonusTracks","statOverrides")) {
            before.getAsJsonObject("balanceProfile").getAsJsonObject(field).remove(id.toString());
            after.getAsJsonObject("balanceProfile").getAsJsonObject(field).remove(id.toString());
        }
        check(before.equals(after),"Bonus floor cannot compensate through skills, siblings, costs, synergy, equipment or global allocations");
        check(original.config().balanceProfile().bonusTrack(id).checkpoints().stream().filter(BonusTrackDefinition.Checkpoint::purchasable).findFirst().orElseThrow().segmentCost()
                == increased.config().balanceProfile().bonusTrack(id).checkpoints().stream().filter(BonusTrackDefinition.Checkpoint::purchasable).findFirst().orElseThrow().segmentCost(), "Bonus first-state price ignores overage");
        var track = runtime.config().balanceProfile().bonusTrack(id);
        var points = new ArrayList<BonusTrackDefinition.Checkpoint>();
        for (var tier : AscendanceTierRegistry.values().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList())
            points.add(new BonusTrackDefinition.Checkpoint(tier.id(), tier.order() == 0 ? 0 : 100,
                    tier.order() == 1 ? 100 : 0, tier.order() == 0 ? 0 : 1, tier.order() > 0, tier.order() == 1));
        var one = new BonusTrackDefinition(id,track.category(),track.unit(),50,AscendanceTiers.DORMANT.id(),AscendanceTiers.DORMANT.id(),
                points,.8,BonusTrackDefinition.PurchaseStyle.CONTINUOUS,List.of(),track.applicability(),List.of(),1,List.of("Strong one-state fixture"),"test",Map.of("native_response",4.0,"breadth",.5));
        var placed = BonusTrackGenerator.meaningful(one,r);
        check(placed.activeStateCount()==1 && placed.startTier().equals(placed.completionTier())
                && AscendanceTierRegistry.get(placed.startTier()).orElseThrow().order()>=4, "Desirable one-state bonus is placed late, not stretched");
        check(placed.activeValues().equals(List.of(50.0)),"Strong bonus allocation preserved without filler subdivision");
        var twoPoints = new ArrayList<BonusTrackDefinition.Checkpoint>();
        for (var tier : AscendanceTierRegistry.values().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList()) {
            int order = tier.order();
            twoPoints.add(new BonusTrackDefinition.Checkpoint(tier.id(), order == 0 ? 0 : order == 1 ? 100 : 200,
                    order == 1 || order == 2 ? 100 : 0, order == 0 ? 0 : order == 1 ? 10.0/24 : 1,
                    order > 0, order == 1 || order == 2));
        }
        var two = new BonusTrackDefinition(id,track.category(),track.unit(),24,AscendanceTiers.DORMANT.id(),AscendanceTiers.AWAKENED.id(),
                twoPoints,.8,BonusTrackDefinition.PurchaseStyle.CONTINUOUS,List.of(),track.applicability(),List.of(),1,
                List.of("Strong two-state fixture"),"test",Map.of("native_response",4.0,"breadth",.5));
        var twoValues = BonusTrackGenerator.meaningful(two,r).activeValues();
        check(twoValues.size() == 2 && Math.abs(twoValues.getFirst() - 10) < 1e-9 && twoValues.getLast() == 24,
                "Actual bonus publication keeps the +14 step intact instead of splitting it into +5 states");
    }
    private static void check(boolean condition,String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); }
        catch (RuntimeException expected) {
            Throwable cause = expected;
            while (!(cause instanceof IllegalArgumentException) && cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof IllegalArgumentException) { checks++; return; }
            throw expected;
        }
        throw new AssertionError("Invalid state accepted");
    }
}
