package com.mistaboom.essence_ascendance.utility;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.Vec3;

/** Shared catch-up teleport that delegates to native tame safety when available. */
public final class SafeTeleportService {
    private SafeTeleportService() { }

    public static boolean catchUp(LivingEntity companion, ServerPlayer owner, double distanceBlocks) {
        if (!Double.isFinite(distanceBlocks) || distanceBlocks <= 0 || !companion.isAlive() || companion.isRemoved()
                || companion.level() != owner.level() || companion.isPassenger() || companion.isVehicle()
                || companion.distanceToSqr(owner) <= distanceBlocks * distanceBlocks) return false;

        Vec3 before = companion.position();
        if (companion instanceof TamableAnimal tame) {
            if (tame.isOrderedToSit()) return false;
            tame.tryToTeleportToOwner();
            if (!companion.position().equals(before)) {
                companion.fallDistance = 0;
                return true;
            }
            return false;
        }

        double spacing = Math.max(companion.getBbWidth(), owner.getBbWidth()) * 2.0;
        double[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (double[] direction : directions) {
            double length = Math.sqrt(direction[0] * direction[0] + direction[1] * direction[1]);
            double x = owner.getX() + spacing * direction[0] / length;
            double z = owner.getZ() + spacing * direction[1] / length;
            if (companion.randomTeleport(x, owner.getY(), z, true)) {
                companion.fallDistance = 0;
                return true;
            }
        }
        return false;
    }
}
