package com.mistaboom.essence_ascendance.posture;

import com.mistaboom.essence_ascendance.config.PostureBalanceSettings;

/** Pure bounded state machine. Tick and incoming-event identities each commit at most once. */
public final class PostureMeter {
    public enum Choice { NONE, EVASIVE, BULWARK, ADAPTIVE }
    private Choice choice = Choice.NONE;
    private double meter;
    private long lastTick = Long.MIN_VALUE, lastHit = Long.MIN_VALUE, expiresAt;
    private int stableTicks, stacks;
    private boolean still;
    private String damageType = "", reason = "inactive";
    public Choice choice() { return choice; }
    public double meter() { return meter; }
    public int stacks() { return stacks; }
    public long expiresAt() { return expiresAt; }
    public String damageType() { return damageType; }
    public String reason() { return reason; }
    public boolean still() { return still; }
    public void select(Choice next) { if (choice != next) { clear("selection_changed"); choice = next; } }
    public void clear(String why) {
        meter = 0; stacks = 0; damageType = ""; expiresAt = 0; stableTicks = 0; still = false;
        lastTick = Long.MIN_VALUE; lastHit = Long.MIN_VALUE; reason = why;
    }
    public void expire(long now) {
        if (choice == Choice.ADAPTIVE && stacks > 0 && now >= expiresAt) {
            stacks = 0; meter = 0; damageType = ""; expiresAt = 0; reason = "adaptation_expired";
        }
    }
    public void tick(long now, double displacement, double turn, boolean intentional, boolean forced,
                     boolean validMode, boolean facingThreat, PostureBalanceSettings settings) {
        if (now == lastTick) return;
        if (lastTick != Long.MIN_VALUE && (now < lastTick || now - lastTick > 1)) clear("tick_discontinuity");
        lastTick = now; expire(now);
        var motion = settings.movement();
        if (!Double.isFinite(displacement) || !Double.isFinite(turn) || displacement > motion.maximumDisplacement()) {
            clear("movement_discontinuity"); lastTick = now; return;
        }
        boolean stable = !forced && validMode && displacement <= (still ? motion.stillExitDisplacement() : motion.stillEnterDisplacement())
                && turn <= (still ? motion.turnExitDegrees() : motion.turnEnterDegrees());
        stableTicks = stable ? Math.min(motion.stableTicks(), stableTicks + 1) : 0;
        still = stableTicks >= motion.stableTicks();
        if (choice == Choice.EVASIVE) {
            boolean build = intentional && !forced && validMode && displacement >= motion.minimumDisplacement();
            meter = bound(meter + (build ? 1.0 / settings.evasive().buildTicks() : -1.0 / settings.evasive().drainTicks()));
            reason = build ? "intentional_movement" : forced ? "forced_motion" : !validMode ? "unsupported_movement_mode" : "movement_stopped";
        } else if (choice == Choice.BULWARK) {
            boolean build = still && facingThreat;
            meter = bound(meter + (build ? 1.0 / settings.bulwark().buildTicks() : -1.0 / settings.bulwark().drainTicks()));
            reason = build ? "still_facing_threat" : forced ? "forced_displacement" : !stable ? "moving_or_turning"
                    : !still ? "settling" : "no_visible_frontal_threat";
        }
    }
    public int prospectiveStacks(String identity, long now, PostureBalanceSettings.Adaptive settings) {
        expire(now);
        return identity.equals(damageType) ? Math.min(settings.maximumStacks(), stacks + 1) : 1;
    }
    public double adaptation(String identity, long now, PostureBalanceSettings.Adaptive settings) {
        if (choice != Choice.ADAPTIVE) return 0;
        return Math.max(0, prospectiveStacks(identity, now, settings) - settings.minimumHits() + 1) * settings.resistancePerStack();
    }
    public boolean hit(long event, long now, String identity, boolean eligible, boolean loss,
                       boolean dodged, PostureBalanceSettings settings) {
        // A newer nested secondary completion must not invalidate its enclosing primary event.
        if (!eligible) return false;
        if (event <= lastHit) return false;
        lastHit = event;
        if (choice == Choice.EVASIVE && (dodged || loss)) {
            meter = bound(meter - (dodged ? settings.evasive().successDrainFraction() : settings.evasive().hitDrainFraction()));
            reason = dodged ? "dodge_consumed_meter" : "taken_hit_drained_meter"; return true;
        }
        if (choice == Choice.ADAPTIVE && loss && !identity.isBlank()) {
            boolean switched = !identity.equals(damageType);
            stacks = prospectiveStacks(identity, now, settings.adaptive()); damageType = identity;
            meter = (double) stacks / settings.adaptive().maximumStacks(); expiresAt = now + settings.adaptive().windowTicks();
            reason = switched ? "new_damage_type" : "same_damage_type"; return true;
        }
        return false;
    }
    public static boolean dodge(double roll, double chance) {
        return Double.isFinite(roll) && roll >= 0 && roll < 1 && Double.isFinite(chance) && chance > 0 && roll < chance;
    }
    private static double bound(double value) { return Math.abs(value - 1) < 1e-12 ? 1 : Math.clamp(value, 0, 1); }
}
