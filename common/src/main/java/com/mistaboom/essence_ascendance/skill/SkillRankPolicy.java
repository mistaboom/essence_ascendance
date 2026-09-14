package com.mistaboom.essence_ascendance.skill;

import com.mistaboom.essence_ascendance.skill.requirement.SkillRequirement;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Map;

/** Catalog intent, separate from generated prices. Current skills remain permanent single purchases. */
public record SkillRankPolicy(int maximumRank, int projectionRanks, SkillRankCurve curve,
                              RefundRule refundRule, Map<Integer, Gates> rankGates) {
    public enum RefundRule { NONE, EXACT_PAID }
    public SkillRankPolicy {
        if (maximumRank < 1 || maximumRank > 64 || projectionRanks < maximumRank || projectionRanks > 64
                || curve == null || refundRule == null) throw new IllegalArgumentException("Invalid skill rank policy");
        rankGates = Map.copyOf(rankGates);
        rankGates.forEach((rank, gates) -> {
            if (rank < 1 || rank > maximumRank || gates == null) throw new IllegalArgumentException("Invalid rank gate");
        });
    }
    public static SkillRankPolicy singlePurchase() {
        return new SkillRankPolicy(1, 5, SkillRankCurve.developed(), RefundRule.NONE, Map.of());
    }
    public record Gates(ResourceLocation requiredTierId, Map<ResourceLocation, Integer> prerequisites,
                        List<SkillRequirement> requirements) {
        public Gates {
            prerequisites = Map.copyOf(prerequisites);
            requirements = List.copyOf(requirements);
            if (requirements.stream().map(SkillRequirement::id).distinct().count() != requirements.size())
                throw new IllegalArgumentException("Duplicate rank requirement identity");
            prerequisites.forEach((id, rank) -> {
                if (id == null || rank < 1 || rank > 64) throw new IllegalArgumentException("Invalid prerequisite rank");
            });
        }
    }
}
