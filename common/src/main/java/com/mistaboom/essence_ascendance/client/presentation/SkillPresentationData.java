package com.mistaboom.essence_ascendance.client.presentation;

import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.client.ClientEssenceState;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.presentation.PresentationBehavior;
import com.mistaboom.essence_ascendance.presentation.PresentationMetric;
import com.mistaboom.essence_ascendance.skill.SkillChoiceGroup;
import com.mistaboom.essence_ascendance.skill.SkillDefinition;
import com.mistaboom.essence_ascendance.skill.SkillEvaluationContext;
import com.mistaboom.essence_ascendance.skill.SkillEvaluationResult;
import com.mistaboom.essence_ascendance.skill.SkillPurchaseEligibility;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.SkillStateEvaluator;
import com.mistaboom.essence_ascendance.skill.balance.SkillRankEffectScaling;
import com.mistaboom.essence_ascendance.skill.requirement.BonusInvestmentRequirement;
import com.mistaboom.essence_ascendance.skill.requirement.PermanentMilestoneRequirement;
import com.mistaboom.essence_ascendance.skill.requirement.SkillRequirement;
import com.mistaboom.essence_ascendance.skill.tooltip.SkillTooltipRegistry;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shared skill facts for Nexus prose and Archive articles. */
public final class SkillPresentationData {
    private SkillPresentationData() { }

    public record EffectLine(
            SkillTooltipRegistry.Binding binding,
            Component prose,
            List<PresentationMetric.Reading> metrics,
            PresentationBehavior<com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings> behavior
    ) {
        public EffectLine { metrics = List.copyOf(metrics); }
    }

    public record RankProjection(
            int rank,
            long cost,
            ResourceLocation requiredTier,
            Map<ResourceLocation, Integer> prerequisites,
            List<SkillRequirement> requirements,
            List<EffectLine> effects,
            SkillPurchaseEligibility playerEligibility
    ) {
        public RankProjection {
            prerequisites = Map.copyOf(prerequisites);
            requirements = List.copyOf(requirements);
            effects = List.copyOf(effects);
        }
    }

    public record Projection(
            SkillDefinition definition,
            PresentationContext.Availability runtimeAvailability,
            PresentationContext.Availability playerAvailability,
            int maximumRank,
            int currentRank,
            Integer nextRank,
            SkillEvaluationResult playerState,
            List<RankProjection> ranks,
            SkillChoiceGroup choiceGroup,
            List<SkillDefinition> exclusions,
            SkillDefinition replacementTarget,
            List<SkillDefinition> replacedBy
    ) {
        public Projection {
            ranks = List.copyOf(ranks);
            exclusions = List.copyOf(exclusions);
            replacedBy = List.copyOf(replacedBy);
        }
        public boolean runtimeReady() { return runtimeAvailability == PresentationContext.Availability.READY; }
        public boolean playerReady() { return playerAvailability == PresentationContext.Availability.READY; }
    }

    public static Projection project(SkillDefinition skill, PresentationContext context) {
        Objects.requireNonNull(skill);
        Objects.requireNonNull(context);
        RuntimeBalanceDefinition runtime = context.runtime().definition();
        var curve = runtime == null ? null : runtime.skillCurves().get(skill.id().toString());
        int maximumRank = curve == null ? 0 : curve.maximumRank();

        ClientEssenceState.Snapshot snapshot = context.player().snapshot();
        ClientEssenceState.SkillPurchaseSnapshot receipt = snapshot == null ? null : snapshot.ownedSkills().get(skill.id());
        int currentRank = receipt == null ? 0 : receipt.rank();
        SkillEvaluationContext evaluationContext = snapshot == null ? null : committedContext(snapshot, context.player().bonusTotals());
        Map<ResourceLocation, SkillEvaluationResult> evaluations = evaluationContext == null
                ? Map.of() : SkillStateEvaluator.evaluateAll(evaluationContext);
        SkillEvaluationResult playerState = evaluations.get(skill.id());

        List<RankProjection> ranks = new ArrayList<>();
        if (curve != null) {
            for (int rank = 1; rank <= curve.maximumRank(); rank++) {
                var settings = SkillRankEffectScaling.applyResolved(runtime.config().skillEffects(),
                        Map.of(skill.id(), rank), runtime.skillCurves());
                List<EffectLine> effects = SkillTooltipRegistry.bindings(skill.id()).stream().map(binding -> {
                    List<PresentationMetric.Reading> readings = binding.metrics().stream()
                            .map(metric -> metric.read(settings)).toList();
                    return new EffectLine(binding, binding.render(settings), readings,
                            binding.behavior() != null && binding.behavior().appliesTo(settings) ? binding.behavior() : null);
                }).toList();
                SkillPurchaseEligibility eligibility = evaluationContext == null ? null
                        : SkillStateEvaluator.evaluatePurchaseEligibility(skill, evaluationContext, rank);
                ranks.add(new RankProjection(rank, rankCost(runtime, skill, rank), skill.requiredTierId(rank),
                        skill.prerequisiteRanks(rank), skill.requirements(rank), effects, eligibility));
            }
        }

        SkillChoiceGroup choice = skill.choiceGroupId().flatMap(SkillRegistry::choiceGroup).orElse(null);
        List<SkillDefinition> exclusions = choice == null ? List.of() : choice.memberIds().stream()
                .filter(id -> !id.equals(skill.id())).map(SkillRegistry::require).toList();
        SkillDefinition replacementTarget = skill.replacementTargetId().flatMap(SkillRegistry::get).orElse(null);
        List<SkillDefinition> replacedBy = SkillRegistry.values().stream()
                .filter(candidate -> candidate.replacementTargetId().filter(skill.id()::equals).isPresent()).toList();
        Integer nextRank = maximumRank == 0 || currentRank >= maximumRank ? null : Math.max(1, currentRank + 1);
        return new Projection(skill, context.runtime().availability(), context.player().availability(), maximumRank,
                currentRank, nextRank, playerState, ranks, choice, exclusions, replacementTarget, replacedBy);
    }

