package com.mistaboom.essence_ascendance.skill;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Detailed current/projected state for a catalog skill. These flags remain
 * separate so callers never need to infer gameplay state from UI labels.
 */
public record SkillEvaluationResult(
        SkillDefinition definition,
        SkillPurchaseEligibility purchaseEligibility,
        int currentRank,
        int projectedRank,
        boolean owned,
        boolean projectedOwned,
        boolean staged,
        boolean selected,
        boolean projectedSelected,
        boolean effective,
        boolean projectedEffective,
        boolean suspended,
        boolean projectedSuspended,
        boolean willSuspend,
        boolean replaced,
        boolean projectedReplaced,
        boolean fallbackActive,
        boolean projectedFallbackActive,
        Set<ResourceLocation> replacedBy,
        Set<ResourceLocation> projectedReplacedBy,
        Set<ResourceLocation> inactivePrerequisites,
        Set<ResourceLocation> projectedInactivePrerequisites
) {
    public SkillEvaluationResult {
        Objects.requireNonNull(definition, "Skill definition cannot be null");
        Objects.requireNonNull(
                purchaseEligibility,
                "Purchase eligibility cannot be null"
        );
        replacedBy = Set.copyOf(Objects.requireNonNull(
                replacedBy,
                "Current replacement sources cannot be null"
        ));
        projectedReplacedBy = Set.copyOf(Objects.requireNonNull(
                projectedReplacedBy,
                "Projected replacement sources cannot be null"
        ));
        inactivePrerequisites = Set.copyOf(Objects.requireNonNull(
                inactivePrerequisites,
                "Current inactive prerequisites cannot be null"
        ));
        projectedInactivePrerequisites = Set.copyOf(Objects.requireNonNull(
                projectedInactivePrerequisites,
                "Projected inactive prerequisites cannot be null"
        ));
    }

    /** True when every projected purchase gate is met and the skill is not committed. */
    public boolean eligibleToPurchase() {
        return currentRank < definition.maximumRank() && purchaseEligibility.satisfied();
    }

    public List<SkillPrerequisiteStatus> prerequisites() {
        return purchaseEligibility.prerequisites();
    }

    public List<SkillRequirementStatus> requirements() {
        return purchaseEligibility.requirements();
    }

    public SkillDisplayState displayState() {
        if (!owned) {
            return eligibleToPurchase()
                    ? SkillDisplayState.ELIGIBLE
                    : SkillDisplayState.LOCKED;
        }
        if (replaced) {
            return SkillDisplayState.REPLACED;
        }
        if (suspended) {
            return SkillDisplayState.SUSPENDED;
        }
        return effective
                ? SkillDisplayState.OWNED_ACTIVE
                : SkillDisplayState.OWNED_INACTIVE;
    }

    public SkillDisplayState projectedDisplayState() {
        if (staged) {
            return SkillDisplayState.STAGED;
        }
        if (!projectedOwned) {
            return eligibleToPurchase()
                    ? SkillDisplayState.ELIGIBLE
                    : SkillDisplayState.LOCKED;
        }
        if (projectedReplaced) {
            return SkillDisplayState.REPLACED;
        }
        if (willSuspend) {
            return SkillDisplayState.WILL_SUSPEND;
        }
        if (projectedSuspended) {
            return SkillDisplayState.SUSPENDED;
        }
        return projectedEffective
                ? SkillDisplayState.OWNED_ACTIVE
                : SkillDisplayState.OWNED_INACTIVE;
    }
}
