package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.gathering.CommittedHarvest;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge's actual native removal result, observed before the later Block.destroy callback. */
@Mixin(ServerPlayerGameMode.class)
public abstract class NeoForgeHarvestRemovalMixin {
    //? if >=26.1.2
    /*@WrapOperation(method="removeBlock",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;onDestroyedByPlayer(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/world/level/material/FluidState;)Z",remap=false),remap=false,require=1)*/
    //? if <26.1.2
    @WrapOperation(method="removeBlock",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;onDestroyedByPlayer(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/level/material/FluidState;)Z",remap=false),remap=false,require=1)
    private boolean essenceAscendance$committedRemoval(BlockState state,Level level,BlockPos position,Player player,
            //? if >=26.1.2
            /*ItemStack tool,*/
            boolean canHarvest,FluidState fluid,Operation<Boolean> original) {
        BlockState before=level.getBlockState(position);
        //? if >=26.1.2
        /*boolean removed=original.call(state,level,position,player,tool,canHarvest,fluid);*/
        //? if <26.1.2
        boolean removed=original.call(state,level,position,player,canHarvest,fluid);
        if(level instanceof ServerLevel server)CommittedHarvest.removed(server,position,before,removed);
        return removed;
    }
}
