package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.gameplay.CommittedReceiptWindow;
import com.mistaboom.essence_ascendance.gameplay.GatheringOutcome;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntConsumer;

/** Bounded consumers of real harvest, rhythm and delivered ore facts. */
public final class GatheringAchievementRules {
    private final CommittedReceiptWindow receipts = new CommittedReceiptWindow();
    private final Map<UUID, Continuation> continuations = new HashMap<>();
    private record Continuation(String rhythm, int blocks, long tick) { }
    public boolean consume(GatheringOutcome fact, long now, boolean eligible, AchievementProgress state, IntConsumer award) {
        if (fact == null || !eligible || state == null) return false;
        var c = fact.context();
        String receipt = UUID.nameUUIDFromBytes((fact.getClass().getSimpleName() + "|" + c.actionId())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        if (!receipts.accept(c.actor(), receipt, c.committedTick(), now)) return false;
        // The catalog caps the ordinary momentum timeout at 72,000 ticks. Only active actors remain.
        continuations.values().removeIf(value -> now < value.tick() || now - value.tick() > 72_000);
        switch (fact) {
            case GatheringOutcome.Momentum momentum -> {
                var previous = continuations.get(c.actor());
                if (!(momentum.maximumSpeed() > 0) || !momentum.eligibleOrigin() || momentum.after() < 1) {
                    continuations.remove(c.actor()); break;
                }
                // Reaching the cap is a checkpoint, never the first continuation block.
                if (momentum.before() < 1) {
                    if (continuations.size() < 4096 || previous != null)
                        continuations.put(c.actor(), new Continuation(momentum.rhythm(), 0, now));
                } else if (previous != null && previous.rhythm().equals(momentum.rhythm()) && previous.blocks() < 20) {
                    int next = previous.blocks() + 1;
                    continuations.put(c.actor(), new Continuation(momentum.rhythm(), next, now));
                    if (next == 20) award.accept(122);
                }
            }
            case GatheringOutcome.Harvest harvest -> {
                if (harvest.generatedOrigin() && harvest.correctTool() && harvest.underwater() && harvest.aquaticBody()) award.accept(126);
            }
            case GatheringOutcome.BonusOre bonus -> {
                if (bonus.deliveredItems() > 0 && state.naturalBonusDimension(c.dimension())) award.accept(182);
            }
        }
        return true;
    }
    public void forget(UUID actor) { continuations.remove(actor); }
}
