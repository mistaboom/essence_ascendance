package com.mistaboom.essence_ascendance.guard;

/** Bounded, non-persistent block reward. A reservation never spends a failed attack. */
public final class CounterattackLedger {
    private double value;
    private long expiresAt;
    private long refreshedAt;
    private long lastEvent;
    private long lastConsumedToken;
    private String decision = "empty";

    public boolean grant(long event, long now, double amount, double capacity, int duration, boolean replace) {
        expire(now);
        if (event <= 0 || event <= lastEvent || !Double.isFinite(amount) || amount <= 0
                || !Double.isFinite(capacity) || capacity <= 0 || duration <= 0) return false;
        lastEvent = event;
        value = Math.min(capacity, (replace ? 0 : value) + amount);
        refreshedAt = now;
        expiresAt = now > Long.MAX_VALUE - duration ? Long.MAX_VALUE : now + duration;
        decision = "block_granted";
        return true;
    }

    public Reservation reserve(long token, long now) {
        expire(now);
        if (value <= 0 || token <= 0) return null;
        decision = "reserved_primary";
        return new Reservation(token, lastEvent, value);
    }

    /** Preserve a reward earned during the attack itself; never consume a reservation twice. */
    public double finish(Reservation reservation, boolean accepted, double measuredLoss, boolean replace) {
        if (reservation == null || reservation.token <= lastConsumedToken) return 0;
        if (!accepted || !Double.isFinite(measuredLoss) || measuredLoss <= 0) {
            decision = "retained_miss_cancel_or_zero";
            return 0;
        }
        lastConsumedToken = reservation.token;
        double consumed = Math.min(value, reservation.value);
        if (!replace || reservation.sourceEvent == lastEvent) value = Math.max(0, value - consumed);
        if (value == 0) expiresAt = 0;
        decision = "consumed_confirmed_primary";
        return consumed;
    }

    public void expire(long now) {
        if (value > 0 && now >= expiresAt) {
            value = 0;
            expiresAt = 0;
            decision = "expired";
        }
    }

    public void clear() {
        value = 0;
        expiresAt = 0;
        decision = "invalidated";
    }

    public double value() { return value; }
    public long expiresAt() { return expiresAt; }
    public long refreshedAt() { return refreshedAt; }
    public long lastEvent() { return lastEvent; }
    public String decision() { return decision; }
    public record Reservation(long token, long sourceEvent, double value) { }
}
