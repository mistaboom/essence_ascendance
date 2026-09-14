package com.mistaboom.essence_ascendance.status;

import com.mistaboom.essence_ascendance.config.StatusBalanceSettings;

/** Deterministic decisions shared by native application and instant dispatch. */
public final class StatusPolicy {
    private StatusPolicy() { }
    public static boolean liveDuration(int duration) { return duration == -1 || duration > 0; }
    public static boolean mirrorReady(boolean harmful, boolean effective, boolean secondary,
                                      boolean hostileSource, long now, long cooldownUntil) {
        return harmful && effective && !secondary && hostileSource && now >= cooldownUntil;
    }
    public static StatusOutcome.Instance copy(StatusOutcome.Instance incoming, StatusBalanceSettings settings) {
        int duration = incoming.duration() == -1 ? settings.mirrorMaximumDurationTicks()
                : Math.clamp(incoming.duration(), 0, settings.mirrorMaximumDurationTicks());
        return new StatusOutcome.Instance(duration, Math.clamp(incoming.amplifier(), 0, settings.mirrorMaximumAmplifier()),
                incoming.ambient(), incoming.particles(), incoming.icon());
    }
    public static long cooldown(long now, StatusBalanceSettings settings) {
        return now > Long.MAX_VALUE - settings.mirrorCooldownTicks() ? Long.MAX_VALUE : now + settings.mirrorCooldownTicks();
    }
}
