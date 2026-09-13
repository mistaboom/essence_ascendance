package com.mistaboom.essence_ascendance.projectile;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;

/** Matches entity-attack animation packets without accepting a second interception or a use-item animation. */
public final class ProjectileSwingLedger {
    private final Deque<Long> expectedAnimations = new ArrayDeque<>();
    private long lastProcessed = Long.MIN_VALUE, useUntil = Long.MIN_VALUE;
    /** Only the initial left click can become a projectile attack; held mining and completion packets cannot. */
    public boolean startBlockAttack(long tick, ServerboundPlayerActionPacket.Action action, boolean mining, BooleanSupplier intercept) {
        if (action != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK || mining || !intercept.getAsBoolean()) return false;
        attackAnimation(tick);
        return true;
    }
    public void attackAnimation(long tick) {
        expire(tick);
        if (expectedAnimations.size() < 16) expectedAnimations.addLast(tick);
    }
    public void used(long tick) { useUntil = tick + 2; }
    public boolean animate(long tick) {
        expire(tick);
        if (!expectedAnimations.isEmpty()) { expectedAnimations.removeFirst(); return false; }
        return tick > useUntil;
    }
    public boolean process(long tick, double readiness, double threshold) {
        if (tick <= lastProcessed || !Double.isFinite(readiness) || readiness < threshold) return false;
        lastProcessed = tick; return true;
    }
    private void expire(long tick) {
        while (!expectedAnimations.isEmpty() && tick - expectedAnimations.peekFirst() > 2) expectedAnimations.removeFirst();
    }
}
