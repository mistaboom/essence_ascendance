package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.movement.TraversalService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class TraversalEntityMixin {
    @ModifyReturnValue(method = {"getBlockSpeedFactor", "getBlockJumpFactor"}, at = @At("RETURN"))
    private float essenceAscendance$terrainFactor(float original) {
        return TraversalService.terrainFactor((Entity)(Object)this, original);
    }
    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$terrainDrag(BlockState state, Vec3 multiplier, CallbackInfo ci) {
        if (TraversalService.ignoresDrag((Entity)(Object)this, state)) ci.cancel();
    }
    // Damage rejection alone leaves lava's fire timer running. Block only lava's native ignition;
    // do not clear another source's timer, change fireImmune(), or grant protection after exit.
    @WrapOperation(method = "lavaHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;igniteForSeconds(F)V"), require = 1, expect = 1)
    private void essenceAscendance$lavaIgnition(Entity entity, float seconds, Operation<Void> original) {
        if (!TraversalService.protectedLavaImmersion(entity)) original.call(entity, seconds);
    }
    // Both loaders retain these two native state commits; NeoForge replaces the vanilla
    // FluidState tag condition, so do not target that removed vanilla-only instruction.
    @ModifyArg(method = "updateSwimming", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setSwimming(Z)V"),
            index = 0, require = 2, expect = 2)
    private boolean essenceAscendance$nativeSwimmingPose(boolean original) {
        return TraversalService.swimmingPose((Entity)(Object)this, original);
    }
    @ModifyExpressionValue(method = "isVisuallyCrawling", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isInWater()Z"))
    private boolean essenceAscendance$swimmingNotCrawling(boolean original) {
        return TraversalService.swimmingWater((Entity)(Object)this, original);
    }
}
