package com.mistaboom.essence_ascendance.posture;

import net.minecraft.world.damagesource.DamageSource;

/** Optional loader bookkeeping cleanup when the common dodge ends a native hit early. */
public interface NativeDamageCleanup {
    void essenceAscendance$finishDodgedDamage(DamageSource source);
}
