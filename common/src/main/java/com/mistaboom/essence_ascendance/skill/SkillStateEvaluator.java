package com.mistaboom.essence_ascendance.skill;

import com.mistaboom.essence_ascendance.skill.requirement.BonusInvestmentRequirement;
import com.mistaboom.essence_ascendance.skill.requirement.DiscoveryRequirement;
import com.mistaboom.essence_ascendance.skill.requirement.PermanentMilestoneRequirement;
import com.mistaboom.essence_ascendance.skill.requirement.PlayerAttunementRequirement;
import com.mistaboom.essence_ascendance.skill.requirement.SkillRequirement;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Shared, deterministic skill-state rules for the server and client UI.
 *
 * <p>Purchase eligibility treats prerequisites as permanent ownership gates,
 * including prerequisites staged in the same transaction. Effective-state
 * evaluation separately inherits active branch state: an owned descendant is
 * inactive while an ordinary prerequisite is ineffective. A replacement's
 * direct target prerequisite is the deliberate exception because the
 * replacement suppresses that target's behavior while retaining its progression
 * identity. A replacement suppresses its target only while the replacement is
 * itself owned, selected, and not suspended, so the target resumes whenever the
 * replacement is unselected or suspended. Ordinary automatic skills are active
 * by default; a self-to-self loadout entry is an explicit suppression marker so
 * one automatic descendant can be disabled without deactivating its branch.</p>
 */
public final class SkillStateEvaluator {

    private SkillStateEvaluator() {
    }

    /** Evaluates the validated global catalog in its stable display order. */
    public static Map<ResourceLocation, SkillEvaluationResult> evaluateAll(
            SkillEvaluationContext context
    ) {
        return evaluateAll(SkillRegistry.values(), context);
    }

    /** Evaluates one global-catalog entry while retaining replacement context. */
    public static SkillEvaluationResult evaluate(
            ResourceLocation skillId,
            SkillEvaluationContext context
    ) {
        Objects.requireNonNull(skillId, "Skill ID cannot be null");
        SkillRegistry.require(skillId);
        SkillEvaluationResult result = evaluateAll(context).get(skillId);
        if (result == null) {
            throw new IllegalStateException("Validated skill disappeared: " + skillId);
        }
        return result;
    }

    public static Map<ResourceLocation, SkillEvaluationResult> evaluateAll(
            Collection<SkillDefinition> definitions,
            SkillEvaluationContext context
    ) {
        Objects.requireNonNull(definitions, "Skill definitions cannot be null");
        Objects.requireNonNull(context, "Skill evaluation context cannot be null");

        Map<ResourceLocation, SkillDefinition> byId = new LinkedHashMap<>();
        for (SkillDefinition definition : definitions) {
            Objects.requireNonNull(definition, "Skill definitions cannot contain null");
            if (byId.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException(
                        "Duplicate skill definition ID: " + definition.id()
                );
            }
        }

        EvaluationFrame authoritative = evaluateFrame(
                byId,
                context.authoritativeRanks(),
                context.authoritativeLoadoutSelections(),
                context.authoritativeBonusTotals(),
                context,
                false
        );
        EvaluationFrame projected = evaluateFrame(
                byId,
                context.projectedRanks(),
                context.projectedLoadoutSelections(),
                context.projectedBonusTotals(),
                context,
                true
        );
        EvaluationFrame bonusOnlyProjection = evaluateFrame(
                byId,
                context.authoritativeRanks(),
                context.authoritativeLoadoutSelections(),
                context.projectedBonusTotals(),
                context,
                false
        );

        Map<ResourceLocation, SkillEvaluationResult> results = new LinkedHashMap<>();
        for (SkillDefinition definition : byId.values()) {
            ResourceLocation skillId = definition.id();
            boolean owned = context.authoritativeOwnedSkillIds().contains(skillId);
            boolean projectedOwned = context.projectedOwnedSkillIds().contains(skillId);
            boolean suspended = authoritative.suspended().contains(skillId);
            boolean projectedSuspended = projected.suspended().contains(skillId);
            boolean willSuspend = owned
                    && authoritative.effective().contains(skillId)
                    && !bonusOnlyProjection.effective().contains(skillId)
                    && projected.activationEnabled().contains(skillId)
                    && !projected.effective().contains(skillId)
                    && !projected.replaced().containsKey(skillId);

            results.put(
                    skillId,
                    new SkillEvaluationResult(
                            definition,
                            evaluatePurchaseEligibility(definition, context),
                            context.authoritativeRank(skillId),
                            context.projectedRank(skillId),
                            owned,
                            projectedOwned,
                            context.projectedRank(skillId) > context.authoritativeRank(skillId),
                            authoritative.selected().contains(skillId),
                            projected.selected().contains(skillId),
                            authoritative.effective().contains(skillId),
                            projected.effective().contains(skillId),
                            suspended,
                            projectedSuspended,
                            willSuspend,
                            authoritative.replaced().containsKey(skillId),
                            projected.replaced().containsKey(skillId),
                            authoritative.fallbackActive().contains(skillId),
                            projected.fallbackActive().contains(skillId),
                            authoritative.replaced().getOrDefault(skillId, Set.of()),
                            projected.replaced().getOrDefault(skillId, Set.of()),
                            authoritative.inactivePrerequisites()
                                    .getOrDefault(skillId, Set.of()),
                            projected.inactivePrerequisites()
                                    .getOrDefault(skillId, Set.of())
                    )
            );
        }

        return Map.copyOf(results);
    }

