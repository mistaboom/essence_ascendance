package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.balance.runtime.BonusTrackDefinition.Checkpoint;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Pure arithmetic over authoritative checkpoints; shared by server validation and synced previews. */
public final class BonusTrackCurve {
    private BonusTrackCurve() { }

    public static double maximumProgression(List<Checkpoint> checkpoints, ResourceLocation tierId) {
        return checkpoints.get(tierIndex(checkpoints, tierId)).effectFraction();
    }

    public static long maximumInvestment(List<Checkpoint> checkpoints, ResourceLocation tierId) {
        return checkpoints.get(tierIndex(checkpoints, tierId)).cumulativeCap();
    }

    public static double progressionForInvestment(List<Checkpoint> checkpoints, double exponent,
                                                  long investment, ResourceLocation tierId) {
        requireExponent(exponent);
        int end = tierIndex(checkpoints, tierId);
        long amount = Math.clamp(investment, 0L, checkpoints.get(end).cumulativeCap());
        long previousCap = 0;
        double previousFraction = 0;
        for (int index = 0; index <= end; index++) {
            Checkpoint point = checkpoints.get(index);
            if (point.cumulativeCap() == previousCap) continue;
            // Stored checkpoints are the authority. Interpolating with t=1 can
            // round a + (b - a) one ULP away from b and miss a funded threshold.
            if (amount == point.cumulativeCap()) return point.effectFraction();
            if (amount <= point.cumulativeCap()) {
                double segment = (amount - previousCap) / (double) (point.cumulativeCap() - previousCap);
                return previousFraction + (point.effectFraction() - previousFraction)
                        * Math.pow(Math.clamp(segment, 0, 1), exponent);
            }
            previousCap = point.cumulativeCap();
            previousFraction = point.effectFraction();
        }
        return previousFraction;
    }

    public static long investmentForProgression(List<Checkpoint> checkpoints, double exponent,
                                                double progression, ResourceLocation tierId) {
        requireExponent(exponent);
        if (!Double.isFinite(progression)) throw new IllegalArgumentException("Progression must be finite");
        int end = tierIndex(checkpoints, tierId);
        double desired = Math.clamp(progression, 0, checkpoints.get(end).effectFraction());
        long previousCap = 0;
        double previousFraction = 0;
        for (int index = 0; index <= end; index++) {
            Checkpoint point = checkpoints.get(index);
            if (point.cumulativeCap() == previousCap) continue;
            // Avoid a lossy long -> double -> long round trip at an exact target.
            if (desired == point.effectFraction()) return point.cumulativeCap();
            if (desired <= point.effectFraction()) {
                double segment = (desired - previousFraction) / (point.effectFraction() - previousFraction);
                return Math.clamp(previousCap + Math.round((point.cumulativeCap() - previousCap)
                        * Math.pow(Math.clamp(segment, 0, 1), 1 / exponent)), previousCap, point.cumulativeCap());
            }
            previousCap = point.cumulativeCap();
            previousFraction = point.effectFraction();
        }
        return previousCap;
    }

    /** Chooses the nearest meaningful effect that is both unlocked and affordable; ties refund downward. */
    public static long snapInvestment(List<Checkpoint> checkpoints, double exponent, List<Double> snapPoints,
                                      long desired, long maximumInvestment, ResourceLocation tierId) {
        long ceiling = Math.clamp(maximumInvestment, 0L, maximumInvestment(checkpoints, tierId));
        double desiredFraction = progressionForInvestment(checkpoints, exponent, Math.clamp(desired, 0L, ceiling), tierId);
        double maximumFraction = maximumProgression(checkpoints, tierId);
        long best = 0;
        double distance = desiredFraction;
        for (double fraction : snapPoints) {
            if (fraction < 0 || fraction > maximumFraction) continue;
            long cost = investmentForProgression(checkpoints, exponent, fraction, tierId);
            if (cost > ceiling) continue;
            double candidateDistance = Math.abs(fraction - desiredFraction);
            if (candidateDistance < distance || (candidateDistance == distance && cost < best)) {
                best = cost;
                distance = candidateDistance;
            }
        }
        return best;
    }

    public static boolean isSnapInvestment(List<Checkpoint> checkpoints, double exponent, List<Double> snapPoints,
                                           long investment, ResourceLocation tierId) {
        if (investment == 0) return true;
        if (investment < 0 || investment > maximumInvestment(checkpoints, tierId)) return false;
        double maximumFraction = maximumProgression(checkpoints, tierId);
        return snapPoints.stream().filter(fraction -> fraction >= 0 && fraction <= maximumFraction)
                .anyMatch(fraction -> investmentForProgression(checkpoints, exponent, fraction, tierId) == investment);
    }

    /** Native thresholds must apply exactly even when integer Essence rounding lands just below the raw curve. */
    public static double realizedProgressionForInvestment(List<Checkpoint> checkpoints, double exponent,
                                                          List<Double> snapPoints, long investment,
                                                          ResourceLocation tierId) {
        long amount = Math.clamp(investment, 0L, maximumInvestment(checkpoints, tierId));
        if (amount == 0) return 0;
        double ceiling = maximumProgression(checkpoints, tierId);
        double earned = 0;
        for (double fraction : snapPoints)
            if (fraction >= 0 && fraction <= ceiling
                    && investmentForProgression(checkpoints, exponent, fraction, tierId) <= amount)
                earned = Math.max(earned, fraction);
        return earned;
    }

    private static int tierIndex(List<Checkpoint> checkpoints, ResourceLocation tierId) {
        for (int index = 0; index < checkpoints.size(); index++)
            if (checkpoints.get(index).tierId().equals(tierId)) return index;
        throw new IllegalArgumentException("No Bonus checkpoint for tier " + tierId);
    }

    private static void requireExponent(double exponent) {
        if (!Double.isFinite(exponent) || exponent <= 0 || exponent > 1)
            throw new IllegalArgumentException("Investment exponent must be in (0, 1]");
    }
}
