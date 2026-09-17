package com.mistaboom.essence_ascendance.skill.effect;

/** Reusable normalized action momentum: one transition per tick, with optional externally-owned retention. */
public final class ContinuousMomentumState implements SkillEffectState {
    public enum Action { BUILD, HOLD, DRAIN, RESET }
    private double amount;
    private long lastTick = Long.MIN_VALUE;
    public double amount() { return amount; }
    public void fill() { amount = 1; }
    public void advance(long now, Action action, int buildTicks, int drainTicks, boolean retained) {
        if (now == lastTick) return;
        if (lastTick != Long.MIN_VALUE && (now < lastTick || now - lastTick > 1)) amount = 0;
        lastTick = now;
        if (action == Action.RESET) amount = 0;
        else if (action == Action.BUILD) amount += 1.0 / Math.max(1, buildTicks);
        else if (action == Action.DRAIN && !retained) amount -= 1.0 / Math.max(1, drainTicks);
        amount = Math.abs(amount - 1) < 1e-12 ? 1 : Math.abs(amount) < 1e-12 ? 0 : Math.clamp(amount, 0, 1);
    }
    @Override public void clear() { amount = 0; lastTick = Long.MIN_VALUE; }
}
