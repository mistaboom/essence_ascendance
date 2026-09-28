package com.mistaboom.essence_ascendance.nexus;

import com.mistaboom.essence_ascendance.network.PlayerEssenceSyncService;
import com.mistaboom.essence_ascendance.machine.HorizontalMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Stateless world access point for permanent player progression. */
public final class AscendanceNexusBlock extends HorizontalMachineBlock implements EntityBlock {

    private static final Component TITLE =
            Component.translatable(
                    "container.essence_ascendance.ascendance_nexus"
            );

    /* Exact 1/16-voxel decomposition of the authored Blockbench mesh, rotated 90° clockwise to match the in-world orientation. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(12.0D, 0.0D, 0.0D, 16.0D, 2.0D, 4.0D),
            Block.box(12.0D, 0.0D, 12.0D, 16.0D, 2.0D, 16.0D),
            Block.box(0.0D, 0.0D, 0.0D, 4.0D, 2.0D, 4.0D),
            Block.box(0.0D, 0.0D, 12.0D, 4.0D, 2.0D, 16.0D),

            Block.box(12.0D, 2.0D, 1.0D, 16.0D, 11.0D, 4.0D),
            Block.box(12.0D, 2.0D, 12.0D, 16.0D, 11.0D, 15.0D),
            Block.box(12.0D, 2.0D, 0.0D, 15.0D, 11.0D, 1.0D),
            Block.box(12.0D, 2.0D, 15.0D, 15.0D, 11.0D, 16.0D),
            Block.box(1.0D, 2.0D, 0.0D, 4.0D, 11.0D, 4.0D),
            Block.box(1.0D, 2.0D, 12.0D, 4.0D, 11.0D, 16.0D),
            Block.box(0.0D, 2.0D, 1.0D, 1.0D, 11.0D, 4.0D),
            Block.box(0.0D, 2.0D, 12.0D, 1.0D, 11.0D, 15.0D),

            Block.box(0.0D, 11.0D, 1.0D, 16.0D, 13.0D, 15.0D),
            Block.box(1.0D, 11.0D, 0.0D, 15.0D, 13.0D, 1.0D),
            Block.box(1.0D, 11.0D, 15.0D, 15.0D, 13.0D, 16.0D),

            Block.box(0.0D, 13.0D, 0.0D, 16.0D, 14.0D, 16.0D),

            Block.box(14.0D, 14.0D, 0.0D, 16.0D, 15.0D, 16.0D),
            Block.box(0.0D, 14.0D, 0.0D, 14.0D, 15.0D, 2.0D),
            Block.box(0.0D, 14.0D, 14.0D, 14.0D, 15.0D, 16.0D),

            Block.box(15.0D, 15.0D, 0.0D, 16.0D, 16.0D, 16.0D),
            Block.box(0.0D, 15.0D, 0.0D, 15.0D, 16.0D, 1.0D),
            Block.box(0.0D, 15.0D, 15.0D, 15.0D, 16.0D, 16.0D)
    );

    public AscendanceNexusBlock(Properties properties) {
        super(properties, SHAPE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AscendanceNexusBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != AscendanceNexusContent.ASCENDANCE_NEXUS_BLOCK_ENTITY.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) ->
                AscendanceNexusBlockEntity.serverTick((ServerLevel) tickLevel, pos,
                        tickState, (AscendanceNexusBlockEntity) entity);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            for (Player player : level.players()) {
                if (player instanceof ServerPlayer serverPlayer
                        && player.containerMenu instanceof AscendanceNexusMenu menu
                        && menu.isAt(level, pos)) {
                    serverPlayer.closeContainer();
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
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

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        MenuProvider provider =
                new SimpleMenuProvider(
                        (containerId, inventory, openingPlayer) ->
                                new AscendanceNexusMenu(
                                        containerId,
                                        inventory,
                                        ContainerLevelAccess.create(
                                                level,
                                                pos
                                        )
                                ),
                        TITLE
                );

        serverPlayer.openMenu(provider);

        /*
         * The screen reads the same authoritative player snapshot used by the
         * rest of the mod. Force a refresh when opening so the Nexus never
         * starts from a stale presentation cache.
         */
        PlayerEssenceSyncService.forceSync(serverPlayer);

        return InteractionResult.CONSUME;
    }
}
