package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.equipment.PlayerAttributedBlockHarvestService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Shared loader-neutral bridge from vanilla block loot into Crop Yield. */
@Mixin(Block.class)
public abstract class BlockCropYieldMixin {

    @Inject(
            method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void essenceAscendance$applyCropYield(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            BlockEntity blockEntity,
            Entity breaker,
            ItemStack tool,
            CallbackInfoReturnable<List<ItemStack>> cir
    ) {
        cir.setReturnValue(
                PlayerAttributedBlockHarvestService.applyCropYield(
                        state,
                        level,
                        pos,
                        blockEntity,
                        breaker,
                        tool,
                        cir.getReturnValue()
                )
        );
    }
}