    public static SkillPurchaseEligibility evaluatePurchaseEligibility(
            SkillDefinition definition,
            SkillEvaluationContext context
    ) {
        Objects.requireNonNull(definition, "Skill definition cannot be null");
        Objects.requireNonNull(context, "Skill evaluation context cannot be null");

        return evaluatePurchaseEligibility(definition, context,
                Math.min(definition.maximumRank(), context.authoritativeRank(definition.id()) + 1));
    }

    public static SkillPurchaseEligibility evaluatePurchaseEligibility(
            SkillDefinition definition, SkillEvaluationContext context, int targetRank
    ) {
        if (targetRank < 1 || targetRank > definition.maximumRank())
            throw new IllegalArgumentException("Skill rank outside catalog policy");
        boolean tierSatisfied = tierSatisfied(
                context.currentTierId(),
                definition.requiredTierId(targetRank)
        );
        List<SkillPrerequisiteStatus> prerequisiteStatuses = definition
                .prerequisiteRanks(targetRank)
                .entrySet().stream()
                .map(entry -> new SkillPrerequisiteStatus(
                        entry.getKey(),
                        entry.getValue(),
                        context.authoritativeRank(entry.getKey()),
                        context.projectedRank(entry.getKey())
                ))
                .toList();
        boolean prerequisitesSatisfied = prerequisiteStatuses
                .stream()
                .allMatch(SkillPrerequisiteStatus::projectedOwned);

        List<SkillRequirementStatus> requirementStatuses = definition
                .requirements(targetRank)
                .stream()
                .map(requirement -> requirementStatus(requirement, context))
                .toList();
        boolean requirementsSatisfied = requirementStatuses
                .stream()
                .allMatch(SkillRequirementStatus::projectedSatisfied);

        return new SkillPurchaseEligibility(
                tierSatisfied,
                prerequisitesSatisfied,
                requirementsSatisfied,
                prerequisiteStatuses,
                requirementStatuses
        );
    }

    /**
     * Finds the exact selectable choices needed to activate an owned skill's
     * prerequisite branch. The returned plan includes {@code skillId} when the
     * clicked skill is itself selectable. It never guesses: a missing owned
     * prerequisite, a toggleable ancestor, a cycle, or two required members of
     * the same choice group produces an empty result.
     *
     * <p>A replacement's direct target is deliberately ownership-only, matching
     * effective-state evaluation. For example, selecting Essence Wings does not
     * first try to select Fatigue Flight in the same group.</p>
     */
    public static Optional<Map<ResourceLocation, ResourceLocation>>
    activationSelectionPlan(
            ResourceLocation skillId,
            Set<ResourceLocation> projectedOwnedSkillIds
    ) {
        return activationPlan(
                SkillRegistry.values(),
                skillId,
                projectedOwnedSkillIds,
                Map.of()
        ).map(ActivationPlan::selectableAssignments);
    }

