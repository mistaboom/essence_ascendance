package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.gathering.GeneratedOreObservations;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** Scattered ores and ordinary/modded feature writes through the real generation-only region. */
@Mixin(WorldGenRegion.class)
public abstract class WorldGenOreOriginMixin {
    @WrapMethod(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z")
    private boolean essenceAscendance$generatedOre(BlockPos position,BlockState state,int flags,int recursion,Operation<Boolean> original) {
        var region=(WorldGenRegion)(Object)this;
        // The native method returns true even for an unchanged state. A no-op is not origin evidence.
        // Avoid reading beyond its cached chunks before the native rejection boundary.
        BlockState before=region.hasChunk(position.getX()>>4,position.getZ()>>4)?region.getBlockState(position):null;
        boolean success=original.call(position,state,flags,recursion);
        if(success&&region.getBlockState(position)==state) {
            if(before!=null&&before!=state)
                GeneratedOreObservations.generated(region.getLevel().getServer(),region.getLevel().dimension().location().toString(),position,state);
            else if(!com.mistaboom.essence_ascendance.gathering.NaturalOreDropService.isOreType(state))
                GeneratedOreObservations.mutation(region.getLevel().getServer(),region.getLevel().dimension().location().toString(),position);
        }
        return success;
    }
}
