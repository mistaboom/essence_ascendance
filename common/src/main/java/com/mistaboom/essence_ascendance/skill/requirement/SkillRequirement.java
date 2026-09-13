package com.mistaboom.essence_ascendance.skill.requirement;

import com.mistaboom.essence_ascendance.skill.SkillRequirementKind;
import net.minecraft.resources.ResourceLocation;

/**
 * Declarative purchase/effect requirement metadata. Evaluation remains
 * server-authoritative and operates on a final projected Nexus transaction.
 */
public sealed interface SkillRequirement permits
        PermanentMilestoneRequirement,
        BonusInvestmentRequirement,
        DiscoveryRequirement {

    ResourceLocation id();

    String translationKey();

    SkillRequirementKind kind();

    /**
     * Live requirements can suspend an owned skill when they later become
     * unsatisfied. Permanent requirements remain latched once earned.
     */
    default boolean live() {
        return false;
    }
}