    public static List<Component> resolveEffects(RuntimeBalanceDefinition runtime, ResourceLocation skill,
                                                  Map<ResourceLocation, Integer> ranks) {
        var resolved = SkillRankEffectScaling.applyResolved(runtime.config().skillEffects(), ranks, runtime.skillCurves());
        return SkillTooltipRegistry.bindings(skill).stream().map(binding -> binding.render(resolved)).toList();
    }

    public static int maximumRank(RuntimeBalanceDefinition runtime, SkillDefinition skill) {
        if (runtime == null) return 0;
        var curve = runtime.skillCurves().get(skill.id().toString());
        return curve == null ? 0 : curve.maximumRank();
    }

    public static long rankCost(RuntimeBalanceDefinition runtime, SkillDefinition skill, int rank) {
        if (runtime == null) throw new IllegalStateException("Authoritative Skill runtime is unavailable");
        var curve = runtime.skillCurves().get(skill.id().toString());
        if (curve == null || rank < 1 || rank > curve.maximumRank())
            throw new IllegalArgumentException("Rank outside synchronized Skill curve " + skill.id());
        return curve.ranks().get(rank - 1).cost();
    }

    public static SkillEvaluationContext committedContext(ClientEssenceState.Snapshot snapshot,
                                                            Map<ResourceLocation, Long> bonusTotals) {
        Map<ResourceLocation, Integer> ranks = new LinkedHashMap<>();
        snapshot.ownedSkills().forEach((id, receipt) -> ranks.put(id, receipt.rank()));
        return SkillEvaluationContext.committed(snapshot.tierId(), ranks, snapshot.loadoutSelections(),
                snapshot.completedMilestones(), java.util.Set.of(), bonusTotals);
    }

    public static Component skillName(ResourceLocation skillId) {
        return SkillRegistry.get(skillId).map(SkillPresentationData::skillName)
                .orElseGet(() -> Component.literal(skillId.getPath()));
    }

    /** Canonical category-colored Skill name for lists, articles and relationships. */
    public static Component skillName(SkillDefinition skill) {
        return Component.translatable(skill.nameTranslationKey()).withStyle(style -> style.withColor(
                AscendancePalette.categoryRgb(skill.essenceId())));
    }

    public static Component tierName(ResourceLocation tierId) {
        Component name = AscendanceTierRegistry.get(tierId).map(tier -> (Component) EssenceText.ascendanceTier(tier))
                .orElseGet(() -> Component.literal(tierId.getPath()));
        return name.copy().withStyle(style -> style.withColor(AscendancePalette.tierMetalRgb(tierId)));
    }

    public static Component choiceGroupName(ResourceLocation groupId) {
        return SkillRegistry.choiceGroup(groupId).map(group -> (Component) Component.translatable(group.translationKey()))
                .orElseGet(() -> Component.literal(groupId.getPath()));
    }

    public static SkillRequirement requirement(SkillDefinition skill, int rank, ResourceLocation requirementId) {
        for (SkillRequirement requirement : skill.requirements(rank))
            if (requirement.id().equals(requirementId)) return requirement;
        return null;
    }

    public static ClientEssenceState.MilestoneSnapshot milestoneState(SkillDefinition skill,
                                                                       int rank,
                                                                       ResourceLocation requirementId,
                                                                       ClientEssenceState.Snapshot snapshot) {
        SkillRequirement requirement = requirement(skill, rank, requirementId);
        if (requirement instanceof PermanentMilestoneRequirement milestone && snapshot != null)
            return snapshot.skillMilestones().get(milestone.milestoneId());
        return null;
    }

    public static Component requirementDescription(SkillDefinition skill, int rank, ResourceLocation requirementId,
                                                   ClientEssenceState.Snapshot snapshot,
                                                   Map<ResourceLocation, Long> displayedBonusTotals) {
        SkillRequirement requirement = requirement(skill, rank, requirementId);
        if (requirement == null) return Component.literal(requirementId.getPath());
        if (requirement instanceof BonusInvestmentRequirement bonus) {
            Component essence = EssenceRegistry.get(bonus.essenceId()).map(EssenceText::essenceShort)
                    .map(Component.class::cast).orElseGet(() -> Component.literal(bonus.essenceId().getPath()));
            Component current = snapshot == null ? Component.literal("—")
                    : Component.literal(Long.toString(displayedBonusTotals.getOrDefault(bonus.essenceId(), 0L)));
            return Component.translatable(requirement.translationKey(), current,
                    Long.toString(bonus.minimumInvestment()), essence);
        }
        if (requirement instanceof PermanentMilestoneRequirement milestone && snapshot != null) {
            ClientEssenceState.MilestoneSnapshot state = snapshot.skillMilestones().get(milestone.milestoneId());
            if (state != null) return Component.literal(state.displayName());
        }
        return Component.translatable(requirement.translationKey());
    }
}
