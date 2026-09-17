package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.projectile.ProjectileTargeting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** Bounded creature-only radial control. Shares team/PvP/immunity policy and native knockback cancellation. */
public final class CrowdControlPulseService {
    private CrowdControlPulseService() { }
    public static int knockback(ServerPlayer owner, double radius, int maximumTargets, double strength) {
        if (!Double.isFinite(strength) || strength <= 0 || !owner.isAlive() || owner.isRemoved()) return 0;
        Vec3 origin = owner.getBoundingBox().getCenter();
        int accepted = 0;
        for (var target : SkillTargetingService.nearby(owner, owner, radius, Set.of(owner.getUUID()), maximumTargets,
                target -> ProjectileTargeting.hostile(owner, target) && CrowdControlEligibility.canControl(owner, target)
                        && target.getBoundingBox().getCenter().distanceToSqr(origin) <= radius * radius
                        && CollisionAttackService.visible(owner, origin, target.getBoundingBox().getCenter()))) {
            Vec3 outward = target.position().subtract(owner.position());
            if (outward.horizontalDistanceSqr() == 0)
                outward = CollisionAttackService.pushDirection(new Vec3(1, 0, 0), Vec3.ZERO, target.getUUID());
            Vec3 direction = outward;
            Vec3 before = target.getDeltaMovement();
            // A control pulse is a secondary outcome, never a fresh primary attack/reflection trigger.
            EquipmentDamageService.withSecondarySkillDamage(() -> target.knockback(strength, -direction.x, -direction.z));
            if (target.getDeltaMovement().subtract(before).lengthSqr() > 0) {
                target.hurtMarked = true;
                accepted++;
            }
        }
        return accepted;
    }
}
