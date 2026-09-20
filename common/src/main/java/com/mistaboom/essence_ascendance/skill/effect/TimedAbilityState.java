package com.mistaboom.essence_ascendance.skill.effect;

/** Reusable one-window ability state. Cooldown ownership is deliberately separate and may be persisted. */
public final class TimedAbilityState implements SkillEffectState {
    private long activeUntil;

    public void activate(long now, int durationTicks) {
        activeUntil = SkillEffectMath.expiresAt(now, durationTicks);
    }

    public boolean active(long now) {
        return activeUntil > now;
    }

    /** Returns true exactly once when a previously active window reaches its deadline. */
    public boolean finishIfExpired(long now) {
        if (activeUntil <= 0 || now < activeUntil) return false;
        activeUntil = 0;
        return true;
    }

    public long activeUntil() { return activeUntil; }

    @Override public void clear() { activeUntil = 0; }
}
