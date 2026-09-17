package com.mistaboom.essence_ascendance.skill.effect;

/** Reusable measured-event threshold plus one refreshable timed reward; no skill IDs or balance defaults. */
public final class ThresholdBuffState implements SkillEffectState {
    private final TimedStackState buff = TimedStackState.shared();
    private double measured, required;
    private boolean recorded, triggered;
    public boolean observe(long now, double amount, double threshold, int durationTicks) {
        return observe(now, amount, threshold, durationTicks, true);
    }
    /** The caller can require an accepted outcome (for example, actual survival) as well as a magnitude. */
    public boolean observe(long now, double amount, double threshold, int durationTicks, boolean eligible) {
        // Preserve the existing inclusive policy for its other consumers.
        return record(now, amount, threshold, durationTicks,
                eligible && amount > 0 && threshold > 0 && amount >= Math.nextDown(threshold));
    }
    /** Strict thresholds do not grant at equality, unlike the inclusive default above. */
    public boolean observeAbove(long now, double amount, double threshold, int durationTicks) {
        return record(now, amount, threshold, durationTicks, amount > threshold);
    }
    private boolean record(long now, double amount, double threshold, int durationTicks, boolean qualifies) {
        if (!Double.isFinite(amount) || !Double.isFinite(threshold) || amount < 0 || threshold < 0)
            throw new IllegalArgumentException("Invalid measured trigger");
        measured = amount; required = threshold; recorded = true; triggered = qualifies;
        if (triggered) buff.grant(now, 1, durationTicks, true);
        return triggered;
    }
    public boolean active(long now) { buff.reconcile(now, 1); return buff.count() > 0; }
    public long expiresAt() { return buff.nextExpiry(); }
    public double measured() { return measured; }
    public double required() { return required; }
    public boolean recorded() { return recorded; }
    public boolean triggered() { return triggered; }
    @Override public void clear() { buff.clear(); measured = required = 0; recorded = triggered = false; }
}
