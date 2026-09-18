package com.mistaboom.essence_ascendance.movement;

import com.mistaboom.essence_ascendance.skill.CommittedSkillAccess;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.ImmobilizationController;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** Shared mode/permission boundary; native controls continue unchanged outside ordinary land/air movement. */
public final class MovementAbilityRules {
    private MovementAbilityRules() { }
    public static ResourceLocation style(Player player) {
        if (CommittedSkillAccess.isEffective(player, SkillIds.VECTOR_JUMP)) return SkillIds.VECTOR_JUMP;
        if (CommittedSkillAccess.isEffective(player, SkillIds.DOUBLE_JUMP)) return SkillIds.DOUBLE_JUMP;
        if (CommittedSkillAccess.isEffective(player, SkillIds.CHARGED_JUMP)) return SkillIds.CHARGED_JUMP;
        return null;
    }
    public static MovementAbilityState.Mode mode(ResourceLocation id) {
        return SkillIds.CHARGED_JUMP.equals(id) ? MovementAbilityState.Mode.CHARGED : MovementAbilityState.Mode.AIR;
    }
    public static boolean allowed(Player player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && !player.isSleeping()
                && !player.isPassenger() && !player.isFallFlying() && !player.getAbilities().flying
                && !player.isInWater() && !player.isInLava() && !player.onClimbable()
                && !player.hasEffect(MobEffects.LEVITATION) && !ImmobilizationController.active(player);
    }
    /** Require actual native support beneath the feet, not just a client-provided on-ground flag.
     * The epsilon only tests geometric contact; it is not a configurable leap height or coyote-time allowance. */
    public static boolean supported(Player player) {
        // Native onGround can briefly be false while walking/sprinting even with
        // feet on a collision surface. Geometry decides contact on both sides;
        // a transient flag alone must never turn a ground press into an air jump.
        final double epsilon = 1.0E-5;
        AABB box = player.getBoundingBox();
        AABB feet = new AABB(box.minX + epsilon, box.minY - epsilon, box.minZ + epsilon,
                box.maxX - epsilon, box.minY + epsilon, box.maxZ - epsilon);
        return player.level().getBlockCollisions(player, feet).iterator().hasNext();
    }
}
