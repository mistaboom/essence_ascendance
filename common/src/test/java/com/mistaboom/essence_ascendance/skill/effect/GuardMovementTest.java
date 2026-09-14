package com.mistaboom.essence_ascendance.skill.effect;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Physical sweep and continuous-charge simulations, using the same selection implementation as the real move wrapper. */
public final class GuardMovementTest {
    private static int checks;
    private static final AABB BODY = new AABB(-.3, 0, -.3, .3, 1.8, .3);
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        slowdown(); sweep(); ledger(); stagger();
        System.out.println("Guard movement contracts passed: " + checks);
    }
    private static void slowdown() {
        check(close(GuardMobilityController.resolveMultiplier(.2F, 0, .85), .88), "Most shield slowdown removed once");
        check(close(GuardMobilityController.resolveMultiplier(.2F, 50, .85), .88), "Skill does not multiply equipment movement");
        check(close(GuardMobilityController.resolveMultiplier(.2F, 100, .85), 1), "Higher invested equipment remains useful");
        check(close(GuardMobilityController.resolveMultiplier(.2F, 0, 0), .2), "Inactive native input unchanged");
        for (int investment = 0; investment <= 100; investment++) for (int skill = 0; skill <= 100; skill++) {
            float result = GuardMobilityController.resolveMultiplier(.2F, investment, skill / 100.0);
            check(result >= .2 && result <= 1 && Float.isFinite(result), "All removal combinations bounded without general speed attribute");
        }
    }
    private static CollisionAttackService.Candidate candidate(long id, double x, double z, boolean eligible, boolean visible) {
        return new CollisionAttackService.Candidate(new UUID(0, id), new AABB(x - .3, 0, z - .3, x + .3, 1.8, z + .3), eligible, visible);
    }
    private static List<CollisionAttackService.Contact> select(Vec3 movement, int budget, List<CollisionAttackService.Candidate> candidates) {
        return CollisionAttackService.select(new CollisionAttackService.Shape(BODY, movement), .2, 2, budget, candidates);
    }
    private static void sweep() {
        var ahead = candidate(1, 0, .8, true, true);
        check(select(new Vec3(0, 0, .3), 3, List.of(ahead)).size() == 1, "Forward sweep hits physical front");
        check(select(new Vec3(0, 0, .1999), 3, List.of(ahead)).isEmpty(), "Speed just below threshold rejected");
        check(select(new Vec3(0, 0, .2), 3, List.of(candidate(1, 0, .7, true, true))).size() == 1, "Exact speed threshold accepted");
        check(select(new Vec3(0, 0, 2.00001), 3, List.of(ahead)).isEmpty(), "Teleport/excess movement cannot become a charge");
        check(select(new Vec3(Double.NaN, 0, .3), 3, List.of(ahead)).isEmpty(), "Nonfinite movement rejected");
        check(select(new Vec3(0, 0, .3), 3, List.of(candidate(1, 0, -.8, true, true))).isEmpty(), "Behind misses");
        check(select(new Vec3(0, 0, .3), 3, List.of(candidate(1, .6001, .8, true, true))).isEmpty(), "Outside physical lateral edge misses");
        check(select(new Vec3(0, 0, .3), 3, List.of(candidate(1, 0, .2, true, true))).isEmpty(), "Passive existing overlap is not an intentional hit");
        check(select(Vec3.ZERO, 3, List.of(ahead)).isEmpty(), "Stationary guard is never an aura");
        check(select(new Vec3(0, 0, .3), 3, List.of(candidate(1, 0, .8, false, true))).isEmpty(), "Teams/PvP/control eligibility rejects before contact");
        check(select(new Vec3(0, 0, .3), 3, List.of(candidate(1, 0, .8, true, false))).isEmpty(), "Occluded target cannot be hit through terrain");
        check(select(new Vec3(0, 0, .1), 3, List.of(ahead)).isEmpty(), "Clipped native displacement cannot reach victim beyond wall");
        var contacts = new ArrayList<>(List.of(candidate(3, 0, .9, true, true), candidate(2, 0, .8, true, true), ahead));
        List<CollisionAttackService.Contact> expected = select(new Vec3(0, 0, 1), 2, contacts);
        check(expected.size() == 2 && expected.getFirst().id().equals(ahead.id()), "Nearest contact then UUID determines bounded order");
        Random random = new Random(0);
        for (int i = 0; i < 100; i++) { Collections.shuffle(contacts, random); check(select(new Vec3(0, 0, 1), 2, contacts).equals(expected), "Entity enumeration cannot change simultaneous order"); }
        for (long id = 0; id < 10; id++) for (int side : new int[]{-1, 0, 1}) {
            Vec3 push = CollisionAttackService.pushDirection(new Vec3(0, 0, .3), new Vec3(side, 0, 1), new UUID(0, id));
            check(close(push.length(), 1) && push.y == 0 && push.z > 0 && (side == 0 || push.x * side > 0), "Forward/lateral direction clears path, native resistance owns magnitude");
        }
    }
    private static void ledger() {
        var ledger = new CollisionAttackService.Ledger(); UUID target = new UUID(0, 1);
        ledger.posture(true, 100);
        check(ledger.accept(target, 100, 20, 3), "First intentional contact consumes budget");
        for (int tick = 101; tick < 130; tick++) { ledger.posture(true, tick); check(!ledger.accept(target, tick, 20, 3), "One charge never machine-guns target even after repeat timer"); }
        check(ledger.remaining(3) == 2, "Accepted contact consumes exactly one slot");
        ledger.posture(false, 130); ledger.posture(true, 131);
        check(ledger.accept(target, 131, 20, 3), "New genuine charge after cooldown may contact again");
        ledger.posture(false, 132); ledger.posture(true, 133);
        check(!ledger.accept(target, 133, 20, 3), "Quick re-raise cannot bypass per-target timer");
        check(ledger.accept(new UUID(0, 2), 133, 20, 1) && !ledger.accept(new UUID(0, 3), 133, 20, 1), "Action budget bounds simultaneous victims");
        ledger.posture(false, 134); check(ledger.remaining(3) == 0, "Invalid posture immediately ends collision permission");
        ledger.posture(true, 2); check(ledger.accept(target, 2, 20, 3), "Dimension/time reset cannot retain stale ledger");
    }
    private static void stagger() {
        var window = new StaggerController.Window(100, 110);
        check(!window.active(99) && window.active(100) && window.active(109) && !window.active(110), "Exact short stagger expiry, no refresh/root semantics");
    }
    private static boolean close(double a, double b) { return Math.abs(a - b) < .00001; }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
}
