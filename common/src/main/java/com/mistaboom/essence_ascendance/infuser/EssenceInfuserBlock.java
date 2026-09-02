package com.mistaboom.essence_ascendance.infuser;

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

public final class EssenceInfuserBlock extends Block implements EntityBlock {

    public EssenceInfuserBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EssenceInfuserBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> blockEntityType
    ) {
        if (level.isClientSide
                || !EssenceInfuserContent.ESSENCE_INFUSER_BLOCK_ENTITY.get().equals(blockEntityType)) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) ->
                EssenceInfuserBlockEntity.serverTick(
                        (ServerLevel) tickLevel,
                        pos,
                        tickState,
                        (EssenceInfuserBlockEntity) blockEntity
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
                && level.getBlockEntity(pos) instanceof EssenceInfuserBlockEntity infuser) {
            infuser.bindOwner(player);
            infuser.refreshLink();
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
        if (!(level.getBlockEntity(pos) instanceof EssenceInfuserBlockEntity infuser)) {
            return InteractionResult.PASS;
        }
        if (!infuser.bindOwner(player) || !infuser.canPlayerUse(player)) {
            player.displayClientMessage(
                    Component.translatable("message.essence_ascendance.infuser_private"),
                    true
            );
            return InteractionResult.FAIL;
        }
        infuser.refreshLink();
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(infuser);
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
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof EssenceInfuserBlockEntity infuser
                && !level.isClientSide) {
            for (int slot = 0; slot < infuser.getContainerSize(); slot++) {
                ItemStack stack = infuser.removeItemNoUpdate(slot);
                if (!stack.isEmpty()) {
                    popResource(level, pos, stack);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
