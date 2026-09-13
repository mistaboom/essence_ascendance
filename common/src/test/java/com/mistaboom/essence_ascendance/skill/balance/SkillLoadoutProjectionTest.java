package com.mistaboom.essence_ascendance.skill.balance;

import com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis;
import com.mistaboom.essence_ascendance.skill.SkillDefinition;
import com.mistaboom.essence_ascendance.skill.SkillEvaluationContext;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.SkillRankCurve;
import com.mistaboom.essence_ascendance.skill.SkillRankPolicy;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.skill.requirement.BonusInvestmentRequirement;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Executable assertions against the real catalog and state evaluator; requires the Minecraft test runtime. */
public final class SkillLoadoutProjectionTest {
    private static int assertions;
    private static final List<SkillDefinition> CATALOG = Skills.definitions();

    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        com.mistaboom.essence_ascendance.essence.EssenceTypes.init(); AscendanceTiers.init();
        com.mistaboom.essence_ascendance.stat.EssenceStats.init();
        com.mistaboom.essence_ascendance.progression.MilestoneProviders.init();
        com.mistaboom.essence_ascendance.progression.Milestones.init(); Skills.init();
        coverage();
        exclusiveBranchesAndDeterminism();
        replacementsAndFutureRanks();
        equipmentAndDelivery();
        rankedGates();
        System.out.println("SkillLoadoutProjectionTest: " + assertions + " assertions passed");
    }

    private static void coverage() {
        SkillBalanceSemantics.validate(CATALOG);
        check(CATALOG.size() == 90, "Curated catalog count is an intentional regression fixture");
        for (SkillDefinition definition : CATALOG) {
            var semantics = SkillBalanceSemantics.require(definition.id());
            check(!semantics.contributions().isEmpty(), "Every catalog skill declares meaningful axes");
            check(semantics.expectedAvailability() > 0 && semantics.expectedAvailability() <= 1, "Availability is bounded");
            check(semantics.confidence() < 1, "First-pass trigger estimates cannot claim certainty");
        }
        check(SkillBalanceSemantics.require(SkillIds.UNTETHERED_FLIGHT).contributions().stream()
                .anyMatch(c -> c.axis() == CapabilityAxis.FLIGHT && c.form() == SkillBalanceSemantics.Form.CAPABILITY),
                "Sustained flight is a transformative capability");
    }

    private static void exclusiveBranchesAndDeterminism() {
        List<SkillDefinition> offense = category("offense");
        var original = project(offense, ranks(offense, 1), true);
        List<SkillDefinition> reversed = new ArrayList<>(offense);
        Collections.reverse(reversed);
        Map<ResourceLocation, Integer> reversedRanks = new LinkedHashMap<>();
        reversed.forEach(skill -> reversedRanks.put(skill.id(), 1));
        var reordered = project(reversed, reversedRanks, true);
        check(original.equals(reordered), "Input order cannot change chosen loadouts, diagnostics or pressure");
        for (var scenario : original.scenarios()) {
            var active = scenario.activeRanks().keySet();
            check(!(active.contains(SkillIds.FRENZY) && active.contains(SkillIds.DESPERATION)), "Stances cannot stack");
            check(!active.contains(SkillIds.ARMOR_CRACK) || active.contains(SkillIds.FRENZY), "Frenzy descendants inherit its active branch");
            check(!active.contains(SkillIds.DEATH_RUSH) || active.contains(SkillIds.DESPERATION), "Desperation descendants inherit its active branch");
            check(count(active, SkillIds.KINDLING, SkillIds.FROSTBITE, SkillIds.STATIC_CHARGE) <= 1, "One elemental branch");
            check(count(active, SkillIds.HOMING_PROJECTILE, SkillIds.RICOCHET, SkillIds.PIERCING_PROJECTILE) <= 1, "One projectile path");
            for (var pressure : scenario.axisPressure().entrySet())
                check(pressure.getValue() <= original.conservativeUpperBounds().get(pressure.getKey()) + 1e-9,
                        "Conservative bound contains every concrete reachable build");
        }
        var current = project(offense, ranks(offense, 1), false);
        check(current.scenarios().stream().noneMatch(s -> s.activeRanks().containsKey(SkillIds.EXPLOSIVE_PAYLOAD)),
                "Unimplemented mechanics are excluded from current-build demand");
        check(original.scenarios().stream().anyMatch(s -> s.activeRanks().containsKey(SkillIds.EXPLOSIVE_PAYLOAD)),
                "Future catalog pressure can be projected without enabling effects");
    }

    private static void replacementsAndFutureRanks() {
        List<SkillDefinition> mobility = category("mobility");
        var one = project(mobility, ranks(mobility, 1), true);
        var five = project(mobility, ranks(mobility, 5), true);
        for (var scenario : one.scenarios()) {
            var active = scenario.activeRanks().keySet();
            check(!(active.contains(SkillIds.VECTOR_JUMP) && active.contains(SkillIds.DOUBLE_JUMP)), "Replacement target not double counted");
            check(!(active.contains(SkillIds.FATIGUE_FLIGHT) && (active.contains(SkillIds.ESSENCE_WINGS)
                    || active.contains(SkillIds.UNTETHERED_FLIGHT))), "Flight replacement suppresses its owned target");
            check(!(active.contains(SkillIds.ESSENCE_WINGS) && active.contains(SkillIds.UNTETHERED_FLIGHT)), "One flight replacement choice");
            check(!active.contains(SkillIds.VECTOR_BOOST) || (active.contains(SkillIds.ESSENCE_WINGS)
                    && active.contains(SkillIds.VECTOR_JUMP)), "Cross-branch prerequisites constrain boost");
        }
        check(one.scenarios().stream().anyMatch(s -> s.activeRanks().containsKey(SkillIds.VECTOR_JUMP)), "Ownership-only replacement parent permits activation");
        check(one.axisEnvelope().get(CapabilityAxis.FLIGHT).equals(five.axisEnvelope().get(CapabilityAxis.FLIGHT)),
                "Buying hypothetical repeated ranks does not multiply binary flight access");
        check(five.axisEnvelope().get(CapabilityAxis.GROUND_SPEED) > one.axisEnvelope().get(CapabilityAxis.GROUND_SPEED),
                "Future numeric ranks are included in projections");
        Map<ResourceLocation, Integer> missingParent = new LinkedHashMap<>(ranks(mobility, 1));
        missingParent.remove(SkillIds.DOUBLE_JUMP);
        var blocked = project(mobility, missingParent, true);
        check(blocked.scenarios().stream().noneMatch(s -> s.activeRanks().containsKey(SkillIds.VECTOR_JUMP)),
                "A replacement still requires ownership of the suppressed parent");
    }

    private static void equipmentAndDelivery() {
        var frenzy = definition(SkillIds.FRENZY);
        SkillEvaluationContext context = SkillEvaluationContext.committed(AscendanceTiers.TRANSCENDENT.id(),
                Map.of(frenzy.id(), 1), Map.of(frenzy.choiceGroup(), frenzy.id()), Set.of(), Set.of(), Set.of(), Map.of());
        double previous = -1;
        for (var equipment : SkillLoadoutProjection.representativeEquipment().subList(0, 3)) {
            var scenario = SkillLoadoutProjection.evaluate(List.of(frenzy), context, (id, rank) -> 1, equipment);
            double pressure = scenario.axisPressure().get(CapabilityAxis.SUSTAINED_DAMAGE);
            check(previous < 0 || previous == pressure, "All-attack benefit counted once for each usable delivery");
            previous = pressure;
        }
        var unequipped = SkillLoadoutProjection.representativeEquipment().getLast();
        check(SkillLoadoutProjection.evaluate(List.of(frenzy), context, (id, rank) -> 1, unequipped)
                .contributingRanks().isEmpty(), "No attack delivery yields no usable attack contribution");
        var defense = category("defense");
        var projected = project(defense, ranks(defense, 1), true);
        for (var scenario : projected.scenarios()) {
            if (scenario.equipmentContext().equals("ranged")) {
                check(!scenario.contributingRanks().containsKey(SkillIds.GUARDED_ADVANCE), "Bow firing does not count shield guarding");
                check(!scenario.contributingRanks().containsKey(SkillIds.RIPOSTE), "Bow firing cannot count melee counterattack");
            }
        }
    }

    private static void rankedGates() {
        var momentum = definition(SkillIds.RUNNING_MOMENTUM);
        var rush = definition(SkillIds.RUSH);
        SkillRankPolicy momentumPolicy = new SkillRankPolicy(2, 5, SkillRankCurve.standard(),
                SkillRankPolicy.RefundRule.NONE, Map.of());
        var liveRequirement = new BonusInvestmentRequirement(
                ResourceLocation.fromNamespaceAndPath("essence_ascendance", "test/rank_investment"),
                rush.essenceId(), 100, "test.rank_investment");
        SkillRankPolicy rushPolicy = new SkillRankPolicy(3, 5, SkillRankCurve.standard(), SkillRankPolicy.RefundRule.NONE,
                Map.of(2, new SkillRankPolicy.Gates(AscendanceTiers.TRANSCENDENT.id(), Map.of(momentum.id(), 2), List.of(liveRequirement))));
        List<SkillDefinition> catalog = List.of(withPolicy(momentum, momentumPolicy), withPolicy(rush, rushPolicy));
        var insufficientParent = project(catalog, Map.of(momentum.id(), 1, rush.id(), 2), true);
        check(insufficientParent.scenarios().stream().noneMatch(s -> s.activeRanks().getOrDefault(rush.id(), 0) > 1),
                "Rank-specific prerequisite gates reduce projected reachable rank");
        var sufficientParent = project(catalog, Map.of(momentum.id(), 2, rush.id(), 2), true);
        check(sufficientParent.scenarios().stream().anyMatch(s -> s.activeRanks().getOrDefault(rush.id(), 0) == 2),
                "Meeting the parent rank makes the later rank reachable");
        var early = SkillLoadoutProjection.project(catalog, AscendanceTiers.AWAKENED.id(),
                Map.of(momentum.id(), 2, rush.id(), 2), (id, rank) -> Math.sqrt(rank), Map.of(), true);
        check(early.scenarios().stream().noneMatch(s -> s.activeRanks().getOrDefault(rush.id(), 0) > 1),
                "Future rank-specific tier requirement is enforced independently from base-tier unlock");
        var thirdRankMissingParent = project(catalog, Map.of(momentum.id(), 1, rush.id(), 3), true);
        check(thirdRankMissingParent.scenarios().stream().noneMatch(s -> s.activeRanks().getOrDefault(rush.id(), 0) > 1),
                "A later rank retains prerequisite-rank gates imposed by an earlier rank");
        var earlyThirdRank = SkillLoadoutProjection.project(catalog, AscendanceTiers.AWAKENED.id(),
                Map.of(momentum.id(), 2, rush.id(), 3), (id, rank) -> Math.sqrt(rank), Map.of(), true);
        check(earlyThirdRank.scenarios().stream().noneMatch(s -> s.activeRanks().getOrDefault(rush.id(), 0) > 1),
                "A later rank retains the strongest earlier tier gate");
        var stagedWithoutTier = new SkillEvaluationContext(AscendanceTiers.AWAKENED.id(),
                Map.of(momentum.id(), 2, rush.id(), 1), Map.of(momentum.id(), 2, rush.id(), 2),
                Map.of(), Map.of(), Set.of(), Set.of(), Set.of(), Map.of(rush.essenceId(), 100L), Map.of(rush.essenceId(), 100L));
        var stagedResult = com.mistaboom.essence_ascendance.skill.SkillStateEvaluator.evaluateAll(catalog, stagedWithoutTier).get(rush.id());
        check(stagedResult.effective() && !stagedResult.projectedEffective(),
                "Owning rank one does not bypass a staged rank-two tier gate");
        check(com.mistaboom.essence_ascendance.skill.SkillStateEvaluator.activationPlan(catalog, rush.id(),
                Map.of(momentum.id(), 1, rush.id(), 2), Map.of()).isEmpty(),
                "Automatic activation planning enforces required parent ranks");
        check(com.mistaboom.essence_ascendance.skill.SkillStateEvaluator.activationPlan(catalog, rush.id(),
                Map.of(momentum.id(), 2, rush.id(), 2), Map.of()).isPresent(),
                "Automatic activation planning accepts a sufficient parent rank");
        var noInvestment = SkillEvaluationContext.committed(AscendanceTiers.TRANSCENDENT.id(),
                Map.of(momentum.id(), 2, rush.id(), 3), Map.of(), Set.of(), Set.of(), Set.of(), Map.of());
        var suspended = SkillLoadoutProjection.evaluate(catalog, noInvestment, (id, rank) -> Math.sqrt(rank),
                SkillLoadoutProjection.representativeEquipment().getFirst());
        check(!suspended.activeRanks().containsKey(rush.id()), "Rank three retains the live investment requirement from rank two");
    }

    private static SkillDefinition withPolicy(SkillDefinition skill, SkillRankPolicy policy) {
        return new SkillDefinition(skill.id(), skill.essenceId(), skill.nameTranslationKey(), skill.descriptionTranslationKey(),
                skill.requiredTierId(), skill.costBand(), skill.prerequisites(), skill.requirements(), skill.choiceGroup(),
                skill.replacementTarget(), skill.activationPolicy(), skill.displayOrder(), skill.layoutHint(), policy);
    }
    private static SkillLoadoutProjection.Projection project(List<SkillDefinition> catalog, Map<ResourceLocation, Integer> ranks, boolean planned) {
        return SkillLoadoutProjection.project(catalog, AscendanceTiers.TRANSCENDENT.id(), ranks,
                (id, rank) -> Math.sqrt(rank), Map.of(), planned);
    }
    private static Map<ResourceLocation, Integer> ranks(List<SkillDefinition> catalog, int rank) {
        Map<ResourceLocation, Integer> ranks = new LinkedHashMap<>();
        catalog.forEach(skill -> ranks.put(skill.id(), rank)); return ranks;
    }
    private static List<SkillDefinition> category(String path) {
        return CATALOG.stream().filter(skill -> skill.essenceId().getPath().equals(path)).toList();
    }
    private static SkillDefinition definition(ResourceLocation id) {
        return CATALOG.stream().filter(skill -> skill.id().equals(id)).findFirst().orElseThrow();
    }
    private static int count(Set<ResourceLocation> values, ResourceLocation... ids) {
        int count = 0; for (ResourceLocation id : ids) if (values.contains(id)) count++; return count;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message); assertions++;
    }
}
