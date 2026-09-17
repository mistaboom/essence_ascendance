package com.mistaboom.essence_ascendance.vitality;

import com.mistaboom.essence_ascendance.skill.effect.AutomaticMealPolicy;
import com.mistaboom.essence_ascendance.skill.effect.ThresholdBuffState;
import com.mistaboom.essence_ascendance.skill.tooltip.SkillValueText;
import java.util.Random;

/** Pure fixtures for the shared recovery and threshold policies; not a substitute for native loader tests. */
public final class HealingRoutingMathTest {
    private static int checks;
    public static void main(String[] args) {
        meals(); healing(); conservation(); triggers(); text();
        System.out.println("HealingRoutingMathTest: " + checks + " checks passed");
    }
    private static void meals() {
        for (int food = 0; food <= 20; food++) {
            check(AutomaticMealPolicy.useful(true, true, food, 8, false, 20, 18), "Metabolic meals cross every ordinary hunger gate");
            check(!AutomaticMealPolicy.useful(true, true, food, 8, true, 20, 18), "manual use is never interrupted");
            check(!AutomaticMealPolicy.useful(true, true, food, 0, false, 20, 18), "food must provide nutrition");
        }
        check(!AutomaticMealPolicy.useful(true, false, 20, 8, false, 20, 18), "ordinary Feast cannot eat at full hunger");
        check(!AutomaticMealPolicy.useful(false, true, 20, 8, false, 20, 18), "full HP/full hunger stops automatic meals");
        int hunger = 0, meals = 0; double hp = 7;
        while (AutomaticMealPolicy.useful(hp < 20, true, hunger, 8, false, 20, 18)) {
            if (hunger == 20) hp = Math.min(20, hp + 8 * .5); // explicit illustrative generated fixture
            hunger = Math.min(20, hunger + 8); meals++;
            check(meals < 10, "finite food-to-health recovery");
        }
        check(hunger == 20 && hp == 20 && meals == 7, "fill hunger, then HP, without eating an extra steak");
        for (double maximum : new double[]{20, 28, 100, 1024}) {
            check(AutomaticMealPolicy.useful(maximum - 1 < maximum, true, 20, 8, false, 20, 18),
                    "full-hunger meals can heal missing HP above the base health pool");
            check(!AutomaticMealPolicy.useful(maximum < maximum, true, 20, 8, false, 20, 18),
                    "full actual health ends metabolic meals at any pool size");
        }
    }
    private static void healing() {
        near(HealingRoutingMath.accepted(8, 12), 4);
        near(HealingRoutingMath.accepted(20, 24), 4); // accepted before native max-HP clamp
        near(HealingRoutingMath.accepted(20, 19), 0);
        near(HealingRoutingMath.accepted(20, Double.NaN), 0);
        near(HealingRoutingMath.mirrored(4, 12, 1), 4);
        near(HealingRoutingMath.mirrored(4, 12, .5), 2);
        near(HealingRoutingMath.mirrored(4, 1, 1), 1);
        near(HealingRoutingMath.mirrored(4, 12, 2), 4); // defensive cap even before generated validation
        near(HealingRoutingMath.mirrored(0, 12, 1), 0);
        near(HealingRoutingMath.mirrored(Double.NaN, 12, 1), 0);
        near(HealingRoutingMath.usefulHealing(3, 8, 1), 8); // parallel, not 3+8
        near(HealingRoutingMath.usefulHealing(10, 8, 1), 10);
        near(HealingRoutingMath.usefulHealing(0, 8, .5), 16);
        near(HealingRoutingMath.usefulHealing(0, 8, 0), 0);
        var queue = new LinearDamageQueue<String>();
        queue.enqueue("zombie", 8, 100); queue.enqueue("arrow", 4, 200);
        near(queue.recover(3), 3); near(queue.total(), 9);
        check(queue.snapshot().get(0).ticks() == 100 && queue.snapshot().get(1).ticks() == 200, "recovery preserves each source deadline");
        near(queue.snapshot().get(0).remaining(), 6); near(queue.snapshot().get(1).remaining(), 3);
        near(queue.recover(100), 9); check(queue.isEmpty(), "excess healing cannot create negative debt");
        queue.enqueue("native", 10, 10);
        queue.settleAmount(payment -> { queue.recover(2); return payment.amount(); });
        near(queue.total(), 7); check(queue.ticksRemaining() == 9, "healing during a native payment cannot erase its settlement");
        queue.settleAmount(payment -> { queue.clear(); return payment.amount(); });
        check(queue.isEmpty(), "death clears the ledger without callback resurrection");
        rejects(() -> queue.recover(-1)); rejects(() -> queue.recover(Double.NaN));
    }
    private static void conservation() {
        var random = new Random(28);
        for (int i = 0; i < 10_000; i++) {
            var queue = new LinearDamageQueue<Integer>();
            double initial = 0, recovered = 0, paid = 0;
            for (int source = 0; source < 3; source++) {
                double amount = .001 + random.nextDouble() * 100; initial += amount;
                queue.enqueue(source, amount, 1 + random.nextInt(40));
            }
            while (!queue.isEmpty()) {
                double heal = random.nextDouble() * 3, fraction = random.nextDouble();
                double recovery = HealingRoutingMath.mirrored(heal, queue.total(), fraction);
                recovered += queue.recover(recovery);
                for (var payment : queue.advance()) paid += payment.amount();
                near(initial, paid + recovered + queue.total());
                check(recovery >= 0 && recovery <= heal, "healing mirroring bounded at 1:1");
            }
            near(initial, paid + recovered);
        }
    }
    private static void triggers() {
        var state = new ThresholdBuffState();
        check(!state.active(0) && !state.recorded(), "no fictional startup surge");
        check(!state.observe(1, 4, 5, 100), "below threshold records diagnostic but does not trigger");
        near(state.measured(), 4); near(state.required(), 5);
        check(state.observe(2, 5, 5, 100), "exact threshold triggers");
        check(state.active(101) && !state.active(102), "buff expires exactly at deadline");
        state.observe(110, 7, 5, 100); state.observe(150, 6, 5, 100);
        check(state.expiresAt() == 250 && state.active(249), "subsequent proc refreshes one shared deadline");
        check(!state.observe(200, 0, 0, 100), "zero measurement cannot proc for free");
        state.clear(); check(!state.active(0) && !state.recorded(), "cleanup clears timed and measured state");
        rejects(() -> state.observe(0, Double.NaN, 1, 100));
        // Adrenaline observes actual HP loss, with a strict pre-hit current-maximum denominator.
        for (float maximum : new float[]{20, 28, 100, 1024}) {
            double threshold = maximum * .25;
            check(!state.observeAbove(10, threshold, threshold, 100), "exactly 25 percent does not trigger");
            check(!state.observeAbove(20, Math.nextDown(threshold), threshold, 100), "below 25 percent does not trigger");
            check(state.observeAbove(30, Math.nextUp(threshold), threshold, 100), "strictly above threshold triggers");
            check(state.expiresAt() == 130 && state.active(129), "shared timer grants immediately");
            state.observeAbove(40, threshold, threshold, 100);
            check(state.expiresAt() == 130, "small hits do not refresh an existing surge");
            state.observeAbove(50, threshold + 1, threshold, 100);
            check(state.expiresAt() == 150 && !state.active(150), "large hits refresh without stacking");
            state.clear();
            var reduced = DamageRoutingMath.ceiling(maximum * .3, maximum, maximum, 0, .75, 1);
            double actual = DamageRoutingMath.healthLost(maximum, maximum - (float)reduced.healthDamage());
            check(!state.observeAbove(10, actual, threshold, 100), "30 percent raw hit reduced to 22.5 percent does not trigger");
            check(!state.observeAbove(20, DamageRoutingMath.healthLost(maximum, maximum), threshold, 100),
                    "absorbed, canceled and zero-HP-loss hits do not trigger");
            state.clear();
        }
        // A previous capacity penalty lowers the NEXT hit's denominator, not the current hit's.
        check(!state.observeAbove(10, 4.5, 20 * .25, 100), "new penalty cannot retroactively lower threshold");
        check(state.observeAbove(20, 4.5, 16 * .25, 100), "existing reduced maximum is respected");
        state.clear();
        check(state.observeAbove(1, .1, 0, 100), "explicit zero-threshold override means any positive loss");
        check(!state.observeAbove(2, 0, 0, 100), "zero loss never triggers even at zero threshold");
        rejects(() -> state.observeAbove(1, Double.NaN, 1, 100));
    }
    private static void text() {
        check(SkillValueText.number(.000123456).equals("0.0001235"), "small generated effects are not displayed as zero");
        check(SkillValueText.number(30).equals("30"), "unnecessary decimal zeros removed");
        check(SkillValueText.number(29.998123).equals("30"), "display-only significant-figure rounding");
        rejects(() -> SkillValueText.number(Double.POSITIVE_INFINITY));
    }
    private static void near(double actual, double expected) {
        check(Math.abs(actual - expected) <= 1e-8 * Math.max(1, Math.abs(expected)), actual + " != " + expected);
    }
    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid input accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
