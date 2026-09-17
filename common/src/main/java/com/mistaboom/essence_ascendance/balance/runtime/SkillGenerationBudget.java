package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import net.minecraft.resources.ResourceLocation;

/** One tier-to-budget contract for generated skill families; not a separate tuning table. */
final class SkillGenerationBudget {
    private SkillGenerationBudget() { }
    static double headroom(BalanceSettings settings, ResourceLocation id) {
        var tiers = AscendanceTierRegistry.powerTiers().stream()
                .sorted(java.util.Comparator.comparingInt(AscendanceTierDefinition::order)).toList();
        var requiredTier = SkillRegistry.require(id).requiredTierId();
        for (int index = 0; index < tiers.size(); index++) if (tiers.get(index).id().equals(requiredTier))
            return BuildPowerTargets.rankOneMultiplier(settings, ProgressionBand.at(index),
                    BuildComposition.Participation.SKILL_FOCUSED) - 1;
        throw new IllegalArgumentException("No generated power band for skill " + id + " at " + requiredTier);
    }
}
