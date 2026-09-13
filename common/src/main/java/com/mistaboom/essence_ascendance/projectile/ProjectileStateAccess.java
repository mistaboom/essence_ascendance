package com.mistaboom.essence_ascendance.projectile;

/** Implemented on Projectile by a common mixin; state never comes from a client packet. */
public interface ProjectileStateAccess {
    ProjectileControlState essenceAscendance$control();
    void essenceAscendance$control(ProjectileControlState state);
    /** Server-authored arrow physics scale, replicated through native entity data for client prediction. */
    default float essenceAscendance$flightScale() { return 1; }
    default void essenceAscendance$flightScale(float scale) { }
    default boolean essenceAscendance$embedded() { return false; }
    default int[] essenceAscendance$nativeHitIds() { return new int[0]; }
    boolean essenceAscendance$secondary();
    void essenceAscendance$secondary(boolean secondary);
    ProjectileState essenceAscendance$state();
    void essenceAscendance$state(ProjectileState state);
    boolean essenceAscendance$launchChecked();
    void essenceAscendance$launchChecked(boolean checked);
}
