package com.mistaboom.essence_ascendance.guard;

import com.mistaboom.essence_ascendance.config.GuardBalanceSettings;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectState;

/** Additive multiplier growth; the earning block uses the newly earned multiplier. */
public final class GuardAmplifierState implements SkillEffectState {
    private double multiplier = 1;
    private long refreshedAt, expiresAt, lastEvent;
    public boolean block(long event, long now, double prevented, boolean perfect, GuardBalanceSettings.Amplifier tuning) {
        if (event <= lastEvent || !Double.isFinite(prevented) || prevented <= 0) return false;
        lastEvent = event;
        multiplier = perfect ? tuning.maximumMultiplier()
                : Math.min(tuning.maximumMultiplier(), multiplier(now) + tuning.perBlockGrowth());
        refreshedAt = now; expiresAt = now + tuning.durationTicks();
        return true;
    }
    public double multiplier(long now) { return now >= refreshedAt && now < expiresAt ? multiplier : 1; }
    public long expiresAt() { return expiresAt; }
    public long lastEvent() { return lastEvent; }
    @Override public void clear() { multiplier = 1; refreshedAt = expiresAt = lastEvent = 0; }
}
