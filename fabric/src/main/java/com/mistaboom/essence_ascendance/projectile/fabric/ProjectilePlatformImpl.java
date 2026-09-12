package com.mistaboom.essence_ascendance.projectile.fabric;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;

public final class ProjectilePlatformImpl {
    private ProjectilePlatformImpl() { }
    public static boolean allowImpact(Projectile projectile, HitResult hit) { return true; }
}
