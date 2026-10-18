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

/** Native utility pricing, authored layout rendering and explicit rank gates. */
public final class SkillProgressionPolicyTest {
    private static int checks;
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        EssenceTypes.init(); EssenceStats.init(); AscendanceTiers.init(); MilestoneProviders.init(); Milestones.init(); Skills.init();
        var effects = new Gson().toJsonTree(SkillEffectBalanceSettings.defaults()).getAsJsonObject();
        var tiers = AscendanceTierRegistry.powerTiers().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList();

        for (var skill : SkillRegistry.values()) {
            for (var parent : skill.prerequisiteRanks(64).keySet())
                check(!parent.equals(SkillIds.TOOL_INSTINCT), "Optional Tool Instinct became a prerequisite");
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

            long price = SkillProgressionPolicy.price(10_000, decision.utility(), 1, 1, 1, 0);
            check(price > 0 && SkillProgressionPolicy.price(20_000, decision.utility(), 1, 1, 1, 0) >= price * 2 - 1,
                    "Generated economic budget must propagate to whole-unit prices");
            check(SkillProgressionPolicy.price(10_000, decision.utility(), 1, .5, 1, 0) < price,
                    "Supported native substitute pressure affects price independently of tier");
            check(SkillProgressionPolicy.price(10_000, decision.utility(), 1, 1, 2, price) >= price,
                    "Greater published rank utility cannot cost less");
        }


        for (var axis : CapabilityAxis.values()) check(SkillProgressionPolicy.authority(axis) > 0, "Every axis has explicit policy");
        var authored = com.mistaboom.essence_ascendance.balance.config.SkillSettingsConfig.defaults().requiredTiers();
        SkillBalanceRuntime.withRequiredTiers(authored, () -> {
            for (var essence : EssenceTypes.ORDERED) {
                var definitions = SkillRegistry.values(essence.id());
                var layout = NexusSkillTreeLayout.build(definitions);
                check(layout.equals(NexusSkillTreeLayout.build(definitions.reversed())), "Authored layout is nondeterministic");
                check(layout.nodes().size() == definitions.size(), "Orphaned or duplicated authored node");
                for (var a : layout.nodes()) {
                    check(a.x() >= 0 && a.y() >= 0 && a.x() + NexusSkillTreeLayout.NODE_WIDTH <= layout.contentWidth()
                            && a.y() + NexusSkillTreeLayout.NODE_HEIGHT <= layout.contentHeight(), "Authored node outside scrollable content");
                    for (var b : layout.nodes()) if (a != b) check(a.x() + NexusSkillTreeLayout.NODE_WIDTH <= b.x()
                            || b.x() + NexusSkillTreeLayout.NODE_WIDTH <= a.x() || a.y() + NexusSkillTreeLayout.NODE_HEIGHT <= b.y()
                            || b.y() + NexusSkillTreeLayout.NODE_HEIGHT <= a.y(), "Authored skill nodes overlap");
                }
            }
            return null;
        });
        var original = SkillRegistry.values().getFirst();
        var ranks = new SkillRankPolicy(2, 2, SkillRankCurve.developed(), SkillRankPolicy.RefundRule.NONE,
                Map.of(2, new SkillRankPolicy.Gates(tiers.getLast().id(), Map.of(), List.of())));
        var gated = copy(original, tiers.getFirst().id(), List.of(), ranks);
        check(SkillBalanceRuntime.requiredTier(gated, tiers.get(1).id(), 1).equals(tiers.get(1).id()),
                "Configured base tier is authoritative before a later rank gate");
        check(SkillBalanceRuntime.requiredTier(gated, tiers.get(1).id(), 2).equals(tiers.getLast().id()),
                "Explicit later rank gate remains authoritative");
        com.mistaboom.essence_ascendance.client.NexusSkillRoutingTest.run();

        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("SkillProgressionPolicyTest: " + checks + " checks PASS");
    }
    private static SkillDefinition copy(SkillDefinition s, ResourceLocation tier, List<ResourceLocation> prerequisites, SkillRankPolicy ranks) {
        return new SkillDefinition(s.id(), s.essenceId(), s.nameTranslationKey(), s.descriptionTranslationKey(), tier, s.costBand(), prerequisites,
                s.requirements(), s.choiceGroup(), s.replacementTarget(), s.activationPolicy(), s.displayOrder(), s.layoutHint(), ranks);
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
