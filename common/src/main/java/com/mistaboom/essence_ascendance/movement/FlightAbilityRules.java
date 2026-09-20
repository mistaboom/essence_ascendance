package com.mistaboom.essence_ascendance.movement;

import com.mistaboom.essence_ascendance.skill.CommittedSkillAccess;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.ImmobilizationController;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/** Shared permission boundary for equipment-free glide and standard Abilities flight. */
public final class FlightAbilityRules {
    private FlightAbilityRules() { }

    /** Whether ordinary movement intent has at least one flight consumer on this side. */
    public static boolean usesMovementInput(Player player) {
        return CommittedSkillAccess.isEffective(player, SkillIds.ESSENCE_WINGS)
                || CommittedSkillAccess.isEffective(player, SkillIds.FATIGUE_FLIGHT)
                || CommittedSkillAccess.isEffective(player, SkillIds.VECTOR_BOOST)
                || CommittedSkillAccess.isEffective(player, SkillIds.UNTETHERED_FLIGHT);
    }

    /** Broad transport eligibility: do not reject input merely because a flight/glide is already active. */
    public static boolean inputAllowed(Player player) {
        return base(player) && !player.isCreative();
    }

    public static boolean wingsAllowed(Player player) {
        return inputAllowed(player) && !player.getAbilities().flying && !player.isInWater() && !player.isInLava()
                && !player.onClimbable() && !player.hasEffect(MobEffects.LEVITATION);
    }

    public static boolean freeFlightAllowed(Player player) {
        return inputAllowed(player) && !player.isFallFlying() && !player.isInWater() && !player.isInLava()
                && !player.onClimbable() && !player.hasEffect(MobEffects.LEVITATION);
    }

    private static boolean base(Player player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && !player.isSleeping()
                && !player.isPassenger() && !ImmobilizationController.active(player);
    }
}