    /** Pure overload used by focused rule tests and future catalog tooling. */
    public static Optional<Map<ResourceLocation, ResourceLocation>>
    activationSelectionPlan(
            Collection<SkillDefinition> definitions,
            ResourceLocation skillId,
            Set<ResourceLocation> projectedOwnedSkillIds
    ) {
        return activationPlan(
                definitions,
                skillId,
                projectedOwnedSkillIds,
                Map.of()
        ).map(ActivationPlan::selectableAssignments);
    }

    /**
     * Finds every loadout mutation needed to activate an owned skill. Selectable
     * ancestors become assignments, while explicit automatic opt-out markers on
     * the clicked skill or any traversed ordinary prerequisite become clears.
     * Nothing is returned unless the complete prerequisite walk is valid.
     */
    public static Optional<ActivationPlan> activationPlan(
            ResourceLocation skillId,
            Set<ResourceLocation> projectedOwnedSkillIds,
            Map<ResourceLocation, ResourceLocation> projectedLoadoutSelections
    ) {
        return activationPlan(
                SkillRegistry.values(),
                skillId,
                projectedOwnedSkillIds,
                projectedLoadoutSelections
        );
    }

    /** Pure overload used by focused rule tests and future catalog tooling. */
    public static Optional<ActivationPlan> activationPlan(
            Collection<SkillDefinition> definitions,
            ResourceLocation skillId,
            Set<ResourceLocation> projectedOwnedSkillIds,
            Map<ResourceLocation, ResourceLocation> projectedLoadoutSelections
    ) {
        Map<ResourceLocation, Integer> ranks = new LinkedHashMap<>();
        projectedOwnedSkillIds.forEach(id -> ranks.put(id, 1));
        return activationPlan(definitions, skillId, ranks, projectedLoadoutSelections);
    }

    public static Optional<ActivationPlan> activationPlan(ResourceLocation skillId,
            Map<ResourceLocation, Integer> projectedRanks,
            Map<ResourceLocation, ResourceLocation> projectedLoadoutSelections) {
        return activationPlan(SkillRegistry.values(), skillId, projectedRanks, projectedLoadoutSelections);
    }

    public static Optional<ActivationPlan> activationPlan(Collection<SkillDefinition> definitions,
            ResourceLocation skillId, Map<ResourceLocation, Integer> projectedRanks,
            Map<ResourceLocation, ResourceLocation> projectedLoadoutSelections) {
        Objects.requireNonNull(definitions, "Skill definitions cannot be null");
        Objects.requireNonNull(skillId, "Skill ID cannot be null");
        Objects.requireNonNull(
                projectedRanks,
                "Projected owned skill IDs cannot be null"
        );
        Objects.requireNonNull(
                projectedLoadoutSelections,
                "Projected loadout selections cannot be null"
        );

        Map<ResourceLocation, SkillDefinition> byId = new LinkedHashMap<>();
        for (SkillDefinition definition : definitions) {
            Objects.requireNonNull(
                    definition,
                    "Skill definitions cannot contain null"
            );
            if (byId.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException(
                        "Duplicate skill definition ID: " + definition.id()
                );
            }
        }

        Map<ResourceLocation, ResourceLocation> selections =
                new LinkedHashMap<>();
        Set<ResourceLocation> automaticSuppressionsToClear =
                new LinkedHashSet<>();
        if (!collectActivationSelections(
                skillId,
                true,
                byId,
                projectedRanks,
                projectedLoadoutSelections,
                selections,
                automaticSuppressionsToClear,
                new LinkedHashSet<>(),
                new LinkedHashSet<>()
        )) {
            return Optional.empty();
        }
        return Optional.of(new ActivationPlan(
                selections,
                automaticSuppressionsToClear
        ));
    }

