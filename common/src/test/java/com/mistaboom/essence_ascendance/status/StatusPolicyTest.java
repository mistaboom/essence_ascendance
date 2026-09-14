package com.mistaboom.essence_ascendance.status;

import com.mistaboom.essence_ascendance.config.StatusBalanceSettings;
import java.util.List;
import java.util.UUID;

/** Pure arithmetic and immutable bounds, independently executable without a world. */
public final class StatusPolicyTest {
    private static int assertions;
    public static void main(String[] args) {
        var settings = StatusBalanceSettings.defaults();
        check(StatusPolicy.liveDuration(-1) && StatusPolicy.liveDuration(1), "infinite and positive duration eligible");
        check(!StatusPolicy.liveDuration(0) && !StatusPolicy.liveDuration(-2), "empty/invalid duration ineligible");
        for (int mask = 0; mask < 32; mask++) {
            boolean harmful = (mask & 1) != 0, effective = (mask & 2) != 0, secondary = (mask & 4) != 0,
                    hostile = (mask & 8) != 0, expired = (mask & 16) != 0;
            check(StatusPolicy.mirrorReady(harmful, effective, secondary, hostile, expired ? 600 : 599, 600)
                    == (harmful && effective && !secondary && hostile && expired), "eligibility/cooldown matrix " + mask);
        }
        for (int duration : new int[]{-1, 0, 1, 1199, 1200, 1201, Integer.MAX_VALUE}) {
            var copy = StatusPolicy.copy(new StatusOutcome.Instance(duration, 255, true, false, true), settings);
            check(copy.duration() >= 0 && copy.duration() <= 1200, "bounded copied duration " + duration);
            check(copy.amplifier() == 4 && copy.ambient() && !copy.particles() && copy.icon(), "copy flags preserved " + duration);
        }
        check(StatusPolicy.copy(new StatusOutcome.Instance(-1, 0, false, true, false), settings).duration() == 1200,
                "infinite incoming transfer becomes bounded finite copy");
        check(StatusPolicy.cooldown(100, settings) == 700, "cooldown start is event time");
        check(StatusPolicy.cooldown(Long.MAX_VALUE - 1, settings) == Long.MAX_VALUE, "cooldown arithmetic cannot overflow");
        expect(() -> new StatusBalanceSettings(0, 100, 1), "zero cooldown rejected");
        expect(() -> new StatusBalanceSettings(600, 0, 1), "zero transfer bound rejected");
        expect(() -> new StatusBalanceSettings(600, 100, 11), "unbounded amplifier rejected");
        var mutable = new java.util.ArrayList<StatusOutcome.Instance>();
        mutable.add(new StatusOutcome.Instance(100, 0, false, true, true));
        var outcome = new StatusOutcome(1, 100, UUID.randomUUID(), 1, "test:dimension", "test:skill", "test:effect", "HARMFUL",
                mutable, List.of(), null, null, "none", "prevented", List.of(), List.of(), true, false,
                List.of(), "not_requested", List.of(), 0, 0, null, "pure", null);
        mutable.clear(); check(outcome.requested().size() == 1, "snapshot detaches mutable list");
        expectUnsupported(() -> outcome.requested().clear(), "snapshot cannot be changed");
        System.out.println("Status policy checks passed: " + assertions);
    }
    private static void check(boolean condition, String reason) { assertions++; if (!condition) throw new AssertionError(reason); }
    private static void expect(Runnable action, String reason) { try { action.run(); throw new AssertionError(reason); } catch (IllegalArgumentException expected) { assertions++; } }
    private static void expectUnsupported(Runnable action, String reason) { try { action.run(); throw new AssertionError(reason); } catch (UnsupportedOperationException expected) { assertions++; } }
}
