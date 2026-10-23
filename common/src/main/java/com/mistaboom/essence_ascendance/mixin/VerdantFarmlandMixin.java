package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.gathering.GatheringRuralService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
//? if >=26.1.2
/*import net.minecraft.world.level.block.FarmlandBlock;*/
//? if <26.1.2
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >=26.1.2
/*@Mixin(FarmlandBlock.class)*/
//? if <26.1.2
@Mixin(FarmBlock.class)
public abstract class VerdantFarmlandMixin {
    //? if >=26.1.2
    /*@WrapOperation(method = "fallOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/FarmlandBlock;turnToDirt(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))*/
    //? if <26.1.2
    @WrapOperation(method = "fallOn", at = @At(value = "INVOKE", target =
            //? if <26.1.2
            "Lnet/minecraft/world/level/block/FarmBlock;turnToDirt(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private void essenceAscendance$protectFarmland(Entity entity, BlockState state, Level level, BlockPos pos,
                                                    Operation<Void> original) {
        if (!GatheringRuralService.protectsFragileGround(entity)) original.call(entity, state, level, pos);
    }
}
