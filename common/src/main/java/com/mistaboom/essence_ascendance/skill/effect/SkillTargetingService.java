package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Deterministic, server-side nearby-target selection shared by area and chain effects. */
public final class SkillTargetingService {
    private SkillTargetingService() { }

    public static List<LivingEntity> nearby(ServerPlayer owner, Entity origin, double radius,
                                            Set<UUID> excluded, int limit) {
        return nearby(owner, origin, radius, excluded, limit, target -> true);
    }

    /** Apply caller-specific geometry/eligibility before the deterministic budget, not after it. */
    public static List<LivingEntity> nearby(ServerPlayer owner, Entity origin, double radius,
                                            Set<UUID> excluded, int limit,
                                            java.util.function.Predicate<LivingEntity> permitted) {
        if (!Double.isFinite(radius) || radius <= 0.0 || limit <= 0
                || origin.level() != owner.level()) return List.of();
        AABB bounds = origin.getBoundingBox().inflate(radius);
        return owner.serverLevel().getEntitiesOfClass(LivingEntity.class, bounds,
                        target -> target.isAlive() && !target.isRemoved()
                                && !excluded.contains(target.getUUID())
                                && EquipmentDamageService.canSkillHarm(owner, target) && permitted.test(target))
                .stream()
                .sorted(Comparator.<LivingEntity>comparingDouble(target -> origin.distanceToSqr(target))
                        .thenComparing(target -> target.getUUID().toString()))
                .limit(limit)
                .toList();
    }
}
