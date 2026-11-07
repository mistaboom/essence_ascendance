package com.mistaboom.essence_ascendance.gameplay;

import java.util.Objects;

/** Bounded scalar trajectory. Displacement and accepted path are deliberately separate facts. */
public final class MovementSessionTracker {
    public static final double PATH_CAP = 1_000_000;
    public record Sample(boolean accepted, boolean discontinuity, double horizontalStep, double spatialStep,
                         double horizontalDisplacement, double verticalDisplacement,
                         double horizontalPath, double spatialPath) { }
    private String sessionId, dimension;
    private long lastTick;
    private double startX, startY, startZ, x, y, z, horizontalPath, spatialPath;
    public void begin(String sessionId, String dimension, long tick, double x, double y, double z) {
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 256 || dimension == null
                || dimension.isBlank() || dimension.length() > 256 || tick < 0 || !finite(x,y,z))
            throw new IllegalArgumentException("Invalid trajectory start");
        this.sessionId = sessionId; this.dimension = dimension; lastTick = tick;
        startX = this.x = x; startY = this.y = y; startZ = this.z = z;
        horizontalPath = spatialPath = 0;
    }
    public boolean active() { return sessionId != null; }
    public String sessionId() { return sessionId; }
    public void stop() { sessionId = dimension = null; horizontalPath = spatialPath = 0; }
    public Sample sample(String dimension, long tick, double x, double y, double z, double maximumStep, boolean accepted) {
        double horizontal = Math.hypot(x - this.x,z - this.z);
        double spatial = Math.hypot(horizontal,y - this.y);
        return sample(dimension,tick,x,y,z,maximumStep,accepted,horizontal,spatial);
    }
    /** Native packet aggregation may contain a bent path longer than the tick's endpoint chord. */
    public Sample sample(String dimension, long tick, double x, double y, double z, double maximumStep,
                         boolean accepted, double horizontal, double spatial) {
        if (!active()) return new Sample(false,true,0,0,0,0,0,0);
        if (!Objects.equals(this.dimension,dimension) || tick < lastTick || !finite(x,y,z)
                || !Double.isFinite(maximumStep) || maximumStep <= 0 || !Double.isFinite(horizontal)
                || !Double.isFinite(spatial) || horizontal < 0 || spatial < horizontal
                || spatial > maximumStep || Math.hypot(Math.hypot(x-this.x,z-this.z),y-this.y) > maximumStep) {
            stop(); return new Sample(false,true,0,0,0,0,0,0);
        }
        if (tick == lastTick || !accepted) return current(false,false,0,0);
        if (horizontal + 1e-7 < Math.hypot(x-this.x,z-this.z)
                || spatial + 1e-7 < Math.hypot(Math.hypot(x-this.x,z-this.z),y-this.y)) {
            stop(); return new Sample(false,true,0,0,0,0,0,0);
        }
        lastTick = tick; this.x = x; this.y = y; this.z = z;
        horizontalPath = Math.min(PATH_CAP,horizontalPath + horizontal);
        spatialPath = Math.min(PATH_CAP,spatialPath + spatial);
        return current(true,false,horizontal,spatial);
    }
    private Sample current(boolean accepted, boolean discontinuity, double horizontal, double spatial) {
        return new Sample(accepted,discontinuity,horizontal,spatial,Math.hypot(x-startX,z-startZ),y-startY,horizontalPath,spatialPath);
    }
    private static boolean finite(double x,double y,double z) { return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z); }
}
