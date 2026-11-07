package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.gameplay.CompanionOutcome;
import com.mistaboom.essence_ascendance.gameplay.CommittedReceiptWindow;
import com.mistaboom.essence_ascendance.gameplay.OutcomePolicies;
import java.util.function.IntConsumer;

/** Owns no pet simulation: consumes measured native success and ownership evidence. */
public final class CompanionAchievementRules {
    private final CommittedReceiptWindow receipts = new CommittedReceiptWindow();
    public boolean consume(CompanionOutcome fact, long now, boolean eligible, AchievementProgress state, IntConsumer award) {
        if (fact == null || state == null) return false;
        var c = fact.context();
        if (c.committedTick() < 0 || now < c.committedTick() || now - c.committedTick() >= CommittedReceiptWindow.LIFETIME_TICKS) return false;
        // Invalidations must work at receipt capacity, offline and in creative mode as well.
        if (fact instanceof CompanionOutcome.CatInvalidated invalidated) {
            state.invalidateCat(invalidated.cat()); return true;
        }
        if (!eligible) return false;
        if (fact instanceof CompanionOutcome.BondedKill kill) {
            if (!kill.hostile() || !kill.empoweredAtContact() || !kill.empoweredAtCommit()
                    || !c.actor().equals(kill.ownerAtContact()) || !c.actor().equals(kill.ownerAtCommit())) return false;
            if (!receipts.accept(c.actor(), "bonded-kill:" + kill.victim(), c.committedTick(), now)) return false;
            award.accept(118); return true;
        }
        var kill = (CompanionOutcome.CatKill) fact;
        if (kill.cats().size() > 64 || !receipts.accept(c.actor(), "cat-kill:" + kill.victim(), c.committedTick(), now)) return false;
        var selected = state.selectedCat();
        if (selected == null) selected = OutcomePolicies.nearestOwnedCat(c.actor(), c.dimension(),
                kill.cats().stream().map(CompanionOutcome.CatFact::candidate).toList()).orElse(null);
        if (selected == null) return false;
        final var bound = selected;
        var evidence = kill.cats().stream().filter(cat -> cat.candidate().id().equals(bound)).findFirst().orElse(null);
        if (evidence == null || !evidence.completeUndamagedEvidence()
                || OutcomePolicies.nearestOwnedCat(c.actor(), c.dimension(), java.util.List.of(evidence.candidate())).isEmpty()) return false;
        if (state.catKill(selected)) award.accept(119);
        return true;
    }
}
