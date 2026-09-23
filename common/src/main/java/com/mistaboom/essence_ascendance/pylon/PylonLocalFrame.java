package com.mistaboom.essence_ascendance.pylon;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Deterministic right-handed frame for upright-authored Pylon geometry.
 * Local +Y is the tower axis, local +X is right, and local +Z is forward.
 */
public record PylonLocalFrame(Direction direction, Vec3 right, Vec3 axis, Vec3 forward) {
    private static final Vec3 CENTER = new Vec3(0.5, 0.5, 0.5);

    public static PylonLocalFrame of(Direction direction) {
        return switch (direction) {
            case UP -> new PylonLocalFrame(direction,
                    new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1));
            case DOWN -> new PylonLocalFrame(direction,
                    new Vec3(1, 0, 0), new Vec3(0, -1, 0), new Vec3(0, 0, -1));
            case NORTH -> new PylonLocalFrame(direction,
                    new Vec3(1, 0, 0), new Vec3(0, 0, -1), new Vec3(0, 1, 0));
            case SOUTH -> new PylonLocalFrame(direction,
                    new Vec3(1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, -1, 0));
            case EAST -> new PylonLocalFrame(direction,
                    new Vec3(0, -1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1));
            case WEST -> new PylonLocalFrame(direction,
                    new Vec3(0, 1, 0), new Vec3(-1, 0, 0), new Vec3(0, 0, 1));
        };
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
