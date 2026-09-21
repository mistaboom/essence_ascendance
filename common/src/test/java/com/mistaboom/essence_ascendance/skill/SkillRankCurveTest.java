package com.mistaboom.essence_ascendance.skill;

/** Dependency-free executable invariants: javac SkillRankCurve.java SkillRankCurveTest.java, then run main. */
public final class SkillRankCurveTest {
    public static void main(String[] args) {
        for (var costShape : SkillRankCurve.CostShape.values()) {
            for (var powerShape : SkillRankCurve.PowerShape.values()) {
                var curve = new SkillRankCurve(costShape, powerShape, 0.25, 0.35, 3.0);
                long previousCost = -1L;
                double previousPower = 0.0;
                double previousCooldown = 1000.0;
                for (int rank = 1; rank <= 64; rank++) {
                    long cost = curve.cost(101L, rank);
                    double power = curve.power(rank);
                    double chance = curve.chance(0.6, rank);
                    double cooldown = curve.cooldown(200.0, 20.0, rank);
                    require(cost >= previousCost, "Rank price decreased");
                    require(power >= previousPower && power <= 3.0, "Rank power violated cap/order");
                    require(chance >= 0.6 && chance <= 1.0, "Chance violated bounds");
                    require(cooldown <= previousCooldown && cooldown >= 20.0, "Cooldown violated bounds/order");
                    previousCost = cost; previousPower = power; previousCooldown = cooldown;
                }
                require(curve.power(1) == 1.0, "Rank-one behavior changed");
            }
        }
        var curve = SkillRankCurve.standard();
        double[] developed = {1, 1.3, 1.65, 2.05, 2.5};
        for (int rank = 1; rank <= developed.length; rank++)
            require(Math.abs(SkillRankCurve.developed().power(rank) - developed[rank - 1]) < 1e-12,
                    "Nominal allocation sampling schedule changed; publication selects meaningful states");
        double previousIncrement = Double.MAX_VALUE;
        for (int rank = 2; rank <= 64; rank++) {
            double increment = curve.power(rank) - curve.power(rank - 1);
            require(increment <= previousIncrement + 1e-12, "Diminishing curve accelerated");
            previousIncrement = increment;
        }
        rejects(() -> curve.cost(Long.MAX_VALUE, 64));
        rejects(() -> curve.cost(-1, 1));
        rejects(() -> curve.power(0));
        rejects(() -> curve.power(65));
        rejects(() -> curve.chance(Double.NaN, 1));
        rejects(() -> curve.cooldown(20, 0, 1));
        System.out.println("SkillRankCurveTest: all cost/power shapes, 64-rank bounds, diminishing gains and overflow passed.");
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | ArithmeticException expected) { return; }
        throw new AssertionError("Invalid rank curve input was accepted");
    }
}
