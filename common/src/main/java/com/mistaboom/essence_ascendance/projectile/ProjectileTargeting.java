package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

/** One deterministic acquisition/retargeting service for both source profiles. */
public final class ProjectileTargeting {
    private ProjectileTargeting() { }
    public static boolean canHarm(ServerPlayer owner, Entity target) {
        if (!target.isAlive() || target.isRemoved() || target.isSpectator() || target.isInvulnerable()
                || !EquipmentDamageService.canSkillHarm(owner, target)) return false;
        if (target instanceof Player player && player.getAbilities().invulnerable) return false;
        return !(target instanceof TamableAnimal pet) || !pet.isOwnedBy(owner);
    }
    public static boolean hostile(ServerPlayer owner, LivingEntity target) {
        if (!canHarm(owner, target) || owner.isAlliedTo(target)) return false;
        return target instanceof Enemy || target instanceof Player
                || target instanceof Mob mob && mob.getTarget() == owner;
    }
    public static boolean visible(Projectile projectile, Vec3 start, Vec3 end) {
        return projectile.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, projectile)).getType() == HitResult.Type.MISS;
    }
    public static LivingEntity acquire(Projectile projectile, ServerPlayer owner, Vec3 origin, Vec3 aim,
                                        double range, double cone, Set<UUID> excluded) {
        if (range <= 0) return null;
        Vec3 direction = aim.normalize();
        double cosine = Math.cos(Math.toRadians(cone));
        return owner.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(origin, origin).inflate(range),
                        target -> hostile(owner, target) && !excluded.contains(target.getUUID())
                                && target.getBoundingBox().getCenter().distanceToSqr(origin) <= range * range
                                && target.getBoundingBox().getCenter().subtract(origin).normalize().dot(direction) >= cosine
                                && visible(projectile, origin, target.getBoundingBox().getCenter()))
                .stream().min(Comparator.<LivingEntity>comparingDouble(target ->
                                1 - target.getBoundingBox().getCenter().subtract(origin).normalize().dot(direction))
                        .thenComparingDouble(target -> target.getBoundingBox().getCenter().distanceToSqr(origin))
                        .thenComparing(Entity::getUUID)).orElse(null);
    }
    public static LivingEntity retarget(Projectile projectile, ServerPlayer owner, ProjectileState state) {
        Vec3 origin = projectile.position();
        double radius = Math.min(state.ricochetRadius, state.remainingRange);
        return owner.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(origin, origin).inflate(radius),
                        target -> hostile(owner, target) && !state.visited.contains(target.getUUID())
                                && target.getBoundingBox().getCenter().distanceToSqr(origin) <= radius * radius
                                && visible(projectile, origin, target.getBoundingBox().getCenter()))
                .stream().min(Comparator.<LivingEntity>comparingDouble(target ->
                                target.getBoundingBox().getCenter().distanceToSqr(origin))
                        .thenComparing(Entity::getUUID)).orElse(null);
    }
    public static void steer(Projectile projectile, ServerPlayer owner, ProjectileState state) {
        if (state.target == null) return;
        Entity entity = owner.serverLevel().getEntity(state.target);
        if (!(entity instanceof LivingEntity target) || !hostile(owner, target)
                || state.visited.contains(target.getUUID())
                || !visible(projectile, projectile.position(), target.getBoundingBox().getCenter())) {
            state.target = null; // Acquire once; losing a lock never starts a target oscillation.
            return;
        }
        projectile.setDeltaMovement(turn(projectile.getDeltaMovement(),
                target.getBoundingBox().getCenter().subtract(projectile.position()), state.profile.turnDegreesPerTick()));
        projectile.hasImpulse = true;
    }
    public static Vec3 turn(Vec3 velocity, Vec3 desired, double degrees) {
        double speed = velocity.length();
        if (speed < 0.000001 || desired.lengthSqr() < 0.000001 || degrees <= 0) return velocity;
        Vec3 current = velocity.scale(1 / speed), goal = desired.normalize();
        double angle = Math.acos(Math.clamp(current.dot(goal), -1, 1));
        double step = Math.min(angle, Math.toRadians(degrees));
        if (angle <= step + 0.000001) return goal.scale(speed);
        Vec3 tangent = goal.subtract(current.scale(current.dot(goal)));
        if (tangent.lengthSqr() < 0.000001) return velocity;
        return current.scale(Math.cos(step)).add(tangent.normalize().scale(Math.sin(step))).normalize().scale(speed);
    }
}
