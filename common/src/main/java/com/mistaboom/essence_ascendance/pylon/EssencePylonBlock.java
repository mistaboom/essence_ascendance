package com.mistaboom.essence_ascendance.pylon;

import com.mistaboom.essence_ascendance.network.EssencePylonNetworkService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public final class EssencePylonBlock extends Block implements EntityBlock {
    public static final BooleanProperty FOCUS_LIT = BooleanProperty.create("focus_lit");
    /** Direction from the attached base/support side toward the tower tip and Focus. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    /* Exact 1/16-voxel decomposition of the authored Blockbench mesh. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2.0D, 0.0D, 2.0D, 14.0D, 1.0D, 7.0D),
            Block.box(2.0D, 0.0D, 9.0D, 14.0D, 1.0D, 14.0D),
            Block.box(5.0D, 0.0D, 7.0D, 11.0D, 2.0D, 9.0D),
            Block.box(3.0D, 1.0D, 3.0D, 13.0D, 2.0D, 7.0D),
            Block.box(3.0D, 1.0D, 9.0D, 13.0D, 2.0D, 13.0D),
            Block.box(7.0D, 2.0D, 6.0D, 11.0D, 12.0D, 10.0D),
            Block.box(9.0D, 2.0D, 5.0D, 11.0D, 12.0D, 6.0D),
            Block.box(9.0D, 2.0D, 10.0D, 11.0D, 12.0D, 11.0D),
            Block.box(5.0D, 2.0D, 5.0D, 7.0D, 12.0D, 11.0D),
            Block.box(4.0D, 12.0D, 4.0D, 12.0D, 14.0D, 7.0D),
            Block.box(4.0D, 12.0D, 9.0D, 12.0D, 14.0D, 12.0D),
            Block.box(5.0D, 12.0D, 7.0D, 11.0D, 14.0D, 9.0D),
            Block.box(4.0D, 14.0D, 9.0D, 5.0D, 16.0D, 11.0D),
            Block.box(4.0D, 14.0D, 4.0D, 5.0D, 16.0D, 7.0D),
            Block.box(4.0D, 14.0D, 11.0D, 12.0D, 16.0D, 12.0D),
            Block.box(11.0D, 14.0D, 9.0D, 12.0D, 16.0D, 11.0D),
            Block.box(5.0D, 14.0D, 4.0D, 12.0D, 16.0D, 5.0D),
            Block.box(11.0D, 14.0D, 5.0D, 12.0D, 16.0D, 7.0D)
    );
    private static final Map<Direction, VoxelShape> SHAPES = directionalShapes();

    public EssencePylonBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(FOCUS_LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FOCUS_LIT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return SHAPES.get(state.getValue(FACING));
    }

    private static Map<Direction, VoxelShape> directionalShapes() {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            PylonLocalFrame frame = PylonLocalFrame.of(direction);
            VoxelShape shape = Shapes.empty();
            for (AABB box : SHAPE.toAabbs()) {
                Vec3 first = frame.localToBlock(new Vec3(box.minX, box.minY, box.minZ));
                Vec3 second = frame.localToBlock(new Vec3(box.maxX, box.maxY, box.maxZ));
                shape = Shapes.or(shape, Block.box(
                        Math.min(first.x, second.x) * 16.0,
                        Math.min(first.y, second.y) * 16.0,
                        Math.min(first.z, second.z) * 16.0,
                        Math.max(first.x, second.x) * 16.0,
                        Math.max(first.y, second.y) * 16.0,
                        Math.max(first.z, second.z) * 16.0));
            }
            shapes.put(direction, shape.optimize());
        }
        return shapes;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EssencePylonBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> blockEntityType
    ) {
        if (level.isClientSide
                || !EssencePylonContent.ESSENCE_PYLON_BLOCK_ENTITY.get().equals(blockEntityType)) {
            return null;
        }

        return (tickLevel, pos, tickState, blockEntity) ->
                EssencePylonBlockEntity.serverTick(
                        (ServerLevel) tickLevel,
                        pos,
                        tickState,
                        (EssencePylonBlockEntity) blockEntity
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
                && level.getBlockEntity(pos) instanceof EssencePylonBlockEntity pylon) {
            pylon.bindOwner(player);
            pylon.refreshLink();
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

        if (!(level.getBlockEntity(pos) instanceof EssencePylonBlockEntity pylon)) {
            return InteractionResult.PASS;
        }

        if (!pylon.bindOwner(player) || !pylon.canPlayerUse(player)) {
            player.displayClientMessage(
                    Component.translatable("message.essence_ascendance.pylon_private"),
                    true
            );
            return InteractionResult.FAIL;
        }

        pylon.refreshLink();
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(pylon);
            EssencePylonNetworkService.forceSync(serverPlayer);
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
                && level.getBlockEntity(pos) instanceof EssencePylonBlockEntity pylon) {
            pylon.invalidateLinkedCrucible();
            if (!level.isClientSide && !pylon.isEmpty()) {
                ItemStack focus = pylon.removeItemNoUpdate(0);
                if (!focus.isEmpty()) {
                    popResource(level, pos, focus);
                }
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
