package com.mistaboom.essence_ascendance.projectile;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;

public final class ProjectilePlatform {
    private ProjectilePlatform() { }
    @ExpectPlatform
    public static boolean allowImpact(Projectile projectile, HitResult hit) { throw new AssertionError(); }
}
