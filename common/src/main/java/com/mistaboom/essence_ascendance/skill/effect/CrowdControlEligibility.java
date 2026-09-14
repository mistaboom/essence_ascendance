package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.projectile.ProjectileTargeting;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Explicit pack-extensible control immunity, sharing combat/team/PvP ownership policy. */
public final class CrowdControlEligibility {
    public static final TagKey<EntityType<?>> IMMUNE = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("essence_ascendance", "crowd_control_immune"));
    private CrowdControlEligibility() { }
    public static boolean canControl(ServerPlayer owner, LivingEntity target) {
        return target != null && owner.isAlive() && !owner.isRemoved()
                && target.level() == owner.level() && ProjectileTargeting.canHarm(owner, target)
                && !owner.isAlliedTo(target) && !target.isPassenger() && !target.isVehicle()
                && !target.getType().is(IMMUNE)
                && target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < 1;
    }
}
