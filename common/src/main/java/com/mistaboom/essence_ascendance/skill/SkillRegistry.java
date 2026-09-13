package com.mistaboom.essence_ascendance.skill;

import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.MilestoneRegistry;
import com.mistaboom.essence_ascendance.skill.requirement.BonusInvestmentRequirement;
import com.mistaboom.essence_ascendance.skill.requirement.DiscoveryRequirement;
import com.mistaboom.essence_ascendance.skill.requirement.PermanentMilestoneRequirement;
import com.mistaboom.essence_ascendance.skill.requirement.PlayerAttunementRequirement;
import com.mistaboom.essence_ascendance.skill.requirement.SkillRequirement;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable runtime view of the curated skill catalog. Constructing this
 * class validates catalog size, identities, references, choice groups,
 * replacements, prerequisite ordering, and cycles, and cost arithmetic.
 */
public final class SkillRegistry {

    public static final int CATALOG_VERSION = 2;

    private static final Catalog CATALOG = createCatalog();

    private SkillRegistry() {
    }

    public static Optional<SkillDefinition> get(ResourceLocation skillId) {
        return Optional.ofNullable(CATALOG.skillsById().get(skillId));
    }

    public static SkillDefinition require(ResourceLocation skillId) {
        Objects.requireNonNull(skillId, "Skill ID cannot be null");
        SkillDefinition definition = CATALOG.skillsById().get(skillId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown skill ID: " + skillId);
        }
        return definition;
    }

    public static List<SkillDefinition> values() {
        return CATALOG.orderedSkills();
    }

    /** Returns the category's skills in stable display order. */
    public static List<SkillDefinition> values(ResourceLocation essenceId) {
        if (essenceId == null) {
            return List.of();
        }
        return CATALOG.skillsByEssence().getOrDefault(essenceId, List.of());
    }

    public static Optional<SkillChoiceGroup> choiceGroup(ResourceLocation groupId) {
        return Optional.ofNullable(CATALOG.choiceGroupsById().get(groupId));
    }

    public static Collection<SkillChoiceGroup> choiceGroups() {
        return CATALOG.choiceGroupsById().values();
    }

    public static Set<ResourceLocation> referencedAttunementIds() {
        return CATALOG.attunementIds();
    }

    /**
     * Includes provisional Attunement identities even when no current skill
     * definition is gated by them. This lets permission-gated development
     * tools validate IDs without freezing candidate sacrifice mappings.
     */
    public static Set<ResourceLocation> knownAttunementIds() {
        return Set.copyOf(SkillAttunements.values());
    }

    public static Set<ResourceLocation> referencedPermanentMilestoneIds() {
        return CATALOG.permanentMilestoneIds();
    }

    public static Set<ResourceLocation> referencedDiscoveryIds() {
        return CATALOG.discoveryIds();
    }

    public static long cost(
            ResourceLocation skillId,
            BalanceProfileDefinition profile
    ) {
        return require(skillId).cost(profile);
    }

    /**
     * Purchase prerequisites use final ownership, not current effectiveness.
     * Pass the union of already-owned and staged-purchase IDs here.
     */
    public static boolean prerequisitesSatisfied(
            SkillDefinition skill,
            Set<ResourceLocation> finalOwnedSkillIds
    ) {
        return missingPrerequisites(skill, finalOwnedSkillIds).isEmpty();
    }

    public static List<ResourceLocation> missingPrerequisites(
            SkillDefinition skill,
            Set<ResourceLocation> finalOwnedSkillIds
    ) {
        Objects.requireNonNull(skill, "Skill cannot be null");
        Objects.requireNonNull(finalOwnedSkillIds, "Final owned-skill IDs cannot be null");
        return skill.prerequisites()
                .stream()
                .filter(id -> !finalOwnedSkillIds.contains(id))
                .toList();
    }

    /**
     * Returns requested skills in prerequisite-first order. Missing catalog
     * IDs are rejected. Prerequisites outside the requested set are omitted.
     */
    public static List<SkillDefinition> topologicalOrder(
            Collection<ResourceLocation> skillIds
    ) {
        Objects.requireNonNull(skillIds, "Skill IDs cannot be null");
        Set<ResourceLocation> requested = new LinkedHashSet<>(skillIds);
        List<SkillDefinition> ordered = new ArrayList<>(requested.size());
        Set<ResourceLocation> visited = new HashSet<>();

        for (ResourceLocation id : requested) {
            require(id);
            addTopologically(id, requested, visited, ordered);
        }

        return List.copyOf(ordered);
    }

