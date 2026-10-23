package com.mistaboom.essence_ascendance.verification;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/** Calls the server-authoritative native damage entry point on both targets. */
public final class NativeDamageTestCompat {
    private NativeDamageTestCompat() { }

    public static boolean hurt(Entity entity, DamageSource source, float amount) {
        //? if >=26.1.2
        /*return entity.hurtServer((net.minecraft.server.level.ServerLevel) entity.level(), source, amount);*/
        //? if <26.1.2
        return entity.hurt(source, amount);
    }
}
