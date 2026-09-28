package com.mistaboom.essence_ascendance.pylon;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Deterministic right-handed frame for upright-authored Pylon geometry.
 * Local +Y is the tower axis, local +X is right, and local +Z is forward.
 */
public record PylonLocalFrame(Direction direction, int quarterTurns, Vec3 right, Vec3 axis, Vec3 forward) {
    private static final Vec3 CENTER = new Vec3(0.5, 0.5, 0.5);

    public static PylonLocalFrame of(Direction direction) {
        return switch (direction) {
            case UP -> new PylonLocalFrame(direction, 0,
                    new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1));
            case DOWN -> new PylonLocalFrame(direction, 0,
                    new Vec3(1, 0, 0), new Vec3(0, -1, 0), new Vec3(0, 0, -1));
            case NORTH -> new PylonLocalFrame(direction, 0,
                    new Vec3(1, 0, 0), new Vec3(0, 0, -1), new Vec3(0, 1, 0));
            case SOUTH -> new PylonLocalFrame(direction, 0,
                    new Vec3(1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, -1, 0));
            case EAST -> new PylonLocalFrame(direction, 0,
                    new Vec3(0, -1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1));
            case WEST -> new PylonLocalFrame(direction, 0,
                    new Vec3(0, 1, 0), new Vec3(-1, 0, 0), new Vec3(0, 0, 1));
        };
    }

    /** Roll is independent of the support axis, retaining every floor/wall/ceiling mount. */
    public static PylonLocalFrame of(Direction direction, int quarterTurns) {
        PylonLocalFrame base = of(direction);
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> base;
            case 1 -> new PylonLocalFrame(direction, 1, base.forward.scale(-1), base.axis, base.right);
            case 2 -> new PylonLocalFrame(direction, 2, base.right.scale(-1), base.axis, base.forward.scale(-1));
            default -> new PylonLocalFrame(direction, 3, base.forward, base.axis, base.right.scale(-1));
        };
    }

    public static PylonLocalFrame of(BlockState state) {
        return of(state.getValue(EssencePylonBlock.FACING), state.getValue(EssencePylonBlock.ROLL));
    }

    public Direction forwardDirection() {
        return Direction.getNearest(forward.x, forward.y, forward.z);
    }

    public static int rollForForward(Direction axis, Direction forward) {
        for (int roll = 0; roll < 4; roll++) {
            if (of(axis, roll).forwardDirection() == forward) return roll;
        }
        throw new IllegalArgumentException("Pylon forward must be perpendicular to mount axis");
    }

    /** Cardinal rotations preserve axis-aligned collision boxes exactly. */
    public VoxelShape transformShape(VoxelShape source) {
        VoxelShape result = Shapes.empty();
        for (AABB box : source.toAabbs()) {
            Vec3 first = localToBlock(new Vec3(box.minX, box.minY, box.minZ));
            Vec3 second = localToBlock(new Vec3(box.maxX, box.maxY, box.maxZ));
            result = Shapes.or(result, Block.box(
                    Math.min(first.x, second.x) * 16.0, Math.min(first.y, second.y) * 16.0,
                    Math.min(first.z, second.z) * 16.0, Math.max(first.x, second.x) * 16.0,
                    Math.max(first.y, second.y) * 16.0, Math.max(first.z, second.z) * 16.0));
        }
        return result.optimize();
    }

    /** Rotates a vector from upright Pylon-local space into block/world axes. */
    public Vec3 localVectorToWorld(Vec3 local) {
        return right.scale(local.x).add(axis.scale(local.y)).add(forward.scale(local.z));
    }

    /** Expresses a block/world-axis vector in upright Pylon-local coordinates. */
    public Vec3 worldVectorToLocal(Vec3 world) {
        return new Vec3(world.dot(right), world.dot(axis), world.dot(forward));
    }

    /** Rotates a point about the center of its block. */
    public Vec3 localToBlock(Vec3 local) {
        return CENTER.add(localVectorToWorld(local.subtract(CENTER)));
    }

    public Vec3 blockToLocal(Vec3 block) {
        return CENTER.add(worldVectorToLocal(block.subtract(CENTER)));
    }
}