    public static int size() {
        return CATALOG.orderedSkills().size();
    }

    public static int catalogVersion() {
        return CATALOG_VERSION;
    }

    /** Catalog construction already ran every validation; this is an explicit startup hook. */
    public static void validate() {
        if (CATALOG.orderedSkills().isEmpty()) {
            throw new IllegalStateException("Skill catalog validation was not completed");
        }
    }

    private static Catalog createCatalog() {
        Map<ResourceLocation, SkillDefinition> byId = new LinkedHashMap<>();
        Map<ResourceLocation, List<SkillDefinition>> mutableByEssence = new LinkedHashMap<>();

        for (EssenceDefinition essence : EssenceTypes.ORDERED) {
            mutableByEssence.put(essence.id(), new ArrayList<>());
        }

        for (SkillDefinition skill : Skills.definitions()) {
            SkillDefinition previous = byId.put(skill.id(), skill);
            if (previous != null) {
                throw invalid("Duplicate skill ID " + skill.id());
            }
            if (EssenceRegistry.get(skill.essenceId()).isEmpty()
                    || !mutableByEssence.containsKey(skill.essenceId())) {
                throw invalid(
                        "Skill " + skill.id() + " uses unknown/non-core Essence "
                                + skill.essenceId()
                );
            }
            if (AscendanceTierRegistry.get(skill.requiredTierId()).isEmpty()) {
                throw invalid(
                        "Skill " + skill.id() + " uses unknown required tier "
                                + skill.requiredTierId()
                );
            }
            mutableByEssence.get(skill.essenceId()).add(skill);
        }

        Map<ResourceLocation, List<SkillDefinition>> byEssence = new LinkedHashMap<>();
        for (EssenceDefinition essence : EssenceTypes.ORDERED) {
            List<SkillDefinition> definitions = mutableByEssence.get(essence.id());
            definitions.sort(java.util.Comparator.comparingInt(SkillDefinition::displayOrder));
            validateCategory(essence.id(), definitions);
            byEssence.put(essence.id(), List.copyOf(definitions));
        }

        for (SkillDefinition skill : byId.values()) {
            for (int rank = 1; rank <= skill.maximumRank(); rank++) {
                if (AscendanceTierRegistry.get(skill.requiredTierId(rank)).isEmpty())
                    throw invalid("Unknown rank tier for " + skill.id());
                for (var prerequisite : skill.prerequisiteRanks(rank).entrySet()) {
                    SkillDefinition parent = byId.get(prerequisite.getKey());
                    if (parent == null || prerequisite.getValue() > parent.maximumRank())
                        throw invalid("Unreachable prerequisite rank for " + skill.id());
                }
            }
        }
        validateSkillReferences(byId);
        validatePrerequisiteGraph(byId);
        validateReplacementGraph(byId);

        Map<ResourceLocation, SkillChoiceGroup> choiceGroups =
                validateChoiceGroups(byId, SkillChoiceGroups.definitions());
        validateReplacementChoices(byId);
        validateCostBands();

        List<SkillDefinition> ordered = new ArrayList<>(byId.size());
        for (EssenceDefinition essence : EssenceTypes.ORDERED) {
            ordered.addAll(byEssence.get(essence.id()));
        }

        Set<ResourceLocation> attunements = new LinkedHashSet<>();
        Set<ResourceLocation> permanentMilestones = new LinkedHashSet<>();
        Set<ResourceLocation> discoveries = new LinkedHashSet<>();
        for (SkillDefinition skill : ordered) {
            for (SkillRequirement requirement : allRankRequirements(skill)) {
                if (requirement instanceof PlayerAttunementRequirement attunement) {
                    attunements.add(attunement.attunementId());
                } else if (requirement instanceof PermanentMilestoneRequirement milestone) {
                    permanentMilestones.add(milestone.milestoneId());
                } else if (requirement instanceof DiscoveryRequirement discovery) {
                    discoveries.add(discovery.discoveryId());
                }
            }
        }

        return new Catalog(
                Collections.unmodifiableMap(byId),
                Collections.unmodifiableMap(byEssence),
                List.copyOf(ordered),
                Collections.unmodifiableMap(choiceGroups),
                Collections.unmodifiableSet(attunements),
                Collections.unmodifiableSet(permanentMilestones),
                Collections.unmodifiableSet(discoveries)
        );
    }