    private static boolean collectActivationSelections(
            ResourceLocation skillId,
            boolean root,
            Map<ResourceLocation, SkillDefinition> definitions,
            Map<ResourceLocation, Integer> projectedRanks,
            Map<ResourceLocation, ResourceLocation> projectedLoadoutSelections,
            Map<ResourceLocation, ResourceLocation> selections,
            Set<ResourceLocation> automaticSuppressionsToClear,
            Set<ResourceLocation> visiting,
            Set<ResourceLocation> visited
    ) {
        if (visited.contains(skillId)) {
            return true;
        }
        SkillDefinition definition = definitions.get(skillId);
        if (definition == null || projectedRanks.getOrDefault(skillId, 0) <= 0) {
            return false;
        }
        if (!visiting.add(skillId)) {
            return false;
        }

        if (definition.activationPolicy() == SkillActivationPolicy.SELECTABLE) {
            ResourceLocation groupId = definition.choiceGroup();
            if (groupId == null) {
                return false;
            }
            ResourceLocation existing = selections.putIfAbsent(
                    groupId,
                    definition.id()
            );
            if (existing != null && !existing.equals(definition.id())) {
                return false;
            }
        } else if (definition.activationPolicy() == SkillActivationPolicy.AUTOMATIC) {
            if (isAutomaticSuppressed(definition, projectedLoadoutSelections)) {
                automaticSuppressionsToClear.add(definition.id());
            }
        } else if (!root
                && definition.activationPolicy() == SkillActivationPolicy.TOGGLE) {
            /* Activating a separate toggle is a distinct player decision. */
            return false;
        }

        for (var prerequisite : definition.prerequisiteRanks(projectedRanks.get(skillId)).entrySet()) {
            ResourceLocation prerequisiteId = prerequisite.getKey();
            if (projectedRanks.getOrDefault(prerequisiteId, 0) < prerequisite.getValue()) {
                return false;
            }
            if (prerequisiteId.equals(definition.replacementTarget())) {
                continue;
            }
            if (!collectActivationSelections(
                    prerequisiteId,
                    false,
                    definitions,
                    projectedRanks,
                    projectedLoadoutSelections,
                    selections,
                    automaticSuppressionsToClear,
                    visiting,
                    visited
            )) {
                return false;
            }
        }

        visiting.remove(skillId);
        visited.add(skillId);
        return true;
    }

    /** One fully validated, all-or-nothing activation mutation. */
    public record ActivationPlan(
            Map<ResourceLocation, ResourceLocation> selectableAssignments,
            Set<ResourceLocation> automaticSuppressionsToClear
    ) {
        public ActivationPlan {
            selectableAssignments = Map.copyOf(Objects.requireNonNull(
                    selectableAssignments,
                    "Selectable assignments cannot be null"
            ));
            automaticSuppressionsToClear = Set.copyOf(Objects.requireNonNull(
                    automaticSuppressionsToClear,
                    "Automatic suppression clears cannot be null"
            ));
        }
    }

