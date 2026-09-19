package com.mistaboom.essence_ascendance.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to vanilla Creeper explosion identity for Threat Sense presentation. */
@Mixin(Creeper.class)
public interface CreeperExplosionAccess {
    @Accessor("explosionRadius")
    int essenceAscendance$getExplosionRadius();
}
