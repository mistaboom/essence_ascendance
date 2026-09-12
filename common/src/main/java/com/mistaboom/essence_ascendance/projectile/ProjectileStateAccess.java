package com.mistaboom.essence_ascendance.projectile;

/** Implemented on Projectile by a common mixin; state never comes from a client packet. */
public interface ProjectileStateAccess {
    boolean essenceAscendance$secondary();
    void essenceAscendance$secondary(boolean secondary);
    ProjectileState essenceAscendance$state();
    void essenceAscendance$state(ProjectileState state);
    boolean essenceAscendance$launchChecked();
    void essenceAscendance$launchChecked(boolean checked);
}
