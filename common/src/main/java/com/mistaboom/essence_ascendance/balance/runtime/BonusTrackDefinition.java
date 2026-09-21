package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.stat.StatCategory;
import com.mistaboom.essence_ascendance.stat.StatUnit;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Resolved, environment-wide Bonus contract. Purchase math never consults live inventory or evidence. */
public record BonusTrackDefinition(ResourceLocation statId, StatCategory category, StatUnit unit,
        double maximumEffect, ResourceLocation startTier, ResourceLocation completionTier,
        List<Checkpoint> checkpoints, double investmentExponent, PurchaseStyle purchaseStyle,
        List<Double> snapPoints, Applicability applicability, List<String> compatibilityRequirements,
        double confidence, List<String> evidence, String source, Map<String, Double> inputs) {
    public enum PurchaseStyle { CONTINUOUS, THRESHOLD, FUNDED_STATES }
    public int activeStateCount() { return (int) checkpoints.stream().filter(Checkpoint::purchasable).count(); }
    public List<Double> activeValues() { return checkpoints.stream().filter(Checkpoint::purchasable)
            .map(point -> point.effectFraction() * maximumEffect).toList(); }
    /** Endpoint remains this track's maximumEffect; requirements never rewrite its generated span. */
    public com.mistaboom.essence_ascendance.skill.ProgressionRequirements.Bonus progressionRequirements() {
        return com.mistaboom.essence_ascendance.skill.ProgressionRequirements.bonus(statId);
    }
    public enum Applicability { AVAILABLE, UNAVAILABLE }
    public record Checkpoint(ResourceLocation tierId, long cumulativeCap, long segmentCost,
                             double effectFraction, boolean available, boolean purchasable) {
        public Checkpoint {
            Objects.requireNonNull(tierId);
            if (cumulativeCap < 0 || cumulativeCap > Long.MAX_VALUE / 10000 || segmentCost < 0
                    || !Double.isFinite(effectFraction) || effectFraction < 0 || effectFraction > 1)
                throw new IllegalArgumentException("Invalid Bonus checkpoint " + tierId);
        }
    }
    public BonusTrackDefinition {
        Objects.requireNonNull(statId); Objects.requireNonNull(category); Objects.requireNonNull(unit);
        Objects.requireNonNull(startTier); Objects.requireNonNull(completionTier);
        Objects.requireNonNull(purchaseStyle); Objects.requireNonNull(applicability);
        checkpoints = List.copyOf(checkpoints); snapPoints = List.copyOf(snapPoints);
        compatibilityRequirements = List.copyOf(compatibilityRequirements); evidence = List.copyOf(evidence);
        inputs = Collections.unmodifiableMap(new TreeMap<>(inputs));
        if (!Double.isFinite(maximumEffect) || maximumEffect < 0 || maximumEffect > 1_000_000
                || !Double.isFinite(investmentExponent) || investmentExponent <= 0 || investmentExponent > 1
                || !Double.isFinite(confidence) || confidence < 0 || confidence > 1 || source == null || source.isBlank()
                || checkpoints.isEmpty() || inputs.values().stream().anyMatch(v -> !Double.isFinite(v)))
            throw new IllegalArgumentException("Invalid resolved Bonus " + statId);
        if (maximumEffect > inputs.getOrDefault("native_maximum_effect", Double.MAX_VALUE))
            throw new IllegalArgumentException("Bonus exceeds generated native attribute headroom " + statId);
        long previous = 0; double fraction = 0; boolean completed = false;
        Set<ResourceLocation> ids = new HashSet<>();
        for (Checkpoint point : checkpoints) {
            if (!ids.add(point.tierId()) || point.cumulativeCap() < previous || point.effectFraction() < fraction
                    || point.segmentCost() != point.cumulativeCap() - previous
                    || ((point.segmentCost() > 0) != (point.effectFraction() > fraction))
                    || point.purchasable() != (point.available() && point.segmentCost() > 0)
                    || (!point.available() && (point.cumulativeCap() != 0 || point.effectFraction() != 0))
                    || (completed && point.segmentCost() != 0))
                throw new IllegalArgumentException("Inconsistent Bonus checkpoint " + statId + "/" + point.tierId());
            previous = point.cumulativeCap(); fraction = point.effectFraction(); completed |= fraction == 1;
        }
        if (!ids.contains(startTier) || !ids.contains(completionTier)
                || (applicability == Applicability.AVAILABLE && (fraction != 1 || maximumEffect <= 0))
                || (applicability == Applicability.UNAVAILABLE && (previous != 0 || fraction != 0)))
            throw new IllegalArgumentException("Invalid Bonus applicability/span " + statId);
        boolean started = false;
        for (var point : checkpoints) {
            started |= point.tierId().equals(startTier);
            if (point.available() != (applicability == Applicability.AVAILABLE && started))
                throw new IllegalArgumentException("Bonus availability disagrees with declared start " + statId);
        }
        if (applicability == Applicability.AVAILABLE) {
            var first = checkpoints.stream().filter(point -> point.effectFraction() > 0).findFirst().orElseThrow();
            var last = checkpoints.stream().filter(point -> point.effectFraction() == 1).findFirst().orElseThrow();
            if (!first.tierId().equals(startTier) || !last.tierId().equals(completionTier))
                throw new IllegalArgumentException("Declared Bonus start/completion disagree with checkpoints " + statId);
        }
        double priorSnap = -1;
        for (double snap : snapPoints) {
            if (!Double.isFinite(snap) || snap < 0 || snap > 1 || snap <= priorSnap)
                throw new IllegalArgumentException("Invalid Bonus snap points " + statId);
            priorSnap = snap;
        }
        if (purchaseStyle != PurchaseStyle.CONTINUOUS && applicability == Applicability.AVAILABLE
                && (snapPoints.isEmpty() || snapPoints.getFirst() != 0 || snapPoints.getLast() != 1))
            throw new IllegalArgumentException("Threshold Bonuses must include zero and completion snaps");
        if (purchaseStyle != PurchaseStyle.CONTINUOUS && applicability == Applicability.AVAILABLE) {
            if (purchaseStyle == PurchaseStyle.FUNDED_STATES
                    && snapPoints.size() != checkpoints.stream().filter(Checkpoint::purchasable).count() + 1)
                throw new IllegalArgumentException("Extra or missing complete Bonus state " + statId);
            long priorCost = -1;
            for (double snap : snapPoints) {
                long cost = com.mistaboom.essence_ascendance.progression.BonusTrackCurve.investmentForProgression(
                        checkpoints, investmentExponent, snap, completionTier);
                if (cost <= priorCost) throw new IllegalArgumentException("Distinct Bonus thresholds collapse to one price " + statId);
                priorCost = cost;
            }
            for (var point : checkpoints) if (point.purchasable() && !snapPoints.contains(point.effectFraction()))
                throw new IllegalArgumentException("Threshold Bonus tier ends between snap points " + statId);
            for (int index = 1; index < snapPoints.size(); index++) {
                Double boundary = inputs.get("native_threshold_" + index);
                if (boundary != null && Math.abs(boundary - maximumEffect * snapPoints.get(index)) > 1e-7)
                    throw new IllegalArgumentException("Bonus snap no longer reaches its generated native boundary " + statId);
            }
            if (purchaseStyle == PurchaseStyle.FUNDED_STATES && inputs.containsKey("first_state_floor")) {
                double priorValue = 0; boolean active = false, finished = false;
                for (var point : checkpoints) {
                    if (point.purchasable()) {
                        double value = point.effectFraction() * maximumEffect;
                        double floor = inputs.get(active ? "later_state_floor" : "first_state_floor");
                        if (finished || value - priorValue + 1e-9 < floor)
                            throw new IllegalArgumentException("Sub-floor or noncontiguous Bonus state " + statId);
                        active = true; priorValue = value;
                    } else if (active) finished = true;
                }
            }
        }
    }
    public Checkpoint checkpoint(ResourceLocation tierId) {
        return checkpoints.stream().filter(p -> p.tierId().equals(tierId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing Bonus tier " + tierId));
    }
}
