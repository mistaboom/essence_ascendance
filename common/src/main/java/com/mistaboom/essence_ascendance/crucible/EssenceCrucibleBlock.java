package com.mistaboom.essence_ascendance.crucible;

import com.mistaboom.essence_ascendance.network.EssenceCrucibleNetworkService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class EssenceCrucibleBlock extends Block implements EntityBlock {

    public EssenceCrucibleBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        return new EssenceCrucibleBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> blockEntityType
    ) {
        if (level.isClientSide
                || !EssenceCrucibleContent.ESSENCE_CRUCIBLE_BLOCK_ENTITY.get().equals(blockEntityType)) {
            return null;
        }

        return (tickLevel, pos, tickState, blockEntity) ->
                EssenceCrucibleBlockEntity.serverTick(
                        (ServerLevel) tickLevel,
                        pos,
                        tickState,
                        (EssenceCrucibleBlockEntity) blockEntity
                );
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            LivingEntity placer,
            ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide
                && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof EssenceCrucibleBlockEntity crucible) {
            crucible.bindOwner(player);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(pos) instanceof EssenceCrucibleBlockEntity crucible)) {
            return InteractionResult.PASS;
        }

        if (!crucible.bindOwner(player) || !crucible.canPlayerUse(player)) {
            player.displayClientMessage(
                    Component.translatable("message.essence_ascendance.crucible_private"),
                    true
            );
            return InteractionResult.FAIL;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(crucible);
            EssenceCrucibleNetworkService.forceSync(serverPlayer);
        }

        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean movedByPiston
    ) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof EssenceCrucibleBlockEntity crucible) {
                crucible.stopChanneling();
                if (!level.isClientSide && !crucible.isEmpty()) {
                    /*
                     * Remove the stack from the block entity before spawning it.
                     * This is safe whether a loader later performs its own
                     * Container-removal side effect: the inventory is already
                     * empty, so the input cannot be dropped twice.
                     */
                    ItemStack input = crucible.removeItemNoUpdate(0);
                    if (!input.isEmpty()) {
                        popResource(level, pos, input);
                    }
                }
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