    private static void validateCategory(
            ResourceLocation essenceId,
            List<SkillDefinition> definitions
    ) {
        Set<Integer> displayOrders = new HashSet<>();
        Set<String> nameKeys = new HashSet<>();
        Set<String> descriptionKeys = new HashSet<>();

        for (SkillDefinition skill : definitions) {
            if (!displayOrders.add(skill.displayOrder())) {
                throw invalid(
                        "Duplicate display order " + skill.displayOrder()
                                + " in Essence category " + essenceId
                );
            }
            if (!nameKeys.add(skill.nameTranslationKey())) {
                throw invalid("Duplicate skill name translation key " + skill.nameTranslationKey());
            }
            if (!descriptionKeys.add(skill.descriptionTranslationKey())) {
                throw invalid(
                        "Duplicate skill description translation key "
                                + skill.descriptionTranslationKey()
                );
            }
        }
    }

    private static List<SkillRequirement> allRankRequirements(SkillDefinition skill) {
        java.util.LinkedHashSet<SkillRequirement> requirements = new java.util.LinkedHashSet<>();
        for (int rank = 1; rank <= skill.maximumRank(); rank++) requirements.addAll(skill.requirements(rank));
        return List.copyOf(requirements);
    }

    private static List<ResourceLocation> allPrerequisites(SkillDefinition skill) {
        java.util.LinkedHashSet<ResourceLocation> ids = new java.util.LinkedHashSet<>(skill.prerequisites());
        for (int rank = 1; rank <= skill.maximumRank(); rank++) ids.addAll(skill.prerequisiteRanks(rank).keySet());
        return List.copyOf(ids);
    }

    private static void validateSkillReferences(Map<ResourceLocation, SkillDefinition> byId) {
        for (SkillDefinition skill : byId.values()) {
            for (ResourceLocation prerequisiteId : allPrerequisites(skill)) {
                SkillDefinition prerequisite = byId.get(prerequisiteId);
                if (prerequisite == null) {
                    throw invalid(
                            "Skill " + skill.id() + " has unknown prerequisite "
                                    + prerequisiteId
                    );
                }
                if (skill.id().equals(prerequisiteId)) {
                    throw invalid("Skill " + skill.id() + " requires itself");
                }
                if (!skill.essenceId().equals(prerequisite.essenceId())) {
                    throw invalid(
                            "Skill " + skill.id() + " has cross-Essence prerequisite "
                                    + prerequisiteId
                    );
                }

                int skillTier = tierOrder(skill.requiredTierId());
                int prerequisiteTier = tierOrder(prerequisite.requiredTierId());
                if (prerequisiteTier >= skillTier) {
                    throw invalid(
                            "Skill " + skill.id() + " must be later than prerequisite "
                                    + prerequisiteId
                    );
                }
            }

            if (skill.replacementTargetId().isPresent()) {
                ResourceLocation targetId = skill.replacementTargetId().orElseThrow();
                SkillDefinition target = byId.get(targetId);
                if (target == null) {
                    throw invalid(
                            "Skill " + skill.id() + " replaces unknown skill " + targetId
                    );
                }
                if (skill.id().equals(targetId)) {
                    throw invalid("Skill " + skill.id() + " replaces itself");
                }
                if (!skill.essenceId().equals(target.essenceId())) {
                    throw invalid(
                            "Skill " + skill.id() + " replaces a skill in another Essence"
                    );
                }
                if (!skill.prerequisites().contains(targetId)) {
                    throw invalid(
                            "Replacement " + skill.id()
                                    + " must retain its target as an owned prerequisite"
                    );
                }
                if (skill.activationPolicy() != SkillActivationPolicy.SELECTABLE) {
                    throw invalid("Replacement " + skill.id() + " must be selectable");
                }
            }

            for (SkillRequirement requirement : allRankRequirements(skill)) {
                if (requirement instanceof BonusInvestmentRequirement bonus) {
                    if (EssenceRegistry.get(bonus.essenceId()).isEmpty()) {
                        throw invalid(
                                "Skill " + skill.id()
                                        + " has Bonus requirement for unknown Essence "
                                        + bonus.essenceId()
                        );
                    }
                    if (bonus.minimumInvestment() <= 0L) {
                        throw invalid(
                                "Skill " + skill.id()
                                        + " has a non-positive Bonus requirement"
                        );
                    }
                } else if (requirement instanceof PermanentMilestoneRequirement milestone
                        && MilestoneRegistry.get(milestone.milestoneId()).isEmpty()) {
                    throw invalid(
                            "Skill " + skill.id()
                                    + " references an unregistered milestone "
                                    + milestone.milestoneId()
                    );
                }
            }

            if (skill.choiceGroupId().isPresent()
                    && skill.activationPolicy() != SkillActivationPolicy.SELECTABLE) {
                throw invalid(
                        "Choice-group skill " + skill.id() + " must be selectable"
                );
            }
        }
    }

