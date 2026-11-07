package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.gathering.GeneratedOreObservations;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Unloaded current blocks cannot supply continuous session provenance. */
@Mixin(ServerLevel.class)
public abstract class GeneratedOreUnloadMixin {
    @Inject(method="unload",at=@At("HEAD"),require=1)
    private void essenceAscendance$unloadedOreProof(LevelChunk chunk,CallbackInfo ci) {
        var level=(ServerLevel)(Object)this;
        GeneratedOreObservations.unloaded(level.getServer(),level.dimension().location().toString(),chunk.getPos());
    }
}
