package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.gathering.GatheringToolResolver;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** Client/server prediction hook for Tool Instinct's shared carried-tool resolver. */
@Mixin(Player.class)
public abstract class ToolInstinctPlayerMixin {
    @WrapMethod(method = "getDestroySpeed")
    private float essenceAscendance$toolInstinctSpeed(BlockState state, Operation<Float> original) {
        Player player = (Player) (Object) this;
        try (GatheringToolResolver.PreviewScope scope = GatheringToolResolver.beginPreview(player, state)) {
            float nativeSpeed = original.call(state);
            return GatheringToolResolver.adjustResolvedDestroySpeed(player, state, nativeSpeed, scope.candidate());
        }
    }

    @WrapMethod(method = "hasCorrectToolForDrops")
    private boolean essenceAscendance$toolInstinctHarvest(BlockState state, Operation<Boolean> original) {
        try (GatheringToolResolver.PreviewScope ignored = GatheringToolResolver.beginPreview((Player) (Object) this, state)) {
            return original.call(state);
        }
    }
}
