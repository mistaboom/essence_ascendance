package com.mistaboom.essence_ascendance.skill.balance;

import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.resources.ResourceLocation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/** Immutable generated skill prices and rank power, installed with the authoritative balance profile. */
public final class SkillBalanceRuntime {
    private static volatile Map<String, ResolvedSkill> values = Map.of();
    private static final ThreadLocal<Map<String, ResolvedSkill>> scopedCurves = new ThreadLocal<>();
    private static final ThreadLocal<Map<ResourceLocation, ResourceLocation>> scopedTiers = new ThreadLocal<>();
    private SkillBalanceRuntime() { }

    public static Map<String, ResolvedSkill> snapshot() {
        var candidate = scopedCurves.get();
        return candidate == null ? values : candidate;
    }
    public static boolean ready() { return !snapshot().isEmpty(); }
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
            validateTier(definition.id(), curve.requiredTierId());
        }
        // Candidate validation must never depend on whichever profile is currently installed.
        withCurves(candidate, () -> { validatePrerequisiteTiers(); return null; });
    }
    public static ResourceLocation resolvedRequiredTier(ResourceLocation skillId, ResourceLocation catalogTier) {
        var tiers = scopedTiers.get();
        if (tiers != null) return tiers.getOrDefault(skillId, catalogTier);
        var curve = snapshot().get(skillId.toString());
        return curve == null || curve.requiredTierId() == null ? catalogTier : curve.requiredTierId();
    }
    /** Generation sees only this candidate's placement; unspecified skills retain catalog placement. */
    public static <T> T withRequiredTiers(Map<ResourceLocation, ResourceLocation> tiers, Supplier<T> operation) {
        var candidate = Map.copyOf(tiers);
        candidate.forEach(SkillBalanceRuntime::validateTier);
        var previous = scopedTiers.get();
        var previousCurves = scopedCurves.get();
        scopedCurves.set(Map.of()); // A fresh generation must not inherit old prices or rank limits.
        scopedTiers.set(candidate);
        try { validatePrerequisiteTiers(); return operation.get(); }
        finally { restore(scopedTiers, previous); restore(scopedCurves, previousCurves); }
    }
    /** Read-only loading/projection scope. It publishes neither prices nor availability globally. */
    public static <T> T withCurves(Map<String, ResolvedSkill> curves, Supplier<T> operation) {
        var previousCurves = scopedCurves.get();
        var previousTiers = scopedTiers.get();
        scopedCurves.set(Collections.unmodifiableMap(new TreeMap<>(curves)));
        scopedTiers.remove();
        try { return operation.get(); }
        finally { restore(scopedCurves, previousCurves); restore(scopedTiers, previousTiers); }
    }
    private static <T> void restore(ThreadLocal<T> scope, T previous) {
        if (previous == null) scope.remove(); else scope.set(previous);
    }
    private static void validateTier(ResourceLocation skillId, ResourceLocation tierId) {
        var definition = SkillRegistry.require(skillId);
        if (tierId == null) return; // Old saved profiles retain their immutable catalog placement.
        var tier = AscendanceTierRegistry.get(tierId).orElseThrow(() ->
                new IllegalArgumentException("Unknown generated skill tier: " + skillId + " / " + tierId));
        if (!tier.grantsPower())
            throw new IllegalArgumentException("Generated skill tier must grant power: " + skillId);
    }
    private static void validatePrerequisiteTiers() {
        for (var skill : SkillRegistry.values()) {
            var curve = snapshot().get(skill.id().toString());
            int maximum = curve == null ? skill.rankPolicy().projectionRanks() : curve.maximumRank();
            for (int rank = 1; rank <= maximum; rank++) {
            int order = AscendanceTierRegistry.get(skill.requiredTierId(rank)).orElseThrow().order();
            for (var prerequisite : skill.prerequisiteRanks(rank).entrySet()) {
                var parent = SkillRegistry.require(prerequisite.getKey());
                var parentCurve = snapshot().get(parent.id().toString());
                if (parentCurve != null && prerequisite.getValue() > parentCurve.maximumRank())
                    throw new IllegalArgumentException("Generated prerequisite rank is unavailable: " + skill.id() + " / " + parent.id());
                int parentOrder = AscendanceTierRegistry.get(parent.requiredTierId(prerequisite.getValue())).orElseThrow().order();
                if (parentOrder > order)
                    throw new IllegalArgumentException("Generated skill precedes required prerequisite: " + skill.id() + " / " + parent.id());
            }
            }
        }
    }
    public static ResolvedRank require(String skillId, int rank) {
        ResolvedSkill skill = snapshot().get(skillId);
        if (skill == null) throw new IllegalStateException("Generated skill balance is unavailable for " + skillId);
        if (rank < 1 || rank > skill.ranks().size()) throw new IllegalArgumentException("Rank outside generated curve");
        return skill.ranks().get(rank - 1);
    }
    public record ResolvedSkill(int maximumRank, List<ResolvedRank> ranks, ResourceLocation requiredTierId) {
        public ResolvedSkill(int maximumRank, List<ResolvedRank> ranks) { this(maximumRank, ranks, null); }
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
