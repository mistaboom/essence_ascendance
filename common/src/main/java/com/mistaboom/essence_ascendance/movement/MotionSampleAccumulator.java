package com.mistaboom.essence_ascendance.movement;

/** Pure packet aggregation. Packet frequency never controls the number of gameplay ticks or grants. */
public final class MotionSampleAccumulator {
    public record Sample(double horizontalDistance, double spatialDistance, double turnDegrees,
                         boolean discontinuity, boolean recentMovement) { }
    private double horizontal, spatial, turn;
    private long lastMovementTick = Long.MIN_VALUE;
    private boolean discontinuity;
    public void record(long now, double horizontalDistance, double spatialDistance, double turnDegrees,
                       double minimumDisplacement, double maximumDisplacement) {
        if (!Double.isFinite(horizontalDistance) || !Double.isFinite(spatialDistance)
                || !Double.isFinite(turnDegrees) || horizontalDistance < 0 || spatialDistance < 0
                || turnDegrees < 0 || spatialDistance > maximumDisplacement) {
            discontinuity = true; return;
        }
        horizontal += horizontalDistance; spatial += spatialDistance; turn += turnDegrees;
        if (spatial > maximumDisplacement) discontinuity = true;
        if (horizontalDistance >= minimumDisplacement) lastMovementTick = now;
    }
    public Sample consume(long now, int graceTicks) {
        boolean recent = lastMovementTick != Long.MIN_VALUE && now >= lastMovementTick
                && now - lastMovementTick <= graceTicks;
        Sample sample = new Sample(horizontal, spatial, Math.min(360, turn), discontinuity, recent);
        horizontal = 0; spatial = 0; turn = 0; discontinuity = false;
        return sample;
    }
    public void clear() {
        horizontal = 0; spatial = 0; turn = 0; discontinuity = false; lastMovementTick = Long.MIN_VALUE;
    }
    public static double angle(double previous, double current) {
        double delta = (current - previous) % 360;
        return Math.abs(delta > 180 ? delta - 360 : delta < -180 ? delta + 360 : delta);
    }
}
