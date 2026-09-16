package com.mistaboom.essence_ascendance.vitality;

import java.util.UUID;

/** Deterministic continuous recovery and one-target chain invariants. Native fixtures exercise attribution/lifecycle. */
public final class RecoveryTest {
    private static int checks;
    public static void main(String[] args) {
        double last = 1;
        for (int i = 0; i <= 2000; i++) {
            double health = 20 - i / 100.0;
            double speed = RecoveryMath.speed(health, 20, 2, 2);
            check(speed >= last && speed <= 3, "Missing health continuously increases bounded acceleration");
            check(speed - last < .003, "No discontinuity at half health");
            last = speed;
        }
        check(RecoveryMath.speed(20, 20, 2, 2) == 1, "Full health has native speed");
        check(RecoveryMath.speed(5, 20, 2, 2) - RecoveryMath.speed(10, 20, 2, 2)
                > RecoveryMath.speed(15, 20, 2, 2) - RecoveryMath.speed(20, 20, 2, 2), "Curve accelerates most below half");
        check(RecoveryMath.speed(Double.NaN, 20, 2, 2) == 1, "Malformed input cannot manufacture regeneration");
        check(RecoveryMath.healing(10, .25, .1, .05, 3, 3) == .25, "Healing caps at actual missing health");
        check(RecoveryMath.healing(10, 20, .1, .05, 99, 3) == 2, "Healing respects maximum chain count");
        for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            check(RecoveryMath.healing(invalid, 20, .1, .05, 3, 3) == 0, "Invalid/zero damage cannot heal");
        var chain = new RecoveryChain(); UUID first = new UUID(0, 1), second = new UUID(0, 2);
        check(chain.hit(first, 100, 60, 3) == 1, "First hit begins at base healing");
        check(chain.hit(first, 159, 60, 3) == 2, "Same target grows before timeout");
        check(chain.hit(first, 160, 60, 3) == 3 && chain.hit(first, 161, 60, 3) == 3, "Cap retains timeout refresh");
        check(chain.hit(second, 162, 60, 3) == 1 && chain.target().equals(second), "Target change replaces one chain");
        chain.expire(221, 60); check(chain.hits() == 1, "Last active tick retained");
        chain.expire(222, 60); check(chain.hits() == 0, "Exact inactivity boundary resets");
        chain.hit(first, 300, 60, 3); chain.expire(299, 60); check(chain.hits() == 0, "Clock rollback resets");
        chain.hit(first, 300, 60, 3); chain.clear(); check(chain.hits() == 0 && chain.target() == null && chain.expiresAt(60) == 0, "Reset releases all transient state");
        System.out.println("Vitality recovery calculations passed: " + checks);
    }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
}
