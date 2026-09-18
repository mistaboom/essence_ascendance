package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mistaboom.essence_ascendance.movement.TraversalService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** State dispatch covers opted-in vanilla and future terrain without special-casing block classes. */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class TraversalBlockStateMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$terrainInside(Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (TraversalService.ignoresInside(entity, (BlockState)(Object)this)) ci.cancel();
    }
    @ModifyReturnValue(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("RETURN"))
    private VoxelShape essenceAscendance$nativeSurface(VoxelShape original, BlockGetter level, BlockPos pos, CollisionContext context) {
        return TraversalService.collision((BlockState)(Object)this, level, pos, context, original);
    }
}