    private static Map<ResourceLocation, SkillChoiceGroup> validateChoiceGroups(
            Map<ResourceLocation, SkillDefinition> byId,
            List<SkillChoiceGroup> definitions
    ) {
        Map<ResourceLocation, SkillChoiceGroup> groups = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> membership = new HashMap<>();

        for (SkillChoiceGroup group : definitions) {
            if (byId.containsKey(group.id())) {
                throw invalid(
                        "Choice-group ID collides with skill ID " + group.id()
                );
            }
            SkillChoiceGroup previous = groups.put(group.id(), group);
            if (previous != null) {
                throw invalid("Duplicate choice-group ID " + group.id());
            }

            Set<ResourceLocation> distinctMembers = new HashSet<>();
            ResourceLocation essenceId = null;
            for (ResourceLocation memberId : group.memberIds()) {
                if (!distinctMembers.add(memberId)) {
                    throw invalid(
                            "Choice group " + group.id() + " repeats member " + memberId
                    );
                }
                SkillDefinition member = byId.get(memberId);
                if (member == null) {
                    throw invalid(
                            "Choice group " + group.id() + " has unknown member " + memberId
                    );
                }
                if (!member.choiceGroupId().orElseThrow(() -> invalid(
                        "Choice-group member " + memberId + " does not declare its group"
                )).equals(group.id())) {
                    throw invalid(
                            "Choice-group member " + memberId + " declares a different group"
                    );
                }
                if (essenceId == null) {
                    essenceId = member.essenceId();
                } else if (!essenceId.equals(member.essenceId())) {
                    throw invalid("Choice group " + group.id() + " spans Essences");
                }

                ResourceLocation existingGroup = membership.put(memberId, group.id());
                if (existingGroup != null) {
                    throw invalid(
                            "Skill " + memberId + " belongs to multiple choice groups"
                    );
                }
            }
        }

        for (SkillDefinition skill : byId.values()) {
            Optional<ResourceLocation> declaredGroup = skill.choiceGroupId();
            if (declaredGroup.isEmpty()) {
                continue;
            }
            SkillChoiceGroup group = groups.get(declaredGroup.orElseThrow());
            if (group == null || !group.memberIds().contains(skill.id())) {
                throw invalid(
                        "Skill " + skill.id() + " references invalid choice group "
                                + declaredGroup.orElseThrow()
                );
            }
        }

        return groups;
    }

    private static void validateReplacementChoices(
            Map<ResourceLocation, SkillDefinition> byId
    ) {
        Map<ResourceLocation, List<SkillDefinition>> byTarget = new HashMap<>();
        for (SkillDefinition skill : byId.values()) {
            skill.replacementTargetId().ifPresent(target ->
                    byTarget.computeIfAbsent(target, ignored -> new ArrayList<>()).add(skill)
            );
        }

        for (Map.Entry<ResourceLocation, List<SkillDefinition>> entry : byTarget.entrySet()) {
            List<SkillDefinition> replacements = entry.getValue();
            if (replacements.size() < 2) {
                continue;
            }
            ResourceLocation groupId = replacements.getFirst()
                    .choiceGroupId()
                    .orElseThrow(() -> invalid(
                            "Multiple replacements for " + entry.getKey()
                                    + " must share a choice group"
                    ));
            for (SkillDefinition replacement : replacements) {
                if (!replacement.choiceGroupId().orElseThrow(() -> invalid(
                        "Multiple replacements for " + entry.getKey()
                                + " must share a choice group"
                )).equals(groupId)) {
                    throw invalid(
                            "Replacements for " + entry.getKey()
                                    + " use different choice groups"
                    );
                }
            }
        }
    }

