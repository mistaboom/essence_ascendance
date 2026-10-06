package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.equipment.PlayerAttributedBlockHarvestService;
import com.mistaboom.essence_ascendance.gathering.GatheringToolResolver;
import com.mistaboom.essence_ascendance.skill.effect.GatheringMiningEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerPlayerGameMode.class)
public abstract class AttunementHarvestMixin {
    @Shadow @Final protected ServerPlayer player;
    @Shadow protected ServerLevel level;

    @WrapMethod(method = "destroyBlock")
    private boolean essenceAscendance$committedHarvest(BlockPos pos, Operation<Boolean> original) {
        BlockState state = level.getBlockState(pos);
        GatheringToolResolver.HarvestScope toolScope = GatheringToolResolver.beginHarvest(player, state);
        ItemStack resolvedTool = toolScope.activeTool().copy();
        AttunementGameplay.beginHarvest(player, pos, state,
                PlayerAttributedBlockHarvestService.blocksPlayerHarvest(state, level.getBlockEntity(pos)));
        boolean completed = false;
        try {
            completed = original.call(pos);
            return completed;
        } finally {
            toolScope.close();
            AttunementGameplay.finishHarvest(completed);
            if (completed) GatheringMiningEffects.successfulHarvest(player, pos, state, resolvedTool);
        }
    }
}
