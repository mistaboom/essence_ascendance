package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.gameplay.CommittedReceiptWindow;
import com.mistaboom.essence_ascendance.gameplay.FarmingOutcome;
import java.util.function.IntConsumer;

/** Consumes only attributed skill commits, never random world growth or requested rewards. */
public final class FarmingAchievementRules {
    private final CommittedReceiptWindow receipts = new CommittedReceiptWindow();
    public boolean consume(FarmingOutcome fact, long now, boolean eligible, AchievementProgress state, IntConsumer award) {
        if (fact == null || !eligible || state == null) return false;
        var c = fact.context();
        if (!receipts.accept(c.actor(), c.actionId(), c.committedTick(), now)) return false;
        switch (fact) {
            case FarmingOutcome.CropGrowth growth -> {
                if (growth.eligibleCrop() && growth.advanced() && state.verdantCrop(growth.crop())) award.accept(128);
            }
            case FarmingOutcome.PocketReward reward -> {
                if (reward.delivered() > 0 && reward.swimming()) award.accept(131);
            }
            case FarmingOutcome.TramplePrevented trample -> {
                if (trample.matureCrop() && trample.farmlandPreserved() && trample.cropPreserved()) award.accept(204);
            }
        }
        return true;
    }
}
