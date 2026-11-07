package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mistaboom.essence_ascendance.gathering.GeneratedOreObservations;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Vanilla vein placement bypasses WorldGenRegion.setBlock through BulkSectionAccess. */
@Mixin(OreFeature.class)
public abstract class OreFeatureOriginMixin {
    @WrapOperation(method="doPlace",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"),require=1)
    private BlockState essenceAscendance$generatedVein(LevelChunkSection section,int x,int y,int z,BlockState state,boolean locking,Operation<BlockState> original,
                                                     @Local(argsOnly=true) WorldGenLevel level,@Local BlockPos.MutableBlockPos position) {
        BlockState previous=original.call(section,x,y,z,state,locking);
        // /place feature supplies ServerLevel, and therefore cannot manufacture generated-origin proof.
        if(level instanceof WorldGenRegion region&&previous!=state&&section.getBlockState(x,y,z)==state)
            GeneratedOreObservations.generated(region.getLevel().getServer(),region.getLevel().dimension().location().toString(),position,state);
        return previous;
    }
}