    private static EvaluationFrame evaluateFrame(
            Map<ResourceLocation, SkillDefinition> definitions,
            Map<ResourceLocation, Integer> ownedRanks,
            Map<ResourceLocation, ResourceLocation> selections,
            Map<ResourceLocation, Long> bonusTotals,
            SkillEvaluationContext context,
            boolean projected
    ) {
        Set<ResourceLocation> ownedIds = ownedRanks.keySet();
        Set<ResourceLocation> selected = new LinkedHashSet<>();
        Set<ResourceLocation> automaticActivation = new LinkedHashSet<>();
        Set<ResourceLocation> suppressedAutomatic = new LinkedHashSet<>();
        Set<ResourceLocation> liveSatisfied = new LinkedHashSet<>();
        Set<ResourceLocation> activatableOwnedIds = new LinkedHashSet<>();

        for (SkillDefinition definition : definitions.values()) {
            if (!ownedIds.contains(definition.id())) {
                continue;
            }
            if (isSelected(definition, selections)) {
                selected.add(definition.id());
            }
            if (definition.activationPolicy() == SkillActivationPolicy.AUTOMATIC) {
                if (isAutomaticSuppressed(definition, selections)) {
                    suppressedAutomatic.add(definition.id());
                } else {
                    automaticActivation.add(definition.id());
                }
            }
            if (liveRequirementsSatisfied(definition, ownedRanks.get(definition.id()), bonusTotals, context)) {
                liveSatisfied.add(definition.id());
            }
            if (!projected || projectedRanksEligible(definition, ownedRanks.get(definition.id()), context)) {
                activatableOwnedIds.add(definition.id());
            }
        }

        /*
         * Purchase prerequisites use final ownership, but effective descendants
         * inherit their ordinary prerequisite branch's active state. A replacement
         * may require the exact parent it suppresses; that one direct target is
         * ownership-satisfied so Vector Jump/Double Jump and the flight replacements
         * cannot deadlock each other.
         *
         * Iteration lets ordinary prerequisite chains settle in catalog order while
         * replacement fallback can both remove and restore a target. The validated
         * catalog is acyclic under this activation rule; failure to converge signals
         * invalid future metadata rather than silently choosing a state.
         */
        Set<ResourceLocation> effective = Set.of();
        Set<ResourceLocation> initialActivation = new LinkedHashSet<>(automaticActivation);
        initialActivation.addAll(selected);
        Set<ResourceLocation> activationEnabled = Set.copyOf(initialActivation);
        Set<ResourceLocation> fallbackActive = Set.of();
        Map<ResourceLocation, Set<ResourceLocation>> replaced = Map.of();
        Map<ResourceLocation, Set<ResourceLocation>> inactivePrerequisites = Map.of();
        boolean converged = false;

        for (int pass = 0; pass <= definitions.size() + 2; pass++) {
            Map<ResourceLocation, Set<ResourceLocation>> nextInactivePrerequisites =
                    inactivePrerequisites(definitions, ownedRanks, effective);
            Set<ResourceLocation> otherwiseEffectiveReplacements = new LinkedHashSet<>();
            for (SkillDefinition definition : definitions.values()) {
                if (definition.replacementTarget() != null
                        && activatableOwnedIds.contains(definition.id())
                        && selected.contains(definition.id())
                        && liveSatisfied.contains(definition.id())
                        && nextInactivePrerequisites
                        .getOrDefault(definition.id(), Set.of())
                        .isEmpty()) {
                    otherwiseEffectiveReplacements.add(definition.id());
                }
            }

            Map<ResourceLocation, Set<ResourceLocation>> nextReplaced =
                    replacementTargets(
                            definitions,
                            ownedIds,
                            otherwiseEffectiveReplacements
                    );
            Set<ResourceLocation> nextFallbackActive = replacementFallbackTargets(
                    definitions,
                    ownedIds,
                    selected,
                    selections,
                    nextReplaced
            );
            nextFallbackActive.removeAll(suppressedAutomatic);
            Set<ResourceLocation> nextActivationEnabled =
                    new LinkedHashSet<>(automaticActivation);
            nextActivationEnabled.addAll(selected);
            nextActivationEnabled.addAll(nextFallbackActive);

            Set<ResourceLocation> nextEffective = new LinkedHashSet<>();
            for (SkillDefinition definition : definitions.values()) {
                ResourceLocation skillId = definition.id();
                if (activatableOwnedIds.contains(skillId)
                        && nextActivationEnabled.contains(skillId)
                        && liveSatisfied.contains(skillId)
                        && nextInactivePrerequisites
                        .getOrDefault(skillId, Set.of())
                        .isEmpty()
                        && !nextReplaced.containsKey(skillId)) {
                    nextEffective.add(skillId);
                }
            }

            if (nextEffective.equals(effective)
                    && nextActivationEnabled.equals(activationEnabled)
                    && nextReplaced.equals(replaced)) {
                effective = Set.copyOf(nextEffective);
                activationEnabled = Set.copyOf(nextActivationEnabled);
                fallbackActive = Set.copyOf(nextFallbackActive);
                replaced = immutableSetMap(nextReplaced);
                inactivePrerequisites = immutableSetMap(nextInactivePrerequisites);
                converged = true;
                break;
            }

            effective = Set.copyOf(nextEffective);
            activationEnabled = Set.copyOf(nextActivationEnabled);
            fallbackActive = Set.copyOf(nextFallbackActive);
            replaced = immutableSetMap(nextReplaced);
            inactivePrerequisites = immutableSetMap(nextInactivePrerequisites);
        }

        if (!converged) {
            throw new IllegalStateException(
                    "Skill activation did not converge; check prerequisite/replacement cycles"
            );
        }

        Set<ResourceLocation> suspended = new LinkedHashSet<>();
        for (ResourceLocation skillId : activationEnabled) {
            if (activatableOwnedIds.contains(skillId)
                    && !liveSatisfied.contains(skillId)) {
                suspended.add(skillId);
            }
        }

        return new EvaluationFrame(
                Set.copyOf(selected),
                Set.copyOf(activationEnabled),
                effective,
                Set.copyOf(suspended),
                Set.copyOf(fallbackActive),
                immutableSetMap(replaced),
                immutableSetMap(inactivePrerequisites)
        );
    }

