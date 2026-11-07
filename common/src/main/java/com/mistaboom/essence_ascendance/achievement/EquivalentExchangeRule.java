package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.gameplay.CommittedReceiptWindow;
import com.mistaboom.essence_ascendance.gameplay.DissolutionOutcome;
import java.util.UUID;
import java.util.function.Predicate;

/** Only committed positive dissolution can satisfy ID 22. No balance/history backfill. */
public final class EquivalentExchangeRule {
    private final CommittedReceiptWindow receipts = new CommittedReceiptWindow();

    public boolean consume(DissolutionOutcome outcome, long now, Predicate<UUID> eligible, Predicate<UUID> award) {
        return outcome != null && outcome.consumedItems() > 0 && outcome.creditedUnits().signum() > 0
                && outcome.operator() != null && eligible.test(outcome.operator())
                && receipts.accept(outcome.operator(), outcome.actionId(), outcome.committedTick(), now)
                && award.test(outcome.operator());
    }
}
