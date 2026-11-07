package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.equipment.PlayerAttributedBlockHarvestService;
import com.mistaboom.essence_ascendance.gathering.GatheringToolResolver;
import com.mistaboom.essence_ascendance.gathering.CommittedHarvest;
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
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayerGameMode.class)
public abstract class AttunementHarvestMixin {
    @Shadow @Final protected ServerPlayer player;
    @Shadow protected ServerLevel level;

    @WrapMethod(method = "destroyBlock")
    private boolean essenceAscendance$committedHarvest(BlockPos pos, Operation<Boolean> original) {
        BlockState state = level.getBlockState(pos);
        GatheringToolResolver.HarvestScope toolScope = GatheringToolResolver.beginHarvest(player, state);
        ItemStack resolvedTool = toolScope.activeTool().copy();
        var evidence = GatheringMiningEffects.captureHarvest(player, pos, state);
        AttunementGameplay.beginHarvest(player, pos, state,
                PlayerAttributedBlockHarvestService.blocksPlayerHarvest(state, level.getBlockEntity(pos)));
        var removal = CommittedHarvest.begin(level,pos,state);
        boolean completed = false;
        try {
            boolean nativeResult = original.call(pos);
            completed = nativeResult && removal.committed();
            return nativeResult;
        } finally {
            removal.close();
            toolScope.close();
            AttunementGameplay.finishHarvest(completed);
            if (completed) GatheringMiningEffects.successfulHarvest(player, pos, state, resolvedTool, evidence);
        }
    }

    // NeoForge replaces this call with onDestroyedByPlayer; its required native adapter observes that result.
    @WrapOperation(method="destroyBlock",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"),require=0)
    private boolean essenceAscendance$actualNativeRemoval(ServerLevel level,BlockPos position,boolean moving,Operation<Boolean> original) {
        BlockState before=level.getBlockState(position);
        boolean removed=original.call(level,position,moving);
        CommittedHarvest.removed(level,position,before,removed);
        return removed;
    }
}
