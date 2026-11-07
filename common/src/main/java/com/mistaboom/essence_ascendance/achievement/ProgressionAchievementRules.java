package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.gameplay.CommittedReceiptWindow;
import com.mistaboom.essence_ascendance.gameplay.ProgressionOutcome;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.IntConsumer;

/** Event-indexed rules; no tick scan or historical action reconstruction. */
public final class ProgressionAchievementRules {
    private final CommittedReceiptWindow receipts = new CommittedReceiptWindow();
    public boolean consume(ProgressionOutcome fact, long now, boolean eligible, AchievementProgress state, IntConsumer award) {
        if (fact == null || !eligible) return false;
        // Several distinct committed facts may share an existing root action. Each typed detail deduplicates separately.
        String detail = switch (fact) {
            case ProgressionOutcome.SkillPurchased p -> p.skillId().toString();
            case ProgressionOutcome.BonusPurchased p -> p.statId().toString();
            case ProgressionOutcome.ActivityContributed p -> p.categoryId() + ":" + p.activityId();
            default -> "";
        };
        String receipt = UUID.nameUUIDFromBytes((fact.actionId() + "|" + fact.getClass().getSimpleName() + "|" + detail)
                .getBytes(StandardCharsets.UTF_8)).toString();
        if (!receipts.accept(fact.actor(), receipt, fact.committedTick(), now)) return false;
        switch (fact) {
            case ProgressionOutcome.NexusUsed p -> { if (p.actor().equals(p.builder())) award.accept(19); }
            case ProgressionOutcome.SkillPurchased p -> {
                if (p.oldRank() == 0) award.accept(26);
                if (p.categoryId().toString().equals("essence_ascendance:utility") && state.utilityPurchase(p.skillId().toString())) award.accept(208);
            }
            case ProgressionOutcome.BonusPurchased p -> {
                if (p.statId().toString().equals("essence_ascendance:max_health") && p.newMaximumHealth() > p.oldMaximumHealth()
                        && p.purchasedBonusIncrease() > 0) award.accept(169);
            }
            case ProgressionOutcome.Crafted p -> { if (p.itemId().toString().equals("essence_ascendance:ascendance_matrix")) award.accept(28); }
            case ProgressionOutcome.ActivityContributed p -> {
                if (state.activity(p.chapterId(), p.categoryId(), p.activityId())) award.accept(30);
            }
            case ProgressionOutcome.SealsCompleted p -> { if (p.playerTierOrder() > 0) award.accept(36); }
            case ProgressionOutcome.Ascended p -> {
                tierMilestones(p.toTierOrder(), award);
                state.observeTier(Math.min(5, p.toTierOrder()));
                state.chapter(""); // Only this chapter-scoped collection resets; lifetime purchases remain.
                if (p.manual() && p.fromTierOrder() > 0 && p.completedSeals() == 6) award.accept(36);
                if (p.night() && p.fullFunctionalArmor()) award.accept(205);
            }
        }
        return true;
    }
    public static void tierMilestones(int tier, IntConsumer award) {
        for (int level = 1; level <= Math.min(5, tier); level++) award.accept(30 + level);
    }
}
