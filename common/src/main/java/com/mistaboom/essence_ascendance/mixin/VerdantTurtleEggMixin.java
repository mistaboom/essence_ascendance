package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.gathering.GatheringRuralService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TurtleEggBlock.class)
public abstract class VerdantTurtleEggMixin {
    @Inject(method = "destroyEgg", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$protectTurtleEgg(Level level, BlockState state, BlockPos pos,
                                                     Entity entity, int inverseChance, CallbackInfo ci) {
        if (GatheringRuralService.protectsFragileGround(entity)) ci.cancel();
    }
}
