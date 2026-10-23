package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mistaboom.essence_ascendance.movement.TraversalService;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Remove only the native off-ground tool penalty in supported immersion. The native submerged
 * efficiency attribute handles the other water penalty; tool rules and other modifiers remain.
 * NeoForge's position-sensitive getDigSpeed and Fabric's getDestroySpeed consume the same policy. */
@Mixin(Player.class)
abstract class TraversalMiningMixin {
    //? if >=26.1.2
    /*@ModifyExpressionValue(method = "getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)F", remap = false,*/
    //? if <26.1.2
    @ModifyExpressionValue(method = "getDigSpeed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)F", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;onGround()Z", remap = true), require = 1, expect = 1)
    private boolean essenceAscendance$swimmingToolEfficiency(boolean original) {
        return original || TraversalService.fluidBody((Player)(Object)this);
    }
    //? if >=26.1.2 {
    /*@com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)F")
    private float essenceAscendance$toolInstinctSpeed(net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Float> original) {
        Player player = (Player)(Object)this;
        try (var scope = com.mistaboom.essence_ascendance.gathering.GatheringToolResolver.beginPreview(player, state)) {
            return com.mistaboom.essence_ascendance.gathering.GatheringToolResolver.adjustResolvedDestroySpeed(player, state,
                    original.call(state, pos), scope.candidate());
        }
    }
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z")
    private boolean essenceAscendance$toolInstinctHarvest(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
        try (var scope = com.mistaboom.essence_ascendance.gathering.GatheringToolResolver.beginPreview((Player)(Object)this, state)) {
            return original.call(state, level, pos);
        }
    }
    *///?}
}