    private static void validatePrerequisiteGraph(
            Map<ResourceLocation, SkillDefinition> byId
    ) {
        validateAcyclicGraph(
                byId,
                SkillRegistry::allPrerequisites,
                "prerequisite"
        );
    }

    private static void validateReplacementGraph(
            Map<ResourceLocation, SkillDefinition> byId
    ) {
        validateAcyclicGraph(
                byId,
                skill -> skill.replacementTargetId().stream().toList(),
                "replacement"
        );
    }

    private static void validateAcyclicGraph(
            Map<ResourceLocation, SkillDefinition> byId,
            java.util.function.Function<SkillDefinition, List<ResourceLocation>> edges,
            String label
    ) {
        Set<ResourceLocation> visiting = new HashSet<>();
        Set<ResourceLocation> visited = new HashSet<>();

        for (SkillDefinition skill : byId.values()) {
            visitGraph(skill.id(), byId, edges, label, visiting, visited);
        }
    }

    private static void visitGraph(
            ResourceLocation id,
            Map<ResourceLocation, SkillDefinition> byId,
            java.util.function.Function<SkillDefinition, List<ResourceLocation>> edges,
            String label,
            Set<ResourceLocation> visiting,
            Set<ResourceLocation> visited
    ) {
        if (visited.contains(id)) {
            return;
        }
        if (!visiting.add(id)) {
            throw invalid("Detected " + label + " cycle at skill " + id);
        }

        for (ResourceLocation target : edges.apply(byId.get(id))) {
            visitGraph(target, byId, edges, label, visiting, visited);
        }

        visiting.remove(id);
        visited.add(id);
    }

    private static void validateCostBands() {
        long[] caps = {10_000L, 100_000L, 1_000_000L, 10_000_000L, 100_000_000L};
        long[][] expected = {
                {500L, 5_000L, 50_000L, 500_000L, 5_000_000L},
                {1_000L, 10_000L, 100_000L, 1_000_000L, 10_000_000L},
                {2_000L, 20_000L, 200_000L, 2_000_000L, 20_000_000L}
        };

        SkillCostBand[] bands = SkillCostBand.values();
        for (int bandIndex = 0; bandIndex < bands.length; bandIndex++) {
            for (int tierIndex = 0; tierIndex < caps.length; tierIndex++) {
                long actual = bands[bandIndex].cost(caps[tierIndex]);
                if (actual != expected[bandIndex][tierIndex]) {
                    throw invalid(
                            "Unexpected " + bands[bandIndex] + " cost " + actual
                                    + " for cap " + caps[tierIndex]
                    );
                }
            }
        }

        if (SkillCostBand.FOUNDATION.cost(1L) != 1L
                || SkillCostBand.ADVANCED.cost(11L) != 2L
                || SkillCostBand.KEYSTONE.cost(Long.MAX_VALUE) <= 0L) {
            throw invalid("Skill cost rounding or overflow validation failed");
        }
    }

    private static int tierOrder(ResourceLocation tierId) {
        return AscendanceTierRegistry.get(tierId)
                .map(AscendanceTierDefinition::order)
                .orElseThrow(() -> invalid("Unknown Ascendance tier " + tierId));
    }

    private static void addTopologically(
            ResourceLocation id,
            Set<ResourceLocation> requested,
            Set<ResourceLocation> visited,
            List<SkillDefinition> ordered
    ) {
        if (!visited.add(id)) {
            return;
        }
        SkillDefinition skill = require(id);
        for (ResourceLocation prerequisite : allPrerequisites(skill)) {
            if (requested.contains(prerequisite)) {
                addTopologically(prerequisite, requested, visited, ordered);
            }
        }
        ordered.add(skill);
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("Invalid Essence Ascendance skill catalog: " + message);
    }

    private record Catalog(
            Map<ResourceLocation, SkillDefinition> skillsById,
            Map<ResourceLocation, List<SkillDefinition>> skillsByEssence,
            List<SkillDefinition> orderedSkills,
            Map<ResourceLocation, SkillChoiceGroup> choiceGroupsById,
            Set<ResourceLocation> attunementIds,
            Set<ResourceLocation> permanentMilestoneIds,
            Set<ResourceLocation> discoveryIds
    ) {
    }
}
