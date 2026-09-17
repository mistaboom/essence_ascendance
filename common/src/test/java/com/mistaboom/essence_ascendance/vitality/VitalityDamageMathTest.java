package com.mistaboom.essence_ascendance.vitality;

import com.mistaboom.essence_ascendance.config.VitalityDamageBalanceSettings;
import java.util.Random;
import com.mistaboom.essence_ascendance.damage.DamageFeedbackState;
import com.mistaboom.essence_ascendance.skill.effect.AutomaticMealPolicy;
import com.mistaboom.essence_ascendance.skill.effect.DamageRoutingState;

/** Pure executable accounting tests: no Minecraft bootstrap, loader, Gradle cache or test framework. */
public final class VitalityDamageMathTest {
    private static int checks;
    public static void main(String[] args) {
        food(); ward(); meals(); quietFeedback(); queues(); trauma(); settings();
        System.out.println("VitalityDamageMathTest: " + checks + " checks passed");
    }
    private static void food() {
        var first = DamageRoutingMath.debitFood(1.25, 1, 20, 1, 0);
        near(first.remainingDamage(), 0); near(first.saturation(), 0);
        check(first.food() == 19, "one whole hunger point is paid once"); near(first.prepaidFood(), .75);
        var second = DamageRoutingMath.debitFood(.5, 1, first.food(), first.saturation(), first.prepaidFood());
        check(second.food() == 19, "fractional paid food is reused, not charged twice"); near(second.prepaidFood(), .25);
        var empty = DamageRoutingMath.debitFood(9, 2, 0, 0, 0); near(empty.remainingDamage(), 9);
        var disabled = DamageRoutingMath.debitFood(9, 0, 20, 20, 0); near(disabled.remainingDamage(), 9); check(disabled.food() == 20, "zero budget spends nothing");
        var restored = DamageRoutingMath.restoreFood(.75, 18, 0, 0, 20);
        check(restored.food() == 18, "sub-point healing is carried"); near(restored.carry(), .75);
        restored = DamageRoutingMath.restoreFood(2.25, restored.food(), restored.saturation(), restored.carry(), 20);
        check(restored.food() == 20, "restore hunger first"); near(restored.saturation(), 1); near(restored.carry(), 0);
        restored = DamageRoutingMath.restoreFood(100, 20, 20, .5, 20);
        near(restored.saturation(), 20); near(restored.carry(), 0);
        Random random = new Random(28);
        for (int iteration = 0; iteration < 20_000; iteration++) {
            int food = random.nextInt(21); double saturation = random.nextDouble() * food;
            double prepaid = random.nextDouble(), unit = .01 + random.nextDouble() * 25;
            double hit = random.nextDouble() * 100;
            var debit = DamageRoutingMath.debitFood(hit, unit, food, saturation, prepaid);
            near(hit - debit.remainingDamage(), (food + saturation + prepaid - debit.food() - debit.saturation() - debit.prepaidFood()) * unit);
            check(debit.food() >= 0 && debit.food() <= food && debit.remainingDamage() >= 0 && debit.remainingDamage() <= hit + 1e-9,
                    "bounded food/health routing");
            var refund = DamageRoutingMath.restoreFood(hit, food, saturation, prepaid, 20);
            double gained = refund.food() + refund.saturation() + refund.carry() - food - saturation - prepaid;
            check(gained <= hit + 1e-9 && refund.food() <= 20 && refund.saturation() <= 20, "restoration never duplicates hunger/saturation");
        }
    }
    private static void ward() {
        // Explicit fixture, not an assertion about a particular generated pack profile.
        var split = DamageRoutingMath.ward(10, .3, 1, 20, 0, 0);
        near(split.remainingDamage(), 7); check(split.food() == 17, "30% redirect pays three food, not the whole bar");
        var saturated = DamageRoutingMath.ward(10, .3, 1.5, 20, 5, 0);
        near(saturated.remainingDamage(), 7); near(saturated.saturation(), 3);
        check(saturated.food() == 20, "hidden saturation pays before visible hunger");
        near(DamageRoutingMath.ward(10, .3, 1, 0, 0, 0).remainingDamage(), 10);
        near(DamageRoutingMath.ward(10, .3, 1, 1, 0, 0).remainingDamage(), 9);
        near(DamageRoutingMath.ward(10, 0, 1, 20, 20, 0).remainingDamage(), 10);
        near(DamageRoutingMath.wardProtection(20, 40, .3), 20 * .3 / .7);
        near(DamageRoutingMath.wardProtection(20, 2, .3), 2);
        near(DamageRoutingMath.scaleShare(.3, 0), 0);
        near(DamageRoutingMath.scaleShare(.3, 1), .3);
        near(DamageRoutingMath.scaleShare(.3, 2), .6 / 1.3);
        check(Double.isFinite(DamageRoutingMath.scaleShare(.9, Double.MAX_VALUE)), "extreme rank scaling is finite");
        rejects(() -> DamageRoutingMath.ward(1, 1, 1, 20, 0, 0));
        rejects(() -> DamageRoutingMath.scaleShare(.3, Double.NaN));
        int food = 20; double prepaid = 0, remaining = 0;
        for (int i = 0; i < 100; i++) {
            var small = DamageRoutingMath.ward(.1, .3, 1, food, 0, prepaid);
            food = small.food(); prepaid = small.prepaidFood(); remaining += small.remainingDamage();
        }
        near(remaining, 7); near(food + prepaid, 17);
        Random random = new Random(751);
        for (int i = 0; i < 10_000; i++) {
            int available = random.nextInt(21); double sat = random.nextDouble() * available;
            double carry = random.nextDouble(), share = random.nextDouble() * .9;
            double unit = .01 + random.nextDouble() * 10, hit = random.nextDouble() * 100;
            var result = DamageRoutingMath.ward(hit, share, unit, available, sat, carry);
            check(result.remainingDamage() + 1e-9 >= hit * (1 - share), "health always pays its unredirected fraction");
            near(hit - result.remainingDamage(),
                    (available + sat + carry - result.food() - result.saturation() - result.prepaidFood()) * unit);
            near(DamageRoutingMath.wardProtection(20, 40, DamageRoutingMath.scaleShare(share, .5)),
                    Math.min(40, 20 * share / (1 - share) * .5));
        }
    }
    private static void meals() {
        for (int nutrition : new int[]{1, 2, 4, 8}) {
            for (int food = 0; food < 18; food++)
                check(AutomaticMealPolicy.useful(true, food, nutrition, false, 20, 18),
                        "an injured starving owner accepts useful meals even when one cannot reach regeneration");
        }
        int hunger = 0, meals = 0;
        while (AutomaticMealPolicy.useful(true, hunger, 8, false, 20, 18)) {
            hunger = Math.min(20, hunger + 8); meals++;
        }
        check(meals == 3 && hunger == 20, "successive steaks recover from an empty bar");
        check(!AutomaticMealPolicy.useful(true, 18, 8, false, 20, 18), "stop once natural regeneration is enabled");
        check(!AutomaticMealPolicy.useful(true, 0, 8, true, 20, 18), "never interrupt manual use");
        check(!AutomaticMealPolicy.useful(true, 0, 0, false, 20, 18), "zero nutrition is not a meal");
        check(AutomaticMealPolicy.useful(false, 15, 8, false, 20, 18), "healthy meal wastes less than half");
        check(!AutomaticMealPolicy.useful(false, 16, 8, false, 20, 18), "healthy meal does not waste half");
        rejects(() -> AutomaticMealPolicy.useful(true, 0, 8, false, 20, 21));
    }
    private static void quietFeedback() {
        var state = new DamageFeedbackState();
        check(!state.consumeQuietUpdate(), "unmarked health update stays native");
        state.observe(true);
        check(state.consumeQuietUpdate(), "quiet payment suppresses its own health-sync flinch");
        check(!state.consumeQuietUpdate(), "quiet flag cannot leak into the next update");
        state.observe(true); state.observe(false);
        check(!state.consumeQuietUpdate(), "a real hit after a payment keeps feedback");
        state.observe(false); state.observe(true);
        check(!state.consumeQuietUpdate(), "a real hit before a payment also keeps feedback");
        for (int i = 0; i < 200; i++) {
            state.observe(true); state.observe(true);
            check(state.consumeQuietUpdate(), "overlapping payments stay quiet across an entire delay window");
        }
        state.observe(false); check(!state.consumeQuietUpdate(), "next real hit is not muted");
        headerLayout();
        var hud = new DamageRoutingState();
        check(!hud.recorded(), "no fictional hit at startup");
        near(hud.lastReduction(), 0);
        hud.record(10, 7); near(hud.incoming(), 10); near(hud.immediate(), 7); near(hud.lastReduction(), 3);
        hud.record(2, 2); near(hud.incoming(), 2); near(hud.immediate(), 2); near(hud.lastReduction(), 0);
        hud.record(9, 4); near(hud.lastReduction(), 5);
        hud.record(Double.NaN, 0); near(hud.lastReduction(), 5);
        hud.record(0, 0); near(hud.lastReduction(), 5);
        hud.record(4, 8); near(hud.immediate(), 4); near(hud.lastReduction(), 0);
        hud.record(6, -1); near(hud.immediate(), 0); near(hud.lastReduction(), 6);
        hud.clear(); check(!hud.recorded(), "runtime cleanup removes stale hit diagnostics");
        near(hud.lastReduction(), 0);
        check(!hud.recent(100, 100), "no card before any routed hit");
        hud.record(10, 7, 100);
        check(hud.recent(100, 100) && hud.recent(199, 100), "a routed hit has its own bounded visibility");
        check(!hud.recent(200, 100) && !hud.recent(99, 100), "expiry and a reversed clock cannot retain a card");
        hud.record(2, 2, 199);
        near(hud.lastReduction(), 0);
        check(hud.recent(250, 100), "an actual zero-prevention hit refreshes presentation");
        hud.record(Double.NaN, 0, 290);
        check(!hud.recent(300, 100), "invalid observations do not refresh presentation");
        hud.clear();
        check(!hud.recent(199, 100), "cleanup removes the presentation window");
    }
    private static void headerLayout() {
        var ordinary = com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudLayout.header(64, 36);
        near(ordinary.scale(), 1);
        var paired = com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudLayout.header(83, 66);
        check(paired.titleLimit() >= 83 && paired.badgeLimit() >= 66, "standard numeric headers fit without truncation");
        check(com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudLayout.height(1) == 39,
                "one-detail-line card retains the standard compact height");
        for (int title = 0; title <= 300; title += 3) {
            for (int badge = 0; badge <= 180; badge += 3) {
                var layout = com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudLayout.header(title, badge);
                check(layout.scale() >= .8f && layout.scale() <= 1, "header scaling has a readability bound");
                check(layout.titleLimit() + Math.min(badge, layout.badgeLimit()) < layout.rightEdge(),
                        "fitted header fields cannot overlap");
                check(layout.rightEdge() * layout.scale() <= 144.001, "header cannot run past card padding");
            }
        }
    }
    private static void queues() {
        var queue = new LinearDamageQueue<String>();
        check(queue.enqueue("zombie", 20, 200), "queue one hit");
        near(queue.nextPayment(), .1); double paid = 0;
        for (int tick = 0; tick < 200; tick++) { for (var payment : queue.advance()) paid += payment.amount(); }
        near(paid, 20); check(queue.isEmpty(), "exact final payout closes the obligation");
        queue.enqueue("a", 20, 200); for (int tick = 0; tick < 100; tick++) queue.advance();
        queue.enqueue("b", 20, 200); near(queue.total(), 30); near(queue.nextPayment(), .2);
        near(queue.purge(.25), 7.5); near(queue.total(), 22.5);
        double unchanged = queue.total(); queue.settle(payment -> false); near(queue.total(), unchanged);
        check(queue.ticksRemaining() == 200, "native rejection pauses the window");
        queue.clear(); queue.enqueue("a", 8, 2);
        queue.settleAmount(payment -> payment.amount() / 2); near(queue.total(), 6);
        queue.settleAmount(payment -> payment.amount() / 2); near(queue.total(), 3);
        queue.settleAmount(LinearDamageQueue.Payment::amount); check(queue.isEmpty(), "partial native prevention leaves unpaid debt");
        queue.enqueue("a", 1, 1); queue.enqueue("b", 1, 1);
        queue.settle(payment -> { queue.clear(); return true; }); check(queue.isEmpty(), "death callback cannot resurrect snapshot debt");
        for (int i = 0; i < LinearDamageQueue.MAX_ENTRIES; i++) check(queue.enqueue("source" + i, 1, 200), "within bounded capacity");
        check(!queue.enqueue("overflow", 1, 200), "full ledger reports overflow rather than dropping a hit");
        check(queue.enqueue("source0", 1, 200), "same-source same-deadline merging at capacity");
        near(queue.total(), LinearDamageQueue.MAX_ENTRIES + 1);
        queue.purge(1); check(queue.isEmpty(), "full purge clears obligations");
        rejects(() -> queue.enqueue("bad", Double.NaN, 200)); rejects(() -> queue.enqueue("bad", 1, 0));
        rejects(() -> queue.purge(-1));
    }
    private static void trauma() {
        // Explicit fixtures: P is applied BOTH to incoming HP and the prevented damage cost.
        var small = DamageRoutingMath.ceiling(2, 20, 20, 0, .75, 1);
        near(small.healthDamage(), 1.5); near(small.convertedDamage(), .5);
        near(small.traumaAdded() * 20, .375);
        var first = DamageRoutingMath.ceiling(20, 40, 40, 0, .75, 1);
        near(first.healthDamage(), 15); near(first.convertedDamage(), 5);
        near(first.traumaAdded() * 40, 3.75);
        double penalty = first.traumaAdded();
        float maximum = (float)(40 * (1 - penalty)), hp = 25;
        near(maximum, 36.25);
        var second = DamageRoutingMath.ceiling(4, maximum, hp, penalty, .75, 1);
        near(second.healthDamage(), 3); near(second.convertedDamage(), 1);
        near(second.traumaAdded() * 40, .75);
        penalty += second.traumaAdded(); hp -= (float)second.healthDamage();
        near(40 * (1 - penalty), 35.5); near(hp, 22);
        penalty = 0; maximum = (float)(40 * (1 - penalty));
        near(maximum, 40); near(hp, 22); // restored capacity, NOT healing
        var fatal = DamageRoutingMath.ceiling(1000, 20, 20, 0, .75, 1);
        near(fatal.healthDamage(), 750); // this is not an anti-one-shot cap anymore
        check(fatal.traumaAdded() <= .95, "capacity cannot cross its native minimum");
        var minimum = DamageRoutingMath.ceiling(2, 1, 1, .95, .75, 1);
        near(minimum.healthDamage(), 1.5); near(minimum.convertedDamage(), .5);
        near(minimum.traumaAdded(), 0); // no room for cost, but no prevention leakage
        var disabled = DamageRoutingMath.ceiling(2, 20, 20, 0, 1, 1);
        near(disabled.healthDamage(), 2); near(disabled.convertedDamage(), 0); near(disabled.traumaAdded(), 0);
        near(DamageRoutingMath.scaleTakenFraction(.75, 0), 1);
        near(DamageRoutingMath.scaleTakenFraction(.75, 1), .75);
        near(DamageRoutingMath.scaleTakenFraction(.75, 2), .6);
        near(DamageRoutingMath.scaleTakenFraction(1, 2), 1);
        near(DamageRoutingMath.healthLost(20, 15), 5);
        near(DamageRoutingMath.healthLost(20, 25), 0);
        near(DamageRoutingMath.healthLost(20, -10), 20);
        near(DamageRoutingMath.healthLost(20, Double.NaN), 0);
        Random random = new Random(28);
        for (int i = 0; i < 20_000; i++) {
            double fraction = random.nextDouble() * .9, normal = 10 + random.nextDouble() * 1000;
            float max = (float)(normal * (1 - fraction));
            float health = (float)(random.nextDouble() * max), hit = (float)(random.nextDouble() * 5000);
            double taken = .01 + random.nextDouble() * .99;
            var result = DamageRoutingMath.ceiling(hit, max, health, fraction, taken, 1);
            double hpAfter = Math.max(0, health - (float)result.healthDamage());
            check(result.healthDamage() >= 0 && result.healthDamage() <= hit * taken, "fraction controls damage regardless of HP or prior penalty");
            check(result.traumaAdded() >= 0 && fraction + result.traumaAdded() < 1, "finite native maximum-health penalty");
            check((float)(max / (1 - fraction) * (1 - fraction - result.traumaAdded())) >= hpAfter,
                    "capacity cost cannot remove surviving HP");
            check((float)(normal * (1 - fraction - result.traumaAdded())) + Math.ulp(max) >= Math.max(1, hpAfter),
                    "native capacity minimum and prior attribute rounding stay bounded");
            check(result.traumaAdded() * (max / (1 - fraction)) <= result.convertedDamage() * taken + 1e-8,
                    "the cost uses the same fraction and never exceeds it");
            near(result.convertedDamage(), hit - result.healthDamage());
            // On surviving hits away from the native floor, the requested cost is paid exactly.
            if (hpAfter > 1 && max - result.convertedDamage() * taken > hpAfter + 2 * Math.ulp(max))
                near(result.traumaAdded() * max / (1 - fraction), result.convertedDamage() * taken);
        }
        rejects(() -> DamageRoutingMath.ceiling(1, 20, 20, 0, 0, 1));
        rejects(() -> DamageRoutingMath.ceiling(1, 20, 20, 0, Double.NaN, 1));
        rejects(() -> DamageRoutingMath.scaleTakenFraction(.75, -1));
    }
    private static void settings() {
        var s = VitalityDamageBalanceSettings.defaults(); s.validate(); checks++;
        rejects(() -> new VitalityDamageBalanceSettings(s.hungerWard(), s.staggeredPain(), s.damageCeiling(),
                new VitalityDamageBalanceSettings.MetabolicConversion(2, .5), s.painPurge(), s.adrenaline()));
        rejects(() -> new VitalityDamageBalanceSettings.HungerWard(Double.POSITIVE_INFINITY, .3));
        rejects(() -> new VitalityDamageBalanceSettings.HungerWard(1, 1));
        rejects(() -> new VitalityDamageBalanceSettings.HungerWard(1, Double.NaN));
        rejects(() -> new VitalityDamageBalanceSettings.StaggeredPain(0));
        rejects(() -> new VitalityDamageBalanceSettings.DamageCeiling(0, 200));
        rejects(() -> new VitalityDamageBalanceSettings.DamageCeiling(.75, 0));
    }
    private static void near(double actual, double expected) {
        check(Math.abs(actual - expected) <= 1e-8 * Math.max(1, Math.abs(expected)), actual + " != " + expected);
    }
    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid state was accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
