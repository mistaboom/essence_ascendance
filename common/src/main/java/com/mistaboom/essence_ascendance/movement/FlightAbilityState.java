package com.mistaboom.essence_ascendance.movement;

import com.mistaboom.essence_ascendance.skill.effect.SkillEffectState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;

/** Shared ordinary-Jump intent, normalized resource clocks, glide ownership and temporary flight-ability lease. */
public final class FlightAbilityState implements SkillEffectState {
    /** Transport tolerance only: three normal heartbeats, not gameplay balance. */
    private static final int ACTIVE_HOLD_GRACE_TICKS = MovementAbilityInput.HEARTBEAT_TICKS * 3;

    private boolean jumpDown;
    private int forwardInput;
    private int leftInput;
    private long jumpHeldSince = Long.MIN_VALUE;
    private boolean jumpArmed = true;
    private boolean jumpPressPending;
    private long jumpPressTick = Long.MIN_VALUE;
    private long lastInput = -1;
    private long lastResourceTick = Long.MIN_VALUE;
    private double stamina = 1;
    private double boostCharge = 1;
    private boolean wingsActive;
    private long wingsStartedTick = Long.MIN_VALUE;
    private boolean flightPermission;
    private boolean thrusting;
    private boolean grantedMayfly;
    private boolean initiatedFlying;

    public void input(long now, MovementAbilityInput input, int timeoutTicks) {
        expireInput(now, timeoutTicks);
        if (!input.enabled()) {
            cancelInput();
            return;
        }
        lastInput = now;
        forwardInput = input.forward();
        leftInput = input.left();
        boolean pressed = input.jump();
        if (!pressed) {
            jumpDown = false;
            jumpHeldSince = Long.MIN_VALUE;
            jumpArmed = true;
            return;
        }
        if (!jumpDown) jumpHeldSince = now;
        if (!jumpDown && jumpArmed) {
            jumpPressPending = true;
            jumpPressTick = now;
            jumpArmed = false;
        }
        jumpDown = true;
    }

    public void expireInput(long now, int timeoutTicks) {
        if (lastInput >= 0 && (now < lastInput || now - lastInput > timeoutTicks)) cancelInput();
    }

    public boolean jumpHeld(long now, int timeoutTicks) {
        return jumpHeldFor(now, timeoutTicks, 0);
    }

    public boolean jumpHeldFor(long now, int timeoutTicks, int minimumTicks) {
        expireInput(now, timeoutTicks);
        return jumpDown && jumpHeldSince != Long.MIN_VALUE && now >= jumpHeldSince
                && now - jumpHeldSince >= Math.max(0, minimumTicks);
    }


    /** Held-thrust consumers demand a recent heartbeat instead of the broader movement-intent timeout. */
    public boolean jumpHeldFreshFor(long now, int minimumTicks) {
        if (lastInput < 0 || now < lastInput || now - lastInput > ACTIVE_HOLD_GRACE_TICKS) {
            if (lastInput >= 0) cancelInput();
            return false;
        }
        return jumpDown && jumpHeldSince != Long.MIN_VALUE && now >= jumpHeldSince
                && now - jumpHeldSince >= Math.max(0, minimumTicks);
    }

    public long consumeJumpPressTick(long now, int timeoutTicks) {
        expireInput(now, timeoutTicks);
        if (!jumpPressPending) return Long.MIN_VALUE;
        jumpPressPending = false;
        long result = jumpPressTick;
        jumpPressTick = Long.MIN_VALUE;
        return result;
    }

    /** Packet frequency never earns resource; only elapsed authoritative game ticks do. */
    public void updateStamina(long now, int enduranceTicks, int rechargeTicks, boolean draining, boolean recharging) {
        long elapsed = elapsed(now);
        if (elapsed <= 0) return;
        double delta = (recharging ? elapsed / (double) Math.max(1, rechargeTicks) : 0)
                - (draining ? elapsed / (double) Math.max(1, enduranceTicks) : 0);
        stamina = Math.clamp(stamina + delta, 0, 1);
    }

    public void updateBoost(long now, int rechargeTicks) {
        long elapsed = elapsed(now);
        if (elapsed > 0) boostCharge = Math.clamp(boostCharge + elapsed / (double) Math.max(1, rechargeTicks), 0, 1);
    }

    private long elapsed(long now) {
        if (lastResourceTick == Long.MIN_VALUE || now < lastResourceTick) {
            lastResourceTick = now;
            return 0;
        }
        long result = now - lastResourceTick;
        lastResourceTick = now;
        return result;
    }

    public boolean spendBoost() {
        if (boostCharge < 1) return false;
        boostCharge = 0;
        return true;
    }

    public void activateFlight(ServerPlayer player) {
        flightPermission = true;
        thrusting = false;
        Abilities abilities = player.getAbilities();
        boolean changed = false;
        if (!abilities.mayfly) {
            abilities.mayfly = true;
            grantedMayfly = true;
            changed = true;
        }
        if (!abilities.flying) {
            abilities.flying = true;
            initiatedFlying = true;
            changed = true;
        }
        if (changed) player.onUpdateAbilities();
    }

    /** Keep only the permission we own; native double-tap/vertical controls remain vanilla once admitted. */
    public void maintainFlightPermission(ServerPlayer player) {
        if (!flightPermission || player.getAbilities().mayfly) return;
        player.getAbilities().mayfly = true;
        grantedMayfly = true;
        player.onUpdateAbilities();
    }

    public void releaseFlight(ServerPlayer player) {
        Abilities abilities = player.getAbilities();
        boolean changed = false;
        boolean nativeFlightMode = player.isCreative() || player.isSpectator();
        if (!nativeFlightMode && initiatedFlying && abilities.flying) {
            abilities.flying = false;
            changed = true;
        }
        if (!nativeFlightMode && grantedMayfly && abilities.mayfly) {
            abilities.mayfly = false;
            changed = true;
        }
        flightPermission = false;
        thrusting = false;
        grantedMayfly = false;
        initiatedFlying = false;
        if (changed) player.onUpdateAbilities();
    }

    public double stamina() { return stamina; }
    public double boostCharge() { return boostCharge; }
    public boolean flightPermission() { return flightPermission; }
    public boolean thrusting() { return thrusting; }
    public int forwardInput() { return forwardInput; }
    public int leftInput() { return leftInput; }
    public void setThrusting(boolean value) { thrusting = value; }
    public boolean wingsActive() { return wingsActive; }
    public long wingsStartedTick() { return wingsStartedTick; }
    public void startWings(long now) { wingsActive = true; wingsStartedTick = now; }
    public void stopWings() { wingsActive = false; wingsStartedTick = Long.MIN_VALUE; }

    /** Cancel inactive thrust without treating a loadout change as a grounded refill. */
    public void suspendThrust() {
        cancelInput();
        thrusting = false;
        lastResourceTick = Long.MIN_VALUE;
    }

    /** A disabled boost loses input, but its already-running recharge still follows server time. */
    public void suspendBoost() { cancelInput(); }

    private void cancelInput() {
        jumpDown = false;
        forwardInput = 0;
        leftInput = 0;
        jumpHeldSince = Long.MIN_VALUE;
        jumpArmed = false;
        jumpPressPending = false;
        jumpPressTick = Long.MIN_VALUE;
        lastInput = -1;
    }

    @Override public void clear() {
        cancelInput();
        stamina = 1;
        boostCharge = 1;
        lastResourceTick = Long.MIN_VALUE;
        wingsActive = false;
        wingsStartedTick = Long.MIN_VALUE;
        flightPermission = false;
        thrusting = false;
        grantedMayfly = false;
        initiatedFlying = false;
    }
}
