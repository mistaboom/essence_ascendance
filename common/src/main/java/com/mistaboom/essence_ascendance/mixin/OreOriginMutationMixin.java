package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.gathering.GeneratedOreObservations;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Current-state proof is consumed before all ordinary player/command/piston block writes. */
@Mixin(Level.class)
public abstract class OreOriginMutationMixin {
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"),require=1)
    private void essenceAscendance$invalidateOreOrigin(BlockPos position,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> cir) {
        if((Object)this instanceof ServerLevel level) {
            GeneratedOreObservations.mutation(level.getServer(),level.dimension().location().toString(),position);
            if (level.hasChunkAt(position) && level.getBlockEntity(position) instanceof com.mistaboom.essence_ascendance.ore.LatentOreBlockEntity ore)
                ore.clearGeneratedOrigin();
        }
    }
}
