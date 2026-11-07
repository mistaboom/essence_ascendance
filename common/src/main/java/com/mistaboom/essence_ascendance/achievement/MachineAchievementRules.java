package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.gameplay.CommittedReceiptWindow;
import com.mistaboom.essence_ascendance.gameplay.DissolutionOutcome;
import com.mistaboom.essence_ascendance.gameplay.MachineOutcome;
import java.util.function.IntConsumer;

/** Event-indexed machine rules. No inventory inference, tick scans, or award-dependent facts. */
public final class MachineAchievementRules {
    private final CommittedReceiptWindow receipts = new CommittedReceiptWindow();
    public boolean consume(MachineOutcome fact, long now, boolean eligible, AchievementProgress state, IntConsumer award) {
        if (fact == null || !eligible || !receipts.accept(fact.actor(), fact.getClass().getSimpleName() + ":" + fact.actionId(), fact.committedTick(), now)) return false;
        switch (fact) {
            case MachineOutcome.Channel p -> award.accept(24);
            case MachineOutcome.Infrastructure p -> {
                if (p.activatedPylon() && p.functioningPylons() > 0) award.accept(46);
                if (p.runningCrucible() && p.activeSlotLimit() > 0 && p.functioningPylons() >= p.activeSlotLimit()) award.accept(57);
            }
            case MachineOutcome.Infusion p -> {
                award.accept(49);
                if (p.result() == MachineOutcome.Result.FILLED_ESSENTIUM && state.machineCategories(p.positiveCategoryMask())) award.accept(51);
                if (p.sourceCrucibleIdentity() != null && state.infuserSource(p.sourceCrucibleIdentity(), p.machineIdentity())) award.accept(56);
                if (p.finalTier() == 5 && p.result() == MachineOutcome.Result.WEAPON_UPGRADE) award.accept(63);
                if (p.finalTier() == 5 && p.result() == MachineOutcome.Result.ROD_UPGRADE) award.accept(73);
                if (state.machineDimension(p.dimension())) award.accept(197);
            }
        }
        return true;
    }
    public boolean consume(DissolutionOutcome fact, long now, boolean eligible, AchievementProgress state, IntConsumer award) {
        if (fact == null || !eligible || !receipts.accept(fact.operator(), "dissolution:" + fact.actionId(), fact.committedTick(), now)) return false;
        if (state.dissolvedItems(fact.consumedInputItems())) award.accept(199);
        if (fact.machineIdentity() != null && state.dissolvedDay(fact.machineIdentity(), fact.overworldDayTime(), fact.positiveCategoryMask())) award.accept(190);
        return true;
    }
}
