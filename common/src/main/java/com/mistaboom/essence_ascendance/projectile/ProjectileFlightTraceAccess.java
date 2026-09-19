package com.mistaboom.essence_ascendance.projectile;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Presentation-only native flight trace shared by projectile-aware skills. */
public interface ProjectileFlightTraceAccess {
    List<Vec3> essenceAscendance$flightTrace();
}
