package com.mistaboom.essence_ascendance.skill.balance;

import com.google.gson.Gson;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.client.nexus.NexusSkillTreeLayout;
import com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Actual registered mechanics plus adversarial graphs; never asserts historical output placements. */
public final class SkillProgressionPolicyTest {
    private static int checks;
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        EssenceTypes.init(); EssenceStats.init(); AscendanceTiers.init(); MilestoneProviders.init(); Milestones.init(); Skills.init();
        var effects = new Gson().toJsonTree(SkillEffectBalanceSettings.defaults()).getAsJsonObject();
        var tiers = AscendanceTierRegistry.powerTiers().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList();
        int later = 0;
        for (var skill : SkillRegistry.values()) {
            var semantics = SkillBalanceSemantics.require(skill.id());
            var decision = SkillProgressionPolicy.evaluate(skill, semantics, effects);
            check(decision.utility() > 0 && decision.exposure() > 0, "Every registered mechanic has positive explicit utility");
            var costly = new SkillBalanceSemantics.Descriptor(semantics.skillId(), semantics.contributions(), semantics.deliveries(),
                    semantics.equipment(), semantics.actions(), semantics.uptime(), semantics.triggerReliability(), semantics.cooldownSeconds(),
                    semantics.durationSeconds(), semantics.rangeBlocks(), semantics.areaRadiusBlocks(), semantics.resourceCost() + 4,
                    semantics.setupRisk(), semantics.condition(), semantics.confidence(), semantics.provenance());
            var burden = SkillProgressionPolicy.evaluate(skill, costly, effects);
            check(burden.utility() <= decision.utility() && SkillProgressionPolicy.price(10_000, burden.utility(), 1, 1, 1, 0)
                            <= SkillProgressionPolicy.price(10_000, decision.utility(), 1, 1, 1, 0),
                    "Supported greater operating burden cannot increase intrinsic utility or price");
            for (var tier : tiers) check(SkillProgressionPolicy.evaluate(copy(skill, tier.id(), skill.prerequisites(), skill.rankPolicy()), semantics, effects).equals(decision),
                    "Catalog placement leaked into computation: " + skill.id());
            var larger = effects.deepCopy();
            for (var outcome : skill.progressionRequirements().outcomes()) {
                double coefficient = outcome.coefficient(larger);
                if (coefficient != 0) ProgressionRequirements.write(larger, outcome.path(),
                        ProgressionRequirements.read(larger, outcome.path()) + Math.max(1, outcome.first()) * 10 / coefficient);
            }
            var increased = SkillProgressionPolicy.evaluate(skill, semantics, larger);
            check(increased.utility() >= decision.utility(), "Increasing native benefits cannot reduce utility");
            if (increased.band().ordinal() > decision.band().ordinal()) later++;
            long price = SkillProgressionPolicy.price(10_000, decision.utility(), 1, 1, 1, 0);
            check(price > 0 && SkillProgressionPolicy.price(20_000, decision.utility(), 1, 1, 1, 0) >= price * 2 - 1,
                    "Generated economic budget must propagate to whole-unit prices");
            check(SkillProgressionPolicy.price(10_000, decision.utility(), 1, .5, 1, 0) < price,
                    "Supported native substitute pressure affects price independently of tier");
            check(SkillProgressionPolicy.price(10_000, decision.utility(), 1, 1, 2, price) >= price,
                    "Greater published rank utility cannot cost less");
        }
        check(later > 0, "Intrinsic power must support later as well as earlier placements");
        environmentInputs();
        for (var axis : CapabilityAxis.values()) check(SkillProgressionPolicy.authority(axis) > 0, "Every axis has explicit policy");
        for (int scenario = 0; scenario < 7; scenario++) {
            var candidates = new TreeMap<ResourceLocation, ResourceLocation>(); int i = 0;
            for (var skill : SkillRegistry.values()) candidates.put(skill.id(), tiers.get(scenario < 5 ? scenario : (i++ * scenario) % tiers.size()).id());
            var closed = SkillProgressionGraph.close(SkillRegistry.values(), candidates);
            check(closed.equals(SkillProgressionGraph.close(SkillRegistry.values().reversed(), candidates)), "Graph closure depends on registry traversal order");
            for (var skill : SkillRegistry.values()) for (var parent : skill.prerequisiteRanks(skill.rankPolicy().projectionRanks()).keySet())
                check(tiers.indexOf(AscendanceTierRegistry.get(closed.get(parent)).orElseThrow())
                        < tiers.indexOf(AscendanceTierRegistry.get(closed.get(skill.id())).orElseThrow()), "Prerequisite must be at least one tier earlier: " + skill.id());
            check(closed.get(SkillIds.RISING_RECOVERY).equals(closed.get(SkillIds.LIFE_STEAL)), "Independent exclusive choices differ in tier");
            check(closed.get(SkillIds.DOUBLE_JUMP).equals(closed.get(SkillIds.CHARGED_JUMP)), "Jump alternatives differ in tier");
            SkillBalanceRuntime.withRequiredTiers(closed, () -> {
                for (var essence : EssenceTypes.ORDERED) {
                    var definitions = SkillRegistry.values(essence.id()); var layout = NexusSkillTreeLayout.build(definitions);
                    check(layout.equals(NexusSkillTreeLayout.build(definitions.reversed())), "Layout is nondeterministic");
                    check(layout.nodes().size() == definitions.size(), "Orphaned or duplicated node");
                    for (var a : layout.nodes()) {
                        check(a.x() >= 0 && a.y() >= 0 && a.x() + NexusSkillTreeLayout.NODE_WIDTH <= layout.contentWidth()
                                && a.y() + NexusSkillTreeLayout.NODE_HEIGHT <= layout.contentHeight(), "Generated node outside scrollable content");
                        for (var b : layout.nodes()) if (a != b) check(a.x() + NexusSkillTreeLayout.NODE_WIDTH <= b.x()
                                || b.x() + NexusSkillTreeLayout.NODE_WIDTH <= a.x() || a.y() + NexusSkillTreeLayout.NODE_HEIGHT <= b.y()
                                || b.y() + NexusSkillTreeLayout.NODE_HEIGHT <= a.y(), "Generated skill nodes overlap");
                    }
                }
                return null;
            });
        }
        var a = SkillRegistry.values().get(0); var b = SkillRegistry.values().get(1);
        var cycle = List.of(copy(a, a.catalogRequiredTierId(), List.of(b.id()), SkillRankPolicy.generated()),
                copy(b, b.catalogRequiredTierId(), List.of(a.id()), SkillRankPolicy.generated()));
        rejects(() -> SkillProgressionGraph.close(cycle, Map.of(a.id(), tiers.getFirst().id(), b.id(), tiers.getFirst().id())), "Cycle accepted");
        var parentPolicy = new SkillRankPolicy(2, 2, SkillRankCurve.developed(), SkillRankPolicy.RefundRule.NONE,
                Map.of(2, new SkillRankPolicy.Gates(tiers.getLast().id(), Map.of(), List.of())));
        var childPolicy = new SkillRankPolicy(1, 1, SkillRankCurve.developed(), SkillRankPolicy.RefundRule.NONE,
                Map.of(1, new SkillRankPolicy.Gates(null, Map.of(a.id(), 2), List.of())));
        var ranked = List.of(copy(a, a.catalogRequiredTierId(), List.of(), parentPolicy), copy(b, b.catalogRequiredTierId(), List.of(), childPolicy));
        rejects(() -> SkillProgressionGraph.close(ranked, Map.of(a.id(), tiers.getFirst().id(), b.id(), tiers.getFirst().id())),
                "Apex prerequisite rank cannot have a later dependent tier");
        var reachablePolicy = new SkillRankPolicy(2, 2, SkillRankCurve.developed(), SkillRankPolicy.RefundRule.NONE,
                Map.of(2, new SkillRankPolicy.Gates(tiers.get(tiers.size() - 2).id(), Map.of(), List.of())));
        var reachable = List.of(copy(a, a.catalogRequiredTierId(), List.of(), reachablePolicy), copy(b, b.catalogRequiredTierId(), List.of(), childPolicy));
        var resolved = SkillProgressionGraph.close(reachable, Map.of(a.id(), tiers.getFirst().id(), b.id(), tiers.getFirst().id()));
        com.mistaboom.essence_ascendance.client.NexusSkillRoutingTest.run();
        check(resolved.get(b.id()).equals(tiers.getLast().id()), "Required parent rank gate did not constrain child availability");
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("SkillProgressionPolicyTest: " + checks + " checks PASS");
    }
    private static void environmentInputs() {
        var baseline = new PackEvidence(Map.of(), List.of(), List.of(), Map.of(), List.of(), List.of(), Map.of());
        var source = new AcquisitionSource("test:shield_supply", AcquisitionSource.Kind.RECIPE, ProgressionBand.LATE,
                1, true, true, 16, .9, List.of(), "Measured test operation");
        var shield = new ResourceEvidence("minecraft:shield", ProgressionBand.LATE, Availability.RENEWABLE_MANUAL, Automation.NONE,
                true, true, 8, .9, List.of(source), List.of());
        var late = withResource(baseline, shield);
        var skill = SkillBalanceSemantics.descriptors().stream().filter(d -> d.equipment().contains(SkillBalanceSemantics.Equipment.SHIELD)).findFirst().orElseThrow();
        check(SkillOperatingAccess.evaluate(skill, late).earliestSupportedSetup() == ProgressionBand.LATE,
                "Supported later operating equipment must constrain availability");
        var missing = SkillOperatingAccess.evaluate(skill, baseline);
        check(!missing.unresolved().isEmpty() && missing.earliestSupportedSetup() == ProgressionBand.ENTRY,
                "Missing acquisition must not fabricate a later equipment gate or assert verified absence");
        var rejected = new ResourceEvidence(shield.itemId(), shield.stage(), shield.availability(), shield.automation(),
                true, false, shield.economicValue(), .9, shield.sources(), List.of());
        check(SkillOperatingAccess.evaluate(skill, withResource(baseline, rejected)).witnesses().isEmpty(), "Internal supplies cannot prove external setup");
        String essence = "essence_ascendance:terrestrial";
        var economy = new com.mistaboom.essence_ascendance.balance.economy.EconomyProfile(Map.of("minecraft:shield",
                new com.mistaboom.essence_ascendance.balance.economy.EconomyProfile.ResourceValue(
                        new com.mistaboom.essence_ascendance.balance.economy.EconomicValue(8),
                        com.mistaboom.essence_ascendance.balance.economy.DissolutionYield.of(8), Map.of(essence, 8.0), List.of())),
                List.of(), List.of(), List.of(), 0, com.mistaboom.essence_ascendance.balance.economy.EconomyProcessingPolicy.defaults());
        check(SkillEconomyAccess.evaluate(late, economy, ProgressionBand.ENTRY, essence).priceFactor() == 1,
                "Later rates must not inflate early prices");
        check(SkillEconomyAccess.evaluate(late, economy, ProgressionBand.LATE, essence).priceFactor() == 4,
                "Known recurring supply must affect price within its explicit bound");
        var unmeasured = new AcquisitionSource(source.id(), source.kind(), source.stage(), 1, true, false, 16, .9, List.of(), "Unknown rate");
        var unknown = new ResourceEvidence(shield.itemId(), shield.stage(), shield.availability(), shield.automation(),
                true, true, 8, .9, List.of(unmeasured), List.of());
        var unchanged = SkillEconomyAccess.evaluate(withResource(baseline, unknown), economy, ProgressionBand.LATE, essence);
        check(unchanged.priceFactor() == 1 && unchanged.unknownRateSources() == 1, "An unmeasured numeric placeholder cannot become throughput");
        var sense = com.mistaboom.essence_ascendance.balance.engine.CompetitiveCapabilities.measurement(CapabilityAxis.INFORMATION, 1.0, "presence",
                "ore sensor", new CapabilityEvidence.Scope("ore_blocks", "sphere", 16.0, null), CapabilityEvidence.Operation.manual(),
                "test", CapabilityEvidence.Origin.TYPED_ADAPTER, List.of());
        check(SkillFunctionalScope.compatible(SkillIds.ORE_SIGHT, sense) && !SkillFunctionalScope.compatible(SkillIds.THREAT_SENSE, sense),
                "An ore sensor cannot establish entity-threat information");
    }
    private static PackEvidence withResource(PackEvidence base, ResourceEvidence resource) {
        return new PackEvidence(Map.of(resource.itemId(), resource), base.equipment(), base.enemies(), base.frontiers(),
                base.facts(), base.warnings(), base.graphSummary(), base.capabilities());
    }
    private static SkillDefinition copy(SkillDefinition s, ResourceLocation tier, List<ResourceLocation> prerequisites, SkillRankPolicy ranks) {
        return new SkillDefinition(s.id(), s.essenceId(), s.nameTranslationKey(), s.descriptionTranslationKey(), tier, s.costBand(), prerequisites,
                s.requirements(), s.choiceGroup(), s.replacementTarget(), s.activationPolicy(), s.displayOrder(), s.layoutHint(), ranks);
    }
    private static void rejects(Runnable operation, String message) {
        try { operation.run(); } catch (IllegalArgumentException expected) { checks++; return; } throw new AssertionError(message);
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
