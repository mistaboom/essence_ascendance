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
        Set<ResourceLocation> authoritativeOwnedSkillIds,
        Set<ResourceLocation> projectedOwnedSkillIds,
        Map<ResourceLocation, ResourceLocation> authoritativeLoadoutSelections,
        Map<ResourceLocation, ResourceLocation> projectedLoadoutSelections,
        Set<ResourceLocation> completedAttunements,
        Set<ResourceLocation> completedMilestones,
        Set<ResourceLocation> completedDiscoveries,
        Map<ResourceLocation, Long> authoritativeBonusTotals,
        Map<ResourceLocation, Long> projectedBonusTotals
) {
    public SkillEvaluationContext {
        Objects.requireNonNull(currentTierId, "Current tier ID cannot be null");
        authoritativeOwnedSkillIds = Set.copyOf(Objects.requireNonNull(
                authoritativeOwnedSkillIds,
                "Authoritative owned-skill IDs cannot be null"
        ));
        projectedOwnedSkillIds = Set.copyOf(Objects.requireNonNull(
                projectedOwnedSkillIds,
                "Projected owned-skill IDs cannot be null"
        ));
        authoritativeLoadoutSelections = Map.copyOf(Objects.requireNonNull(
                authoritativeLoadoutSelections,
                "Authoritative loadout selections cannot be null"
        ));
        projectedLoadoutSelections = Map.copyOf(Objects.requireNonNull(
                projectedLoadoutSelections,
                "Projected loadout selections cannot be null"
        ));
        completedAttunements = Set.copyOf(Objects.requireNonNull(
                completedAttunements,
                "Completed attunements cannot be null"
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

        if (!projectedOwnedSkillIds.containsAll(authoritativeOwnedSkillIds)) {
            throw new IllegalArgumentException(
                    "A Nexus projection cannot remove permanent skill ownership"
            );
        }
    }

    /**
     * Builds a no-draft context. This is convenient for runtime gameplay
     * consumers that only need the current effective state.
     */
    public static SkillEvaluationContext committed(
            ResourceLocation currentTierId,
            Set<ResourceLocation> ownedSkillIds,
            Map<ResourceLocation, ResourceLocation> loadoutSelections,
            Set<ResourceLocation> completedAttunements,
            Set<ResourceLocation> completedMilestones,
            Set<ResourceLocation> completedDiscoveries,
            Map<ResourceLocation, Long> bonusTotals
    ) {
        return new SkillEvaluationContext(
                currentTierId,
                ownedSkillIds,
                ownedSkillIds,
                loadoutSelections,
                loadoutSelections,
                completedAttunements,
                completedMilestones,
                completedDiscoveries,
                bonusTotals,
                bonusTotals
        );
    }

    public boolean hasProjectedChanges() {
        return !authoritativeOwnedSkillIds.equals(projectedOwnedSkillIds)
                || !authoritativeLoadoutSelections.equals(projectedLoadoutSelections)
                || !authoritativeBonusTotals.equals(projectedBonusTotals);
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
