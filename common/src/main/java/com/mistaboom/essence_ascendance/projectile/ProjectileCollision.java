package com.mistaboom.essence_ascendance.projectile;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.function.Predicate;

/** Swept contacts retain their exact position for range accounting and continuation. */
public final class ProjectileCollision {
    // Match ProjectileUtil's native projectile collision margin.
    private static final double HIT_MARGIN = 0.3F;

    private ProjectileCollision() { }

    public static EntityHitResult firstEntity(Projectile projectile, Vec3 start, Vec3 end,
                                               Predicate<Entity> eligible) {
        EntityHitResult nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        AABB search = projectile.getBoundingBox().expandTowards(end.subtract(start)).inflate(1);
        for (Entity candidate : projectile.level().getEntities(projectile, search, eligible)) {
            Optional<Vec3> contact = contact(candidate.getBoundingBox().inflate(HIT_MARGIN), start, end);
            if (contact.isEmpty()) continue;
            double distance = start.distanceToSqr(contact.get());
            if (distance < nearestDistance || distance == nearestDistance && nearest != null
                    && candidate.getUUID().compareTo(nearest.getEntity().getUUID()) < 0) {
                nearest = new EntityHitResult(candidate, contact.get());
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /** A shot starting inside an unvisited hitbox contacts it immediately. */
    public static Optional<Vec3> contact(AABB bounds, Vec3 start, Vec3 end) {
        return bounds.contains(start) ? Optional.of(start) : bounds.clip(start, end);
    }
}
