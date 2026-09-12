package com.mistaboom.essence_ascendance.projectile.neoforge;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.EventHooks;

public final class ProjectilePlatformImpl {
    private ProjectilePlatformImpl() { }
    public static boolean allowImpact(Projectile projectile, HitResult hit) {
        return !EventHooks.onProjectileImpact(projectile, hit);
    }
}
