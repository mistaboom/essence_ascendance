package com.mistaboom.essence_ascendance.projectile;

import net.minecraft.world.entity.projectile.Projectile;

/** Request the native movement update after authoritative velocity changes. */
final class ProjectileMotionSync {
    private ProjectileMotionSync() { }

    static void mark(Projectile projectile) {
        //? if >=26.1.2
        /*projectile.hurtMarked = true;*/
        //? if <26.1.2
        projectile.hasImpulse = true;
    }
}
