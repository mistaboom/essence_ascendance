package com.mistaboom.essence_ascendance.movement;

import com.mistaboom.essence_ascendance.skill.effect.SkillEffectState;

/** Shared edge/charge/air-resource controller. Server time earns charge; packet count never does.
 * The client may run this only to route ordinary Jump away from vanilla Elytra deployment.
 * Physical impulses, charge values and landing resources remain server-owned. */
public final class MovementAbilityState implements SkillEffectState {
    public enum Mode { CHARGED, AIR }
    /** NATIVE_JUMP routes input only; it never authorizes an extra server impulse. */
    public enum Action { NONE, NATIVE_JUMP, CHARGED_RELEASE, AIR_JUMP }
    public record Decision(Action action, double charge) {
        public static final Decision NONE = new Decision(Action.NONE, 0);
        public static final Decision NATIVE_JUMP = new Decision(Action.NATIVE_JUMP, 0);
    }
    private boolean down, armed, grounded, airAvailable, claimedPress, awaitingTakeoff;
    private boolean nativeGroundPress;
    private long chargeStarted = -1, lastInput = -1, lastAction = Long.MIN_VALUE, launchAt = -1;

    /** Observe actual mode/support even when the user sends no input. A reset in midair cannot refill. */
    public void observe(long now, boolean enabled, boolean supported, int inputTimeout) {
        if (!enabled) { clear(); return; }
        if (lastInput >= 0 && now < lastInput) { clear(); return; }
        if (lastInput >= 0 && now - lastInput > inputTimeout) cancelInput();
        if (awaitingTakeoff) {
            if (!supported) awaitingTakeoff = false;
            else if (now - launchAt <= inputTimeout) { grounded = false; return; }
            else awaitingTakeoff = false; // A rejected/ceiling-blocked launch must not lock input forever.
        }
        grounded = supported;
        if (grounded) airAvailable = true;
        else {
            chargeStarted = -1; // Walking off an edge never releases stored ground charge.
            // A ground press may reach native takeoff, but may not survive landing as
            // another jump. Only a real key release can start another ground press.
            if (nativeGroundPress) { nativeGroundPress = false; claimedPress = down; }
        }
    }

    public Decision input(long now, MovementAbilityInput input, Mode mode, boolean enabled,
                          boolean supported, int chargeTicks, int inputTimeout) {
        observe(now, enabled, supported, inputTimeout);
        if (!enabled) return Decision.NONE;
        if (!input.enabled()) { cancelInput(); return Decision.NONE; }
        lastInput = now;
        boolean pressed = input.jump();
        if (!pressed) {
            boolean release = down && chargeStarted >= 0 && grounded && lastAction != now;
            double charge = charge(now, chargeTicks);
            down = false; armed = true; claimedPress = false; nativeGroundPress = false; chargeStarted = -1;
            if (release) { lastAction = now; return new Decision(Action.CHARGED_RELEASE, charge); }
            return Decision.NONE;
        }
        boolean edge = !down && armed;
        down = true;
        if (!edge) return nativeGroundPress ? Decision.NATIVE_JUMP : Decision.NONE;
        armed = false;
        if (mode == Mode.CHARGED && grounded && !awaitingTakeoff) {
            chargeStarted = now; claimedPress = true;
        } else if (mode == Mode.AIR) {
            if (grounded) {
                // Let vanilla perform the initial jump, including sprinting and
                // native jump delay. Once the feet leave support this press ends.
                nativeGroundPress = true;
                return Decision.NATIVE_JUMP;
            }
            if (input.requestsAirJump() && airAvailable && lastAction != now) {
                airAvailable = false; claimedPress = true; lastAction = now;
                return new Decision(Action.AIR_JUMP, 1);
            }
            // A spent air press can still deploy native Elytra. Forward its edge
            // only, so holding it through landing cannot become an automatic hop.
            return Decision.NATIVE_JUMP;
        } else if (!grounded && !awaitingTakeoff) {
            // Charged Jump only owns grounded input. Preserve native Elytra
            // deployment on a separate airborne press after the charged launch.
            return Decision.NATIVE_JUMP;
        }
        return Decision.NONE;
    }

    public void launched(long now, boolean fromGround) {
        chargeStarted = -1;
        if (fromGround) { awaitingTakeoff = true; launchAt = now; grounded = false; }
    }
    public double charge(long now, int chargeTicks) {
        return chargeStarted < 0 ? 0 : Math.clamp((now - chargeStarted) / (double) Math.max(1, chargeTicks), 0, 1);
    }
    public boolean charging() { return chargeStarted >= 0; }
    public boolean airAvailable() { return airAvailable; }
    public boolean claimedPress() { return down && claimedPress; }
    public boolean grounded() { return grounded; }
    /** Stale or GUI-suspended input cancels rather than fabricating a release or a new press. */
    private void cancelInput() { down = false; armed = false; claimedPress = false; nativeGroundPress = false; chargeStarted = -1; lastInput = -1; }
    @Override public void clear() {
        cancelInput(); grounded = false; airAvailable = false; awaitingTakeoff = false;
        lastAction = Long.MIN_VALUE; launchAt = -1;
    }
}
