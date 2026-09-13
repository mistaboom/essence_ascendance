package com.mistaboom.essence_ascendance.skill;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, side-neutral inputs used to evaluate the skill catalog.
 *
 * <p>The authoritative fields describe the last committed player state. The
 * projected fields describe the complete Nexus draft after all staged Bonus,
 * purchase, and loadout changes have been applied. Skill purchases are kept
 * separate from Bonus totals: only committed/projected stat investment belongs
 * in the two Bonus maps.</p>
 */
public record SkillEvaluationContext(
        ResourceLocation currentTierId,
        Map<ResourceLocation, Integer> authoritativeRanks,
        Map<ResourceLocation, Integer> projectedRanks,
        Map<ResourceLocation, ResourceLocation> authoritativeLoadoutSelections,
        Map<ResourceLocation, ResourceLocation> projectedLoadoutSelections,
        Set<ResourceLocation> completedMilestones,
        Set<ResourceLocation> completedDiscoveries,
        Map<ResourceLocation, Long> authoritativeBonusTotals,
        Map<ResourceLocation, Long> projectedBonusTotals
) {
    public SkillEvaluationContext {
        Objects.requireNonNull(currentTierId, "Current tier ID cannot be null");
        authoritativeRanks = copyRanks(authoritativeRanks);
        projectedRanks = copyRanks(projectedRanks);
        authoritativeLoadoutSelections = Map.copyOf(Objects.requireNonNull(
                authoritativeLoadoutSelections,
                "Authoritative loadout selections cannot be null"
        ));
        projectedLoadoutSelections = Map.copyOf(Objects.requireNonNull(
                projectedLoadoutSelections,
                "Projected loadout selections cannot be null"
        ));
        completedMilestones = Set.copyOf(Objects.requireNonNull(
                completedMilestones,
                "Completed milestones cannot be null"
        ));
        completedDiscoveries = Set.copyOf(Objects.requireNonNull(
                completedDiscoveries,
                "Completed discoveries cannot be null"
        ));
        authoritativeBonusTotals = copyNonNegativeTotals(
                authoritativeBonusTotals,
                "Authoritative Bonus totals"
        );
        projectedBonusTotals = copyNonNegativeTotals(
                projectedBonusTotals,
                "Projected Bonus totals"
        );


    }

    /**
     * Builds a no-draft context. This is convenient for runtime gameplay
     * consumers that only need the current effective state.
     */
    public static SkillEvaluationContext committed(
            ResourceLocation currentTierId,
            Map<ResourceLocation, Integer> ownedRanks,
            Map<ResourceLocation, ResourceLocation> loadoutSelections,
            Set<ResourceLocation> completedMilestones,
            Set<ResourceLocation> completedDiscoveries,
            Map<ResourceLocation, Long> bonusTotals
    ) {
        return new SkillEvaluationContext(
                currentTierId,
                ownedRanks,
                ownedRanks,
                loadoutSelections,
                loadoutSelections,
                completedMilestones,
                completedDiscoveries,
                bonusTotals,
                bonusTotals
        );
    }

    public boolean hasProjectedChanges() {
        return !authoritativeRanks.equals(projectedRanks)
                || !authoritativeLoadoutSelections.equals(projectedLoadoutSelections)
                || !authoritativeBonusTotals.equals(projectedBonusTotals);
    }

    public Set<ResourceLocation> authoritativeOwnedSkillIds() { return authoritativeRanks.keySet(); }
    public Set<ResourceLocation> projectedOwnedSkillIds() { return projectedRanks.keySet(); }
    public int authoritativeRank(ResourceLocation id) { return authoritativeRanks.getOrDefault(id, 0); }
    public int projectedRank(ResourceLocation id) { return projectedRanks.getOrDefault(id, 0); }
    private static Map<ResourceLocation, Integer> copyRanks(Map<ResourceLocation, Integer> ranks) {
        ranks.forEach((id, rank) -> {
            if (id == null || rank == null || rank < 1 || rank > 64)
                throw new IllegalArgumentException("Invalid skill rank state");
        });
        return Map.copyOf(ranks);
    }

    private static Map<ResourceLocation, Long> copyNonNegativeTotals(
            Map<ResourceLocation, Long> totals,
            String label
    ) {
        Objects.requireNonNull(totals, label + " cannot be null");
        for (Map.Entry<ResourceLocation, Long> entry : totals.entrySet()) {
            if (entry.getKey() == null
                    || entry.getValue() == null
                    || entry.getValue() < 0L) {
                throw new IllegalArgumentException(
                        label + " must contain only non-null IDs and non-negative values"
                );
            }
        }
        return Map.copyOf(totals);
    }
}
