package com.mistaboom.essence_ascendance.skill;

/** Reusable, bounded cost and power curves. Rank one has power multiplier one. */
public record SkillRankCurve(CostShape costShape, PowerShape powerShape,
                             double costGrowth, double powerGrowth, double softCap) {
    public enum CostShape { FLAT_INCREMENT, GEOMETRIC }
    public enum PowerShape { DIMINISHING, SOFT_CAPPED, UTILITY, CHANCE, DURATION, COOLDOWN, THRESHOLD }
    public SkillRankCurve {
        if (costShape == null || powerShape == null || !Double.isFinite(costGrowth)
                || costGrowth < 0 || !Double.isFinite(powerGrowth) || powerGrowth < 0
                || !Double.isFinite(softCap) || softCap < 1) throw new IllegalArgumentException("Invalid skill rank curve");
    }
    public static SkillRankCurve standard() {
        return new SkillRankCurve(CostShape.GEOMETRIC, PowerShape.DIMINISHING, 0.6, 0.35, 3.0);
    }
    public long cost(long firstRankCost, int rank) {
        requireRank(rank);
        if (firstRankCost < 0L) throw new IllegalArgumentException("Negative first-rank cost");
        double factor = costShape == CostShape.GEOMETRIC
                ? Math.pow(1.0 + costGrowth, rank - 1.0) : 1.0 + costGrowth * (rank - 1.0);
        double result = Math.ceil(firstRankCost * factor);
        if (!Double.isFinite(result) || result >= Long.MAX_VALUE) throw new ArithmeticException("Skill rank cost overflow");
        return (long) result;
    }
    public double power(int rank) {
        requireRank(rank);
        double increments = rank - 1.0;
        return switch (powerShape) {
            case DIMINISHING, DURATION, UTILITY -> Math.min(softCap, 1.0 + powerGrowth * Math.log1p(increments));
            case SOFT_CAPPED, CHANCE, COOLDOWN -> 1.0 + (softCap - 1.0)
                    * (1.0 - Math.exp(-powerGrowth * increments));
            case THRESHOLD -> Math.min(softCap, 1.0 + Math.floor(increments / 2.0) * powerGrowth);
        };
    }
    public double chance(double firstRankChance, int rank) {
        if (!Double.isFinite(firstRankChance) || firstRankChance < 0 || firstRankChance > 1)
            throw new IllegalArgumentException("Chance must be in [0,1]");
        return Math.min(1.0, firstRankChance * power(rank));
    }
    public double cooldown(double firstRankTicks, double minimumTicks, int rank) {
        if (!Double.isFinite(firstRankTicks) || !Double.isFinite(minimumTicks)
                || minimumTicks <= 0 || firstRankTicks < minimumTicks)
            throw new IllegalArgumentException("Cooldown must have a positive floor");
        return Math.max(minimumTicks, firstRankTicks / power(rank));
    }
    private static void requireRank(int rank) {
        if (rank < 1 || rank > 64) throw new IllegalArgumentException("Rank must be in [1,64]");
    }
}
