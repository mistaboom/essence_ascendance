package com.mistaboom.essence_ascendance.skill;

import com.mistaboom.essence_ascendance.progression.Milestones;
import net.minecraft.resources.ResourceLocation;

/**
 * Compatibility aliases for the registered milestone definitions.
 *
 * <p>The authoritative IDs, default providers, targets, and display names now
 * live in {@link Milestones}; this class deliberately contains no separate
 * skill-milestone registry or hard-coded targets.</p>
 */
public final class SkillMilestones {

    public static final ResourceLocation SKY_LIMIT = Milestones.SKY_LIMIT.id();
    public static final ResourceLocation HERO_OF_THE_VILLAGE =
            Milestones.HERO_OF_THE_VILLAGE.id();
    public static final ResourceLocation BEST_FRIENDS_FOREVER =
            Milestones.BEST_FRIENDS_FOREVER.id();
    public static final ResourceLocation LOCAL_BREWERY =
            Milestones.LOCAL_BREWERY.id();
    public static final ResourceLocation ENCHANTER = Milestones.ENCHANTER.id();
    public static final ResourceLocation BEACON_ACTIVATION =
            Milestones.BEACON_ACTIVATION.id();

    private SkillMilestones() {
    }

}
