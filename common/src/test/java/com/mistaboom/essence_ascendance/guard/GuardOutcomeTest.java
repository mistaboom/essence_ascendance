package com.mistaboom.essence_ascendance.guard;

import com.mistaboom.essence_ascendance.config.GuardBalanceSettings;
import com.mistaboom.essence_ascendance.equipment.ShieldMath;
import com.mistaboom.essence_ascendance.skill.effect.SafeCreatureAreaService;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Contract simulations across timing, measured force, disjoint reflection, bounded echo and counter composition. */
public final class GuardOutcomeTest {
    private static int checks;
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        for (int raise = 1; raise <= 5; raise++) for (int window = 1; window <= 10; window++) {
            long ready = 100 + raise;
            for (int delta = -10; delta <= 15; delta++) {
                check(PerfectGuardFramework.perfect(ready + delta, ready, window, true, 1) == (delta >= 0 && delta < window), "Half-open native-ready window");
                check(!PerfectGuardFramework.perfect(ready + delta, ready, window, false, 1), "Native readiness required");
                check(!PerfectGuardFramework.perfect(ready + delta, ready, window, true, 0), "Positive committed block required");
            }
        }
        check(!PerfectGuardFramework.perfect(100, -1, 5, true, 1), "Unknown/reloaded/invalid guard start never fabricates perfect timing");
        check(!PerfectGuardFramework.perfect(100, 100, 5, true, Double.NaN), "Nonfinite force rejected");
        for (double incoming : new double[]{0, .5, 10, 100, 1024}) for (double blockedFraction : new double[]{0, .25, 1}) {
            double blocked = incoming * blockedFraction;
            double extension = ReflectionRouter.wardExtension(incoming, blocked, 0, 20, .5, true);
            check(close(extension, (incoming - blocked) * .1), "Ward uses disjoint measured nonshield prevention");
            check(ReflectionRouter.wardExtension(incoming, blocked, 1, 20, .5, true) == 0, "Positive health or absorption loss excludes extension");
            check(ReflectionRouter.wardExtension(incoming, blocked, 0, 0, 1, true) == 0, "No free equipment reflection");
            check(ReflectionRouter.wardExtension(incoming, blocked, 0, 20, 1, false) == 0, "Invalid/canceled/secondary source rejected");
            double baseBlock = ShieldMath.reflectedPortion((float) blocked, 40);
            check(close(ReflectionRouter.compose(0, baseBlock, extension, 2), (baseBlock + extension) * 2), "Amplifier applies once to composed primary reflection");
            check(close(ReflectionRouter.compose(1, baseBlock, 0, 1), 1 + baseBlock), "Base ordinary and block reflection preserved without skills");
        }
        var tuning = new GuardBalanceSettings.Amplifier(.25, 2, 120);
        var amplifier = new GuardAmplifierState();
        var stored = new CounterattackLedger(); var riposte = new CounterattackLedger();
        for (long event = 1; event <= 10; event++) {
            check(amplifier.block(event, 100, 8, false, tuning), "Positive blocks add growth");
            check(!amplifier.block(event, 100, 8, true, tuning), "Duplicate block cannot promote or refresh");
            check(amplifier.multiplier(100) <= 2, "Amplifier bounded maximum");
        }
        check(amplifier.multiplier(219) == 2 && amplifier.multiplier(220) == 1, "Exact amplifier expiry");
        check(amplifier.block(11, 220, 4, false, tuning) && amplifier.multiplier(220) == 1.25, "Expired growth starts from base");
        check(amplifier.block(12, 221, 4, true, tuning) && amplifier.multiplier(221) == 2 && amplifier.expiresAt() == 341, "Perfect promotes and refreshes immediately");
        amplifier.clear(); check(amplifier.multiplier(221) == 1, "Effect/equipment lifecycle clears amplifier");
        // One perfect native outcome can arm Riposte plus exactly one exclusive reward branch.
        stored.grant(20, 300, 2, 8, 160, false); riposte.grant(20, 300, 1, 1, 100, true);
        var force = stored.reserve(100, 301); var counter = riposte.reserve(100, 301);
        check(force.value() == 2 && counter.value() == 1, "Stored Force and Riposte can share one primary reservation");
        check(stored.finish(force, true, 5, false) == 2 && riposte.finish(counter, true, 5, true) == 1, "Same confirmed native loss consumes each once");
        check(stored.finish(force, true, 5, false) == 0 && riposte.finish(counter, true, 5, true) == 0, "Duplicate callback cannot consume again");
        for (double strength : new double[]{0, .1, .4, 1, 64, Double.MAX_VALUE, Double.NaN}) {
            Vec3 attempt = KnockbackEchoService.attempt(strength, 3, 4);
            check(Double.isFinite(attempt.length()) && attempt.length() <= 64.000001, "Attempt record remains bounded and finite");
            double echo = KnockbackEchoService.magnitude(attempt, .5, 1);
            check(echo >= 0 && echo <= 1, "Echo has a validated magnitude cap");
            if (strength == .4) check(close(echo, .2), "Fully resisted native attempt can retain positive echoed force");
        }
        check(KnockbackEchoService.attempt(1, Double.NaN, 1).equals(Vec3.ZERO), "Nonfinite attempted direction rejected");
        check(KnockbackEchoService.attempt(1, 0, 0).equals(Vec3.ZERO), "Zero direction does not invent force");
        UUID self = new UUID(0, 0), primary = new UUID(0, 1), ally = new UUID(0, 2), wall = new UUID(0, 3);
        List<SafeCreatureAreaService.Candidate> candidates = new ArrayList<>();
        candidates.add(new SafeCreatureAreaService.Candidate(self, Vec3.ZERO, true));
        candidates.add(new SafeCreatureAreaService.Candidate(primary, Vec3.ZERO, true));
        candidates.add(new SafeCreatureAreaService.Candidate(ally, Vec3.ZERO, false));
        candidates.add(new SafeCreatureAreaService.Candidate(wall, Vec3.ZERO, false));
        for (int i = 4; i < 24; i++) candidates.add(new SafeCreatureAreaService.Candidate(new UUID(0, i), new Vec3(i % 5, 0, 0), true));
        var selected = SafeCreatureAreaService.select(Vec3.ZERO, 4, 6, Set.of(self, primary), candidates);
        check(selected.size() == 6 && !selected.contains(self) && !selected.contains(primary) && !selected.contains(ally) && !selected.contains(wall), "Bounded hostile visible area excludes defender/primary/allies/walls");
        for (int seed = 0; seed < 30; seed++) {
            Collections.shuffle(candidates, new Random(seed));
            check(selected.equals(SafeCreatureAreaService.select(Vec3.ZERO, 4, 6, Set.of(self, primary), candidates)), "Deterministic area order across enumeration order");
        }
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("GuardOutcomeTest: " + checks + " checks passed");
    }
    private static boolean close(double a, double b) { return Math.abs(a-b) < 1e-4; }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
