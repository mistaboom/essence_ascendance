package com.mistaboom.essence_ascendance.mixin;

import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Server mining state distinguishes continued block-breaking animations from melee swings. */
@Mixin(ServerPlayerGameMode.class)
public interface ProjectileMiningAccess {
    @Accessor("isDestroyingBlock") boolean essenceAscendance$destroyingBlock();
}