    private static boolean projectedRanksEligible(SkillDefinition definition, int targetRank,
                                                   SkillEvaluationContext context) {
        int currentRank = context.authoritativeRank(definition.id());
        for (int rank = currentRank + 1; rank <= targetRank; rank++) {
            if (rank > definition.maximumRank() || !evaluatePurchaseEligibility(definition, context, rank).satisfied())
                return false;
        }
        return true;
    }

    private static Map<ResourceLocation, Set<ResourceLocation>> inactivePrerequisites(
            Map<ResourceLocation, SkillDefinition> definitions,
            Map<ResourceLocation, Integer> ownedRanks,
            Set<ResourceLocation> effectiveIds
    ) {
        Map<ResourceLocation, Set<ResourceLocation>> inactive = new LinkedHashMap<>();
        for (SkillDefinition definition : definitions.values()) {
            if (!ownedRanks.containsKey(definition.id())) {
                continue;
            }
            for (var prerequisite : definition.prerequisiteRanks(ownedRanks.get(definition.id())).entrySet()) {
                ResourceLocation prerequisiteId = prerequisite.getKey();
                boolean replacementTarget = prerequisiteId.equals(
                        definition.replacementTarget()
                );
                if (ownedRanks.getOrDefault(prerequisiteId, 0) < prerequisite.getValue()
                        || (!replacementTarget && !effectiveIds.contains(prerequisiteId))) {
                    inactive.computeIfAbsent(
                            definition.id(),
                            ignored -> new LinkedHashSet<>()
                    ).add(prerequisiteId);
                }
            }
        }
        return inactive;
    }

    private static Map<ResourceLocation, Set<ResourceLocation>> replacementTargets(
            Map<ResourceLocation, SkillDefinition> definitions,
            Set<ResourceLocation> ownedIds,
            Set<ResourceLocation> effectiveReplacementIds
    ) {
        Map<ResourceLocation, Set<ResourceLocation>> replaced = new LinkedHashMap<>();
        for (ResourceLocation replacementId : effectiveReplacementIds) {
            SkillDefinition replacement = definitions.get(replacementId);
            if (replacement == null
                    || replacement.replacementTarget() == null
                    || !ownedIds.contains(replacement.replacementTarget())) {
                continue;
            }
            replaced.computeIfAbsent(
                    replacement.replacementTarget(),
                    ignored -> new LinkedHashSet<>()
            ).add(replacementId);
        }
        return replaced;
    }

    private static Set<ResourceLocation> replacementFallbackTargets(
            Map<ResourceLocation, SkillDefinition> definitions,
            Set<ResourceLocation> ownedIds,
            Set<ResourceLocation> selectedIds,
            Map<ResourceLocation, ResourceLocation> selections,
            Map<ResourceLocation, Set<ResourceLocation>> replaced
    ) {
        Set<ResourceLocation> fallback = new LinkedHashSet<>();
        for (SkillDefinition replacement : definitions.values()) {
            if (replacement.replacementTarget() == null
                    || !ownedIds.contains(replacement.id())
                    || !ownedIds.contains(replacement.replacementTarget())
                    || replaced.containsKey(replacement.replacementTarget())) {
                continue;
            }

            /*
             * A selected but suspended/branch-blocked replacement restores its
             * target. When the replacement group permits no selection, an owned
             * target also resumes so Vector Jump can fall back to Double Jump.
             * Selecting a different member (for example Charged Jump) deliberately
             * keeps that target branch inactive.
             */
            if (selectedIds.contains(replacement.id())
                    || (replacement.choiceGroup() != null
                    && !selections.containsKey(replacement.choiceGroup()))) {
                fallback.add(replacement.replacementTarget());
            }
        }
        return fallback;
    }

