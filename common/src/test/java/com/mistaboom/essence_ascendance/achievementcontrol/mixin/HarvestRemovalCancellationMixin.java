package com.mistaboom.essence_ascendance.achievementcontrol.mixin;

import com.mistaboom.essence_ascendance.achievementcontrol.HarvestRemovalCancellation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forces a genuine native removal failure after GameMode permission checks have already succeeded. */
@Mixin(Level.class)
public abstract class HarvestRemovalCancellationMixin {
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"),cancellable=true,require=1)
    private void essenceAscendance$denyActualRemoval(BlockPos position,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> cir){
        if(HarvestRemovalCancellation.rejects((Level)(Object)this,position))cir.setReturnValue(false);
    }
}
