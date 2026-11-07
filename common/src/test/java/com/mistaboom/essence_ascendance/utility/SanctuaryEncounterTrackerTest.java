package com.mistaboom.essence_ascendance.utility;

import java.util.UUID;

/** Executable producer-policy checks, independent of achievement awards or native entities. */
public final class SanctuaryEncounterTrackerTest {
    private static final String OVERWORLD = "minecraft:overworld", NETHER = "minecraft:the_nether";
    private static int checks;

    public static void main(String[] args) {
        transitions(); causalDamage(); boundedEvidence();
        System.out.println("Sanctuary encounter invariants passed: " + checks);
    }

    private static void transitions() {
        SanctuaryEncounterTracker tracker = new SanctuaryEncounterTracker();
        UUID target = id(1);
        check(!tracker.undamaged(target, OVERWORLD, 0), "Unknown encounter cannot assert no damage");
        tracker.started(target, OVERWORLD, 10);
        check(tracker.undamaged(target, OVERWORLD, 10), "Actual acquisition starts complete clean evidence");
        check(!tracker.undamaged(target, NETHER, 10), "Dimensions cannot merge evidence");
        tracker.ended(target, 15);
        check(!tracker.undamaged(target, OVERWORLD, 15), "Losing targeting ends encounter");
        tracker.started(target, OVERWORLD, 16);
        check(tracker.undamaged(target, OVERWORLD, 16), "New clean acquisition is a new encounter");
        tracker.remove(target);
        check(!tracker.undamaged(target, OVERWORLD, 17), "Death/unload cleanup rejects stale evidence");
        tracker.started(target, OVERWORLD, 20);
        tracker.clear();
        check(tracker.size() == 0 && !tracker.undamaged(target, OVERWORLD, 20), "Lifecycle clears active records");
    }

    private static void causalDamage() {
        SanctuaryEncounterTracker alice = new SanctuaryEncounterTracker();
        SanctuaryEncounterTracker bob = new SanctuaryEncounterTracker();
        UUID pillager = id(2), other = id(3);
        alice.started(pillager, OVERWORLD, 0);
        bob.started(pillager, OVERWORLD, 0);
        alice.damaged(other, OVERWORLD, 1);
        check(alice.undamaged(pillager, OVERWORLD, 2), "Damage to a different mob cannot poison this encounter");
        alice.damaged(pillager, OVERWORLD, 3);
        alice.damaged(pillager, OVERWORLD, 3);
        check(!alice.undamaged(pillager, OVERWORLD, 4), "Repeated accepted callback poisons exactly this victim");
        check(bob.undamaged(pillager, OVERWORLD, 4), "Another player's damage cannot poison this actor");
        check(!alice.undamaged(pillager, OVERWORLD, 1000), "Waiting out aggression delay does not erase encounter damage");
        alice.ended(pillager, 1001);
        alice.started(pillager, OVERWORLD, 1002);
        check(alice.undamaged(pillager, OVERWORLD, 1002), "Separate later encounter may be clean");

        SanctuaryEncounterTracker delayed = new SanctuaryEncounterTracker();
        delayed.damaged(pillager, OVERWORLD, 10);
        delayed.started(pillager, OVERWORLD, 11);
        check(!delayed.undamaged(pillager, OVERWORLD, 11), "Damage preceding delayed retaliation acquisition rejects");
        delayed.ended(pillager, 12);
        delayed.started(pillager, OVERWORLD, 209);
        check(!delayed.undamaged(pillager, OVERWORLD, 209), "Recent-damage interval is strict at 199 ticks");
        delayed.ended(pillager, 209);
        delayed.started(pillager, OVERWORLD, 210);
        check(delayed.undamaged(pillager, OVERWORLD, 210), "New acquisition after full 200-tick quiet interval may be clean");
    }

    private static void boundedEvidence() {
        SanctuaryEncounterTracker tracker = new SanctuaryEncounterTracker();
        UUID oldest = id(0);
        for (int i = 0; i < SanctuaryEncounterTracker.MAX_TARGETS; i++) tracker.started(id(i), OVERWORLD, 0);
        tracker.started(id(1000), OVERWORLD, 1);
        check(tracker.size() == SanctuaryEncounterTracker.MAX_TARGETS, "Target evidence is bounded");
        check(!tracker.undamaged(id(1000), OVERWORLD, 1), "Capacity exhaustion rejects unknown targets");
        check(tracker.undamaged(oldest, OVERWORLD, SanctuaryEncounterTracker.MAX_ENCOUNTER_TICKS - 1), "Encounter valid before strict TTL");
        check(!tracker.undamaged(oldest, OVERWORLD, SanctuaryEncounterTracker.MAX_ENCOUNTER_TICKS), "Expiration cannot become clean evidence");
        tracker.started(oldest, OVERWORLD, 20_000);
        check(!tracker.undamaged(oldest, OVERWORLD, 19_999), "Clock rewind invalidates evidence");
    }

    private static UUID id(long value) { return new UUID(0, value); }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
