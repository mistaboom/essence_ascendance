package com.mistaboom.essence_ascendance.nexus;

import com.mistaboom.essence_ascendance.network.PlayerEssenceSyncService;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Stateless world access point for permanent player progression. */
public final class AscendanceNexusBlock extends Block {

    private static final Component TITLE =
            Component.translatable(
                    "container.essence_ascendance.ascendance_nexus"
            );

    public AscendanceNexusBlock(Properties properties) {
        super(properties);
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
