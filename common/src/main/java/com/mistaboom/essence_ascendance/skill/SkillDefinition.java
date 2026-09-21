package com.mistaboom.essence_ascendance.skill;

import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.skill.requirement.SkillRequirement;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable catalog entry. No gameplay-effect handler is stored here. */
public record SkillDefinition(
        ResourceLocation id,
        ResourceLocation essenceId,
        String nameTranslationKey,
        String descriptionTranslationKey,
        ResourceLocation requiredTierId,
        SkillCostBand costBand,
        List<ResourceLocation> prerequisites,
        List<SkillRequirement> requirements,
        ResourceLocation choiceGroup,
        ResourceLocation replacementTarget,
        SkillActivationPolicy activationPolicy,
        int displayOrder,
        SkillLayoutHint layoutHint,
        SkillRankPolicy rankPolicy
) {
    public SkillDefinition {
        Objects.requireNonNull(id, "Skill ID cannot be null");
        Objects.requireNonNull(essenceId, "Skill Essence ID cannot be null");
        Objects.requireNonNull(requiredTierId, "Required tier ID cannot be null");
        Objects.requireNonNull(costBand, "Skill cost band cannot be null");
        Objects.requireNonNull(activationPolicy, "Activation policy cannot be null");
        Objects.requireNonNull(layoutHint, "Layout hint cannot be null");
        Objects.requireNonNull(rankPolicy, "Rank policy cannot be null");

        if (nameTranslationKey == null || nameTranslationKey.isBlank()) {
            throw new IllegalArgumentException("Skill name translation key cannot be blank");
        }
        if (descriptionTranslationKey == null || descriptionTranslationKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Skill description translation key cannot be blank"
            );
        }
        if (displayOrder < 0) {
            throw new IllegalArgumentException("Skill display order cannot be negative");
        }

        prerequisites = List.copyOf(
                Objects.requireNonNull(prerequisites, "Prerequisites cannot be null")
        );
        requirements = List.copyOf(
                Objects.requireNonNull(requirements, "Requirements cannot be null")
        );

        if (prerequisites.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Prerequisites cannot contain null IDs");
        }
        if (requirements.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Requirements cannot contain null entries");
        }

        Set<ResourceLocation> requirementIds = new HashSet<>();
        for (SkillRequirement requirement : requirements) {
            if (!requirementIds.add(requirement.id())) throw new IllegalArgumentException("Duplicate skill requirement ID");
        }

        Set<ResourceLocation> distinctPrerequisites = new HashSet<>(prerequisites);
        if (distinctPrerequisites.size() != prerequisites.size()) {
            throw new IllegalArgumentException(
                    "Skill " + id + " declares a prerequisite more than once"
            );
        }
    }

    public Optional<ResourceLocation> choiceGroupId() {
        return Optional.ofNullable(choiceGroup);
    }

    public Optional<ResourceLocation> replacementTargetId() {
        return Optional.ofNullable(replacementTarget);
    }

    public boolean isReplacement() {
        return replacementTarget != null;
    }

    public ProgressionRequirements.Skill progressionRequirements() { return ProgressionRequirements.skill(id); }

    public boolean hasLiveRequirements() {
        return requirements.stream().anyMatch(SkillRequirement::live);
    }

    public long cost(BalanceProfileDefinition profile) {
        return cost(profile, 1);
    }

    public long cost(BalanceProfileDefinition profile, int targetRank) {
        if (targetRank < 1 || targetRank > maximumRank()) throw new IllegalArgumentException("Skill maximum rank exceeded");
        return com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime.require(id.toString(), targetRank).cost();
    }

    public int maximumRank() {
        if (rankPolicy.maximumRank() > 0) return rankPolicy.maximumRank();
        var resolved = com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime.snapshot().get(id.toString());
        return resolved == null ? 1 : resolved.maximumRank();
    }

    /** Later ranks inherit earlier gates unless the same requirement identity is explicitly refined. */
    public ResourceLocation requiredTierId(int rank) {
        ResourceLocation result = requiredTierId;
        int order = com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry.get(result).orElseThrow().order();
        for (int value = 1; value <= rank; value++) {
            var gates = rankPolicy.rankGates().get(value);
            if (gates == null || gates.requiredTierId() == null) continue;
            int candidateOrder = com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry.get(gates.requiredTierId()).orElseThrow().order();
            if (candidateOrder > order) { result = gates.requiredTierId(); order = candidateOrder; }
        }
        return result;
    }

    public java.util.Map<ResourceLocation, Integer> prerequisiteRanks(int rank) {
        java.util.Map<ResourceLocation, Integer> values = new java.util.TreeMap<>();
        prerequisites.forEach(id -> values.put(id, 1));
        for (int current = 1; current <= rank; current++) {
            var gates = rankPolicy.rankGates().get(current);
            if (gates != null) gates.prerequisites().forEach((id, required) -> values.merge(id, required, Math::max));
        }
        return java.util.Collections.unmodifiableMap(values);
    }

    public List<SkillRequirement> requirements(int rank) {
        java.util.Map<ResourceLocation, SkillRequirement> values = new java.util.LinkedHashMap<>();
        requirements.forEach(requirement -> values.put(requirement.id(), requirement));
        for (int current = 1; current <= rank; current++) {
            var gates = rankPolicy.rankGates().get(current);
            if (gates != null) gates.requirements().forEach(requirement -> values.put(requirement.id(), requirement));
        }
        return List.copyOf(values.values());
    }
}
