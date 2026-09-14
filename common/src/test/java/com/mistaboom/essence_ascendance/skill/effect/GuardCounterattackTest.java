package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.guard.CounterattackLedger;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Deterministic reward/transaction simulation; native targeting and hook order have separate loader fixtures. */
public final class GuardCounterattackTest {
    private static int assertions;
    private GuardCounterattackTest() { }

    public static void main(String[] args) {
        run();
        System.out.println("GuardCounterattackTest: " + assertions + " reward, boundary and transaction checks passed");
    }

    public static void run() {
        CounterattackLedger force = new CounterattackLedger();
        check(!force.grant(1, 100, 0, 8, 160, false), "Zero prevented force never grants");
        check(!force.grant(1, 100, Double.NaN, 8, 160, false), "Nonfinite prevented force never grants");
        check(!force.grant(1, 100, 1, Double.POSITIVE_INFINITY, 160, false), "Unbounded capacity never grants");
        check(force.grant(1, 100, 2, 8, 160, false), "Measured positive block grants");
        check(!force.grant(1, 101, 2, 8, 160, false), "Native callback duplicate is inert");
        equal(force.value(), 2, "Duplicate cannot add force");
        check(force.expiresAt() == 260, "Duplicate cannot refresh expiry");
        force.grant(2, 120, 3, 8, 160, false);
        equal(force.value(), 5, "Later blocks accumulate");
        check(force.expiresAt() == 280, "Later positive block refreshes idle expiry");
        force.grant(3, 125, 20, 8, 160, false);
        equal(force.value(), 8, "Capacity clips a high-force hit");
        check(!force.grant(2, 126, 5, 8, 160, false), "Older outcome cannot arrive twice");
        var attempt = force.reserve(1, 126);
        equal(force.value(), 8, "Reservation does not pre-spend");
        equal(force.finish(attempt, false, 10, false), 0, "Canceled attack retains even with unrelated measured loss");
        equal(force.finish(attempt, true, 0, false), 0, "Accepted zero-health/absorption attack retains");
        equal(force.finish(attempt, true, Double.NaN, false), 0, "Invalid measured loss retains");
        equal(force.value(), 8, "Miss/cancel/zero preserve charge");
        equal(force.finish(attempt, true, 3, false), 8, "A confirmed primary strike consumes once");
        equal(force.finish(attempt, true, 3, false), 0, "Duplicate success cannot consume twice");
        check(force.reserve(2, 127) == null, "Consumed charge cannot apply to a second attack");
        force.grant(4, 130, 2, 8, 160, false);
        check(force.expiresAt() == 290, "Charge after use starts a fresh duration");
        force.expire(289); equal(force.value(), 2, "One tick before expiry remains active");
        force.expire(290); equal(force.value(), 0, "Expiry is exact and inclusive");
        check(!force.grant(4, 291, 2, 8, 160, false), "Expired duplicate cannot rebuild force");
        force.grant(5, 300, 2, 8, 160, false);
        attempt = force.reserve(3, 301);
        force.grant(6, 301, 3, 8, 160, false);
        force.finish(attempt, true, 2, false);
        equal(force.value(), 3, "A real nested block's newly earned force survives the reserved strike");
        force.clear(); equal(force.value(), 0, "Invalid lifecycle clears all force");
        check(force.expiresAt() == 0, "Invalid lifecycle clears expiry");

        CounterattackLedger riposte = new CounterattackLedger();
        riposte.grant(7, 400, 1, 1, 100, true);
        riposte.grant(8, 420, 1, 1, 100, true);
        equal(riposte.value(), 1, "Perfect blocks refresh one Riposte, never stack");
        check(riposte.expiresAt() == 520, "Perfect refresh exact expiry");
        var strike = riposte.reserve(4, 421);
        equal(riposte.finish(strike, false, 0, true), 0, "Riposte air/cancel preserves arm");
        riposte.grant(9, 421, 1, 1, 100, true);
        riposte.finish(strike, true, 4, true);
        equal(riposte.value(), 1, "A newer perfect block during resolution retains its new arm");
        strike = riposte.reserve(5, 422);
        riposte.finish(strike, true, .01, true);
        equal(riposte.value(), 0, "Any confirmed positive mitigated loss consumes Riposte");

        // Combined attack has independent reservations but one accepted outcome and one spend per effect.
        for (int i = 1; i <= 100; i++) {
            force.grant(100 + i, 1_000 + i, .1 * i, 8, 160, false);
            riposte.grant(100 + i, 1_000 + i, 1, 1, 100, true);
            var f = force.reserve(10 + i, 1_000 + i);
            var r = riposte.reserve(10 + i, 1_000 + i);
            boolean accepted = i % 3 != 0;
            double loss = i % 5 == 0 ? 0 : i;
            double before = force.value();
            double spent = force.finish(f, accepted, loss, false);
            riposte.finish(r, accepted, loss, true);
            equal(spent, accepted && loss > 0 ? before : 0, "Combined confirmed policy " + i);
            check(force.value() <= 8 && riposte.value() <= 1, "Combined bounds " + i);
        }
        AABB box = new AABB(2, 0, -1, 3, 2, 1);
        equal(GuardCounterattackService.closest(new Vec3(0, 1.6, 0), box).distanceToSqr(new Vec3(0, 1.6, 0)), 4,
                "Reach measures the actual nearest target surface, not its center");
        equal(GuardCounterattackService.closest(new Vec3(2.5, 1, 0), box).distanceToSqr(new Vec3(2.5, 1, 0)), 0,
                "Overlapping bounds have zero reach distance");
        check(!GuardCounterattackService.suppressDisplacement(null), "No protection outside a native attack scope");
    }

    private static void equal(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1.0E-8, message + ": actual=" + actual + ", expected=" + expected);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
