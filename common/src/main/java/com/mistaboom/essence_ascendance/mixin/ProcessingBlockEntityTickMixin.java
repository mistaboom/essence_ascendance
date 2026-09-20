package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.utility.ProcessingAccelerationService;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Reuses each eligible processor's native ticker; no copied recipe or machine timing logic. */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class ProcessingBlockEntityTickMixin {
    @Shadow @Final private BlockEntity blockEntity;

    @WrapMethod(method = "tick")
    private void essenceAscendance$accelerateProcessor(Operation<Void> original) {
        ProcessingAccelerationService.Plan plan = ProcessingAccelerationService.plan(blockEntity);
        original.call();
        for (int tick = 0; tick < plan.extraTicks(); tick++) original.call();
    }
}
