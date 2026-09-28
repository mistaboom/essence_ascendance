package com.mistaboom.essence_ascendance.machine;

import com.mistaboom.essence_ascendance.pylon.PylonLocalFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/** Placement and collision orientation for the west-facing authored machine meshes. */
public abstract class HorizontalMachineBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    protected HorizontalMachineBlock(Properties properties, VoxelShape authoredShape) {
        super(properties);
        // Missing properties in old saves resolve to this default: retain their old appearance.
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.WEST));
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            shapes.put(direction, frame(direction).transformShape(authoredShape));
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    public static PylonLocalFrame frame(Direction facing) {
        int quarterTurns = switch (facing) {
            case WEST -> 0;
            case SOUTH -> 1;
            case EAST -> 2;
            case NORTH -> 3;
            default -> throw new IllegalArgumentException("Horizontal machine facing required: " + facing);
        };
        return PylonLocalFrame.of(Direction.UP, quarterTurns);
    }
}
