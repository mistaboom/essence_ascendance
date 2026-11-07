package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.gameplay.CommittedReceiptWindow;
import com.mistaboom.essence_ascendance.gameplay.SurvivalOutcome;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.IntConsumer;

/** Small consumers of successful skill/health commits; never activates skills or changes cooldowns. */
public final class SurvivalAchievementRules {
    private final CommittedReceiptWindow receipts = new CommittedReceiptWindow();
    public boolean consume(SurvivalOutcome fact, long now, boolean eligible, AchievementProgress state, IntConsumer award) {
        if (fact == null || state == null || (!eligible && !(fact instanceof SurvivalOutcome.Death) && !(fact instanceof SurvivalOutcome.CeilingDebt))) return false;
        var c = fact.context();
        // Native death hooks are exactly-once and reset correctness must not depend on award-window capacity.
        if (fact instanceof SurvivalOutcome.Death) {
            if (c.committedTick() < 0 || now < c.committedTick() || now - c.committedTick() >= CommittedReceiptWindow.LIFETIME_TICKS) return false;
            state.realDeath(); return true;
        }
        if (!eligible && fact instanceof SurvivalOutcome.CeilingDebt) {
            if (now < c.committedTick() || now - c.committedTick() >= CommittedReceiptWindow.LIFETIME_TICKS) return false;
            state.disarmCeilingDebt(); return true;
        }
        // One native operation may repair equipment and finish a meal. Kinds remain separate commits.
        String receipt = UUID.nameUUIDFromBytes((fact.getClass().getSimpleName() + "|" + c.actionId())
                .getBytes(StandardCharsets.UTF_8)).toString();
        if (!receipts.accept(c.actor(), receipt, c.committedTick(), now)) return false;
        switch (fact) {
            case SurvivalOutcome.SecondWind ignored -> {
                award.accept(176);
                if (state.secondWind()) award.accept(177);
            }
            case SurvivalOutcome.SpiritReform reform -> { if (reform.alive()) award.accept(107); }
            case SurvivalOutcome.AutomaticMeal meal -> { if (meal.completedMeals() == 2) award.accept(110); }
            case SurvivalOutcome.MirroredEffect reflected -> {
                if (reflected.sourceWitch() && reflected.harmful() && reflected.applied()
                        && reflected.source().equals(reflected.target())) award.accept(114);
            }
            case SurvivalOutcome.CeilingDebt debt -> {
                if (state.ceilingDebt(debt.before(), debt.after(), debt.capacityReduced())) award.accept(193);
            }
            case SurvivalOutcome.Adaptation adaptation -> {
                if (adaptation.maximum() > 0 && adaptation.previous() < adaptation.maximum()
                        && adaptation.amount() >= adaptation.maximum()) award.accept(200);
            }
            case SurvivalOutcome.Repair repair -> {
                if (repair.amount() > 0 && repair.y() < 0 && state.repairedFood(repair.food())) award.accept(209);
            }
            case SurvivalOutcome.Death ignored -> { } // Handled before the bounded success receipt guard.
        }
        return true;
    }
}
