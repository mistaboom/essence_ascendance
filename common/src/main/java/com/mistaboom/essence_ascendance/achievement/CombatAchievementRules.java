package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.gameplay.CombatOutcome;
import com.mistaboom.essence_ascendance.gameplay.CommittedReceiptWindow;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.IntConsumer;

/** Consumes accepted combat observations; never resolves damage or infers kills from attacks. */
public final class CombatAchievementRules {
    private final CommittedReceiptWindow receipts = new CommittedReceiptWindow();

    public boolean consume(CombatOutcome fact, long now, boolean eligible, AchievementProgress state, IntConsumer award) {
        if (fact == null || !eligible) return false;
        var context = fact.context();
        // An action may damage and kill several victims. Neither event kinds nor victim identities collapse.
        String receipt = UUID.nameUUIDFromBytes((fact.getClass().getSimpleName() + "|" + context.actionId()
                + "|" + context.victim()).getBytes(StandardCharsets.UTF_8)).toString();
        if (!receipts.accept(context.actor(), receipt, context.committedTick(), now)) return false;
        var weapon = context.weapon();
        switch (fact) {
            case CombatOutcome.Damage damage -> {
                if (weapon.glaive() && Double.isFinite(damage.loss()) && damage.loss() > 0
                        && Double.isFinite(damage.contactDistance()) && damage.contactDistance() >= 0) {
                    if (weapon.tier() >= 1 && damage.contactDistance() > 4) award.accept(136);
                    if (context.hostile() && damage.contactDistance() > 9) award.accept(138);
                    if (damage.contactDistance() >= 8) award.accept(206);
                }
            }
            case CombatOutcome.Kill kill -> {
                if (context.hostile()) {
                    if (weapon.glaive() && weapon.mode().equals("STAB")) award.accept(76);
                    // The reusable causal group counts distinct deaths, and only its threshold transition earns.
                    if (weapon.glaive() && weapon.mode().equals("SWIPE") && kill.sameActionHostileKills() == 3) award.accept(77);
                    if (weapon.ascendanceWeapon() && context.dimension().equals("minecraft:overworld")
                            && kill.actorY() < 0 && kill.victimY() < 0 && kill.noBoots() && state.barefootUndergroundKill()) award.accept(120);
                    if (kill.ledgerEligible() && state.ledgerTrackedKill()) award.accept(215);
                }
                if (context.victimType().equals("minecraft:wither") && kill.desperation()
                        && Double.isFinite(kill.healthFraction()) && kill.healthFraction() >= 0
                        && kill.healthFraction() < 0.1) award.accept(184);
            }
            case CombatOutcome.Freeze ignored -> { if (context.hostile()) award.accept(85); }
            case CombatOutcome.Pacified pacified -> {
                if (context.victimType().equals("minecraft:pillager") && pacified.targetedActor()
                        && pacified.undamagedEncounter()) award.accept(207);
            }
        }
        return true;
    }
}