    private static Map<ResourceLocation, Set<ResourceLocation>> immutableSetMap(
            Map<ResourceLocation, Set<ResourceLocation>> source
    ) {
        Map<ResourceLocation, Set<ResourceLocation>> copy = new LinkedHashMap<>();
        source.forEach((id, values) -> copy.put(id, Set.copyOf(values)));
        return Map.copyOf(copy);
    }

    private static boolean isSelected(
            SkillDefinition definition,
            Map<ResourceLocation, ResourceLocation> selections
    ) {
        return switch (definition.activationPolicy()) {
            case AUTOMATIC -> false;
            case SELECTABLE -> definition.choiceGroup() != null
                    && definition.id().equals(selections.get(definition.choiceGroup()));
            case TOGGLE -> definition.id().equals(selections.get(definition.id()));
        };
    }

    /**
     * Automatic skills are active by default; a self-selection explicitly suppresses them.
     */
    public static boolean isAutomaticSuppressed(
            SkillDefinition definition,
            Map<ResourceLocation, ResourceLocation> selections
    ) {
        Objects.requireNonNull(definition, "Skill definition cannot be null");
        Objects.requireNonNull(selections, "Loadout selections cannot be null");
        return definition.activationPolicy() == SkillActivationPolicy.AUTOMATIC
                && definition.id().equals(selections.get(definition.id()));
    }

    private static boolean liveRequirementsSatisfied(
            SkillDefinition definition,
            int rank,
            Map<ResourceLocation, Long> bonusTotals,
            SkillEvaluationContext context
    ) {
        for (SkillRequirement requirement : definition.requirements(rank)) {
            if (requirement.live()
                    && !requirementSatisfied(requirement, bonusTotals, context)) {
                return false;
            }
        }
        return true;
    }

    private static SkillRequirementStatus requirementStatus(
            SkillRequirement requirement,
            SkillEvaluationContext context
    ) {
        return new SkillRequirementStatus(
                requirement.id(),
                requirement.kind(),
                requirement.translationKey(),
                requirement.live(),
                requirementSatisfied(
                        requirement,
                        context.authoritativeBonusTotals(),
                        context
                ),
                requirementSatisfied(
                        requirement,
                        context.projectedBonusTotals(),
                        context
                )
        );
    }

    private static boolean requirementSatisfied(
            SkillRequirement requirement,
            Map<ResourceLocation, Long> bonusTotals,
            SkillEvaluationContext context
    ) {
        if (requirement instanceof PlayerAttunementRequirement attunement) {
            return context.completedAttunements().contains(attunement.attunementId());
        }
        if (requirement instanceof PermanentMilestoneRequirement milestone) {
            return context.completedMilestones().contains(milestone.milestoneId());
        }
        if (requirement instanceof BonusInvestmentRequirement bonus) {
            return bonusTotals.getOrDefault(bonus.essenceId(), 0L)
                    >= bonus.minimumInvestment();
        }
        if (requirement instanceof DiscoveryRequirement discovery) {
            return context.completedDiscoveries().contains(discovery.discoveryId());
        }

        /* Future requirement implementations fail closed until handled here. */
        return false;
    }

    private static boolean tierSatisfied(
            ResourceLocation currentTierId,
            ResourceLocation requiredTierId
    ) {
        AscendanceTierDefinition current = AscendanceTierRegistry
                .get(currentTierId)
                .orElse(null);
        AscendanceTierDefinition required = AscendanceTierRegistry
                .get(requiredTierId)
                .orElse(null);
        return current != null
                && required != null
                && current.order() >= required.order();
    }

    private record EvaluationFrame(
            Set<ResourceLocation> selected,
            Set<ResourceLocation> activationEnabled,
            Set<ResourceLocation> effective,
            Set<ResourceLocation> suspended,
            Set<ResourceLocation> fallbackActive,
            Map<ResourceLocation, Set<ResourceLocation>> replaced,
            Map<ResourceLocation, Set<ResourceLocation>> inactivePrerequisites
    ) {
    }
}
