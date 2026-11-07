package com.mistaboom.essence_ascendance.gameplay;

import java.util.List;
import java.util.UUID;

/** Tests policy decisions and rejection boundaries only; this does not implement later trackers. */
public final class OutcomePolicyTest {
    private static final UUID ACTOR = new UUID(0, 1), OTHER = new UUID(0, 2);
    private static final String OVERWORLD = "minecraft:overworld";
    private static int checks;

    public static void main(String[] args) {
        fight(); cats(); day(); death(); movement();
        System.out.println("Outcome policies: " + checks + " checks passed");
    }

    private static void fight() {
        check(OutcomePolicies.sameFight(500, 699, 200, true), "Last active combat tick remains in the fight");
        check(!OutcomePolicies.sameFight(500, 700, 200, true), "Exact combat expiry ends the fight");
        check(OutcomePolicies.sameFight(650, 700, 200, true), "New accepted hostile damage renews the interval");
        check(!OutcomePolicies.sameFight(500, 550, 40, true)
                && OutcomePolicies.sameFight(500, 550, 80, true), "The live supplied timeout controls the decision");
        check(!OutcomePolicies.sameFight(-1, 10, 200, true), "Missing accepted hostile damage cannot start a fight");
        check(!OutcomePolicies.sameFight(500, 499, 200, true), "Backward clocks cannot revive a fight");
        check(!OutcomePolicies.sameFight(500, 501, 200, false), "Dimension change ends the fight");
        check(!OutcomePolicies.sameFight(500, 500, 0, true), "Invalid timeout fails closed");
    }

    private static OutcomePolicies.CatCandidate cat(String id, UUID owner, String dimension,
                                                    boolean alive, boolean loaded, boolean tamed, double square) {
        return new OutcomePolicies.CatCandidate(UUID.fromString(id), owner, dimension, alive, loaded, tamed, square);
    }

    private static void cats() {
        var atBoundary = cat("00000000-0000-0000-0000-000000000008", ACTOR, OVERWORLD, true, true, true, 64);
        var outside = cat("00000000-0000-0000-0000-000000000009", ACTOR, OVERWORLD, true, true, true, 64.000001);
        check(OutcomePolicies.nearestOwnedCat(ACTOR, OVERWORLD, List.of(atBoundary, outside)).orElseThrow().equals(atBoundary.id()),
                "Eight-block 3D boundary is included; farther candidates are excluded");
        var first = cat("7fffffff-ffff-ffff-ffff-ffffffffffff", ACTOR, OVERWORLD, true, true, true, 9);
        var second = cat("80000000-0000-0000-0000-000000000000", ACTOR, OVERWORLD, true, true, true, 9);
        check(OutcomePolicies.nearestOwnedCat(ACTOR, OVERWORLD, List.of(second, atBoundary, first)).orElseThrow().equals(first.id()),
                "Nearest distance then lexical UUID resolves ties regardless of signed UUID ordering");
        check(OutcomePolicies.nearestOwnedCat(ACTOR, OVERWORLD, List.of(first, second)).equals(
                OutcomePolicies.nearestOwnedCat(ACTOR, OVERWORLD, List.of(second, first))), "Selection ignores candidate iteration order");
        var rejected = List.of(
                cat("00000000-0000-0000-0000-000000000011", OTHER, OVERWORLD, true, true, true, 1),
                cat("00000000-0000-0000-0000-000000000012", ACTOR, "minecraft:the_nether", true, true, true, 1),
                cat("00000000-0000-0000-0000-000000000013", ACTOR, OVERWORLD, false, true, true, 1),
                cat("00000000-0000-0000-0000-000000000014", ACTOR, OVERWORLD, true, false, true, 1),
                cat("00000000-0000-0000-0000-000000000015", ACTOR, OVERWORLD, true, true, false, 1),
                cat("00000000-0000-0000-0000-000000000016", ACTOR, OVERWORLD, true, true, true, Double.NaN),
                cat("00000000-0000-0000-0000-000000000017", ACTOR, OVERWORLD, true, true, true, -1), outside);
        for (var invalid : rejected)
            check(OutcomePolicies.nearestOwnedCat(ACTOR, OVERWORLD, List.of(invalid)).isEmpty(),
                    "Foreign, unavailable, wild, malformed, or out-of-range cat cannot be selected: " + invalid.id());
    }

    private static void day() {
        check(OutcomePolicies.sameMinecraftDay(0, 23_999), "All observations before the day boundary share a bucket");
        check(!OutcomePolicies.sameMinecraftDay(23_999, 24_000), "Exact day boundary opens a new collection");
        check(!OutcomePolicies.sameMinecraftDay(18_000, 24_000), "A sleep jump resets the calendar collection");
        check(!OutcomePolicies.sameMinecraftDay(10_000, 9_999), "Backward clock within the same day invalidates the session");
        check(OutcomePolicies.minecraftDay(-1) == -1 && OutcomePolicies.minecraftDay(-24_000) == -1
                && OutcomePolicies.minecraftDay(-24_001) == -2, "Negative dayTime uses floor division rather than truncation");
        check(!OutcomePolicies.sameMinecraftDay(-1, 0), "Crossing zero changes negative calendar day");
    }

    private static void death() {
        check(OutcomePolicies.endsLife(true, false, false), "Committed unprevented death ends a life");
        check(!OutcomePolicies.endsLife(false, false, false), "Lethal damage or respawn alone is not committed death");
        check(!OutcomePolicies.endsLife(true, true, false), "Prevention cannot masquerade as a life boundary");
        check(!OutcomePolicies.endsLife(true, false, true), "Alive Spirit Walk reformation cannot end a life");
    }

    private static void movement() {
        using(false, true, true, false, 0, 16, OutcomePolicies.MovementDecision.WAIT, "No sample alone does not prove stopping");
        using(true, true, true, false, 0, 16, OutcomePolicies.MovementDecision.END, "Accepted stationary sample interrupts sprint");
        using(true, true, true, false, 0.1, 16, OutcomePolicies.MovementDecision.CONTINUE, "Accepted positive movement continues sprint");
        using(true, true, true, false, 16, 16, OutcomePolicies.MovementDecision.CONTINUE, "Live displacement maximum is inclusive");
        using(true, true, true, false, 16.01, 16, OutcomePolicies.MovementDecision.END, "Excess displacement ends rather than clipping distance");
        using(true, true, true, false, 2, 1, OutcomePolicies.MovementDecision.END, "Changing live limit changes eligibility");
        using(true, true, true, false, Double.NaN, 16, OutcomePolicies.MovementDecision.END, "Nonfinite displacement is rejected");
        using(true, false, true, false, 1, 16, OutcomePolicies.MovementDecision.END, "Mode loss, mount or death ends the session");
        using(false, false, true, false, 0, 16, OutcomePolicies.MovementDecision.END, "Mode loss also ends a session without a movement packet");
        using(true, true, false, false, 1, 16, OutcomePolicies.MovementDecision.END, "Dimension changes end the session");
        using(true, true, true, true, 1, 16, OutcomePolicies.MovementDecision.END, "Even a short teleport cannot count as movement");
    }

    private static void using(boolean observed, boolean mode, boolean dimension, boolean teleport,
                              double distance, double limit, OutcomePolicies.MovementDecision expected, String message) {
        check(OutcomePolicies.sprintContinuity(observed, mode, dimension, teleport, distance, limit) == expected, message);
    }

    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
