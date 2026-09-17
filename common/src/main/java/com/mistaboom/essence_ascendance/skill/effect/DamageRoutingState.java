package com.mistaboom.essence_ascendance.skill.effect;

/** Last accepted routing decision for compact HUD/debug presentation, never damage authority. */
public final class DamageRoutingState implements SkillEffectState {
    private boolean recorded;
    private double incoming, immediate, lastReduction;
    private long recordedAt = Long.MIN_VALUE;

    public void record(double incoming, double immediate) {
        record(incoming, immediate, Long.MIN_VALUE);
    }

    public void record(double incoming, double immediate, long now) {
        if (!Double.isFinite(incoming) || !Double.isFinite(immediate) || incoming <= 0) return;
        this.incoming = incoming;
        this.immediate = Math.clamp(immediate, 0, incoming);
        // Zero is an actual outcome too. Never retain an older positive prevention on a new hit.
        lastReduction = this.incoming - this.immediate;
        recordedAt = now;
        recorded = true;
    }

    public boolean recorded() { return recorded; }
    public double incoming() { return incoming; }
    public double immediate() { return immediate; }
    /** Amount prevented by the most recent routed hit, including zero. */
    public double lastReduction() { return lastReduction; }
    /** A real hit, rather than unrelated combat, reveals this presentation-only state. */
    public boolean recent(long now, int windowTicks) {
        return recorded && recordedAt != Long.MIN_VALUE && windowTicks > 0
                && now >= recordedAt && now - recordedAt < windowTicks;
    }
    @Override public void clear() {
        recorded = false;
        incoming = immediate = lastReduction = 0;
        recordedAt = Long.MIN_VALUE;
    }
}
