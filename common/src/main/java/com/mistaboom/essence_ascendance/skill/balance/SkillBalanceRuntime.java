package com.mistaboom.essence_ascendance.skill.balance;

import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Immutable generated skill prices and rank power, installed with the authoritative balance profile. */
public final class SkillBalanceRuntime {
    private static volatile Map<String, ResolvedSkill> values = Map.of();
    private SkillBalanceRuntime() { }

    public static Map<String, ResolvedSkill> snapshot() { return values; }
    public static boolean ready() { return !values.isEmpty(); }
    public static void clear() { values = Map.of(); }
    public static void install(Map<String, ResolvedSkill> candidate) {
        validate(candidate);
        values = Collections.unmodifiableMap(new TreeMap<>(candidate));
    }
    public static void validate(Map<String, ResolvedSkill> candidate) {
        if (candidate == null || candidate.size() != SkillRegistry.size())
            throw new IllegalArgumentException("Generated skill curves do not match the registered catalog");
        for (var definition : SkillRegistry.values()) {
            ResolvedSkill curve = candidate.get(definition.id().toString());
            if (curve == null || (definition.rankPolicy().maximumRank() > 0
                    && curve.maximumRank() > definition.rankPolicy().maximumRank()))
                throw new IllegalArgumentException("Missing/incompatible skill rank curve: " + definition.id());
        }
    }
    public static ResolvedRank require(String skillId, int rank) {
        ResolvedSkill skill = values.get(skillId);
        if (skill == null) throw new IllegalStateException("Generated skill balance is unavailable for " + skillId);
        if (rank < 1 || rank > skill.ranks().size()) throw new IllegalArgumentException("Rank outside generated curve");
        return skill.ranks().get(rank - 1);
    }
    public record ResolvedSkill(int maximumRank, List<ResolvedRank> ranks) {
        public ResolvedSkill {
            ranks = List.copyOf(ranks);
            if (maximumRank < 1 || maximumRank > 64 || ranks.size() < maximumRank || ranks.size() > 64)
                throw new IllegalArgumentException("Invalid generated maximum skill rank");
            if (ranks.getFirst().powerMultiplier() != 1.0)
                throw new IllegalArgumentException("Rank-one power must be 1: change generated effect tuning to adjust base power");
            long lastCost = -1;
            double lastPower = 0;
            for (int i = 0; i < ranks.size(); i++) {
                ResolvedRank rank = ranks.get(i);
                if (rank.rank() != i + 1 || rank.cost() < lastCost || rank.powerMultiplier() < lastPower)
                    throw new IllegalArgumentException("Nonmonotonic generated rank curve");
                lastCost = rank.cost(); lastPower = rank.powerMultiplier();
            }
        }
    }
    /** Multiplier retains nominal allocation diagnostics; parameters are the published native value authority. */
    public record ResolvedRank(int rank, long cost, double powerMultiplier, Map<String, Double> parameters) {
        public ResolvedRank(int rank, long cost, double powerMultiplier) { this(rank, cost, powerMultiplier, Map.of()); }
        public ResolvedRank {
            parameters = Collections.unmodifiableMap(new TreeMap<>(parameters));
            if (rank < 1 || rank > 64 || cost < 0 || !Double.isFinite(powerMultiplier) || powerMultiplier <= 0)
                throw new IllegalArgumentException("Invalid generated skill rank value");
            if (parameters.entrySet().stream().anyMatch(e -> e.getKey().isBlank() || !Double.isFinite(e.getValue())))
                throw new IllegalArgumentException("Invalid generated native rank parameters");
        }
    }
}
