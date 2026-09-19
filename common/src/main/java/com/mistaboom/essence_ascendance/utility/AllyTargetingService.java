package com.mistaboom.essence_ascendance.utility;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.List;

/** Deterministic friendly target selection shared by support effects. */
public final class AllyTargetingService {
    private AllyTargetingService() { }

    public static boolean allied(ServerPlayer owner, LivingEntity target) {
        if (target == owner || !target.isAlive() || target.isRemoved() || target.level() != owner.level()) return false;
        if (target instanceof Player) return owner.isAlliedTo(target);
        if (!(target instanceof OwnableEntity ownable)) return false;
        LivingEntity creatureOwner = ownable.getOwner();
        return creatureOwner != null && (creatureOwner == owner || owner.isAlliedTo(creatureOwner));
    }

    public static List<LivingEntity> nearby(ServerPlayer owner, double radiusBlocks, int maximumTargets) {
        if (!Double.isFinite(radiusBlocks) || radiusBlocks <= 0 || maximumTargets <= 0) return List.of();
        double radiusSquared = radiusBlocks * radiusBlocks;
        return owner.serverLevel().getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(radiusBlocks),
                        target -> allied(owner, target) && owner.distanceToSqr(target) <= radiusSquared)
                .stream()
                .sorted(Comparator.<LivingEntity>comparingDouble(owner::distanceToSqr)
                        .thenComparing(target -> target.getUUID().toString()))
                .limit(maximumTargets)
                .toList();
    }
}
