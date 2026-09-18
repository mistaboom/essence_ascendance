package com.mistaboom.essence_ascendance.movement;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.skill.CommittedSkillAccess;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Extensible movement-impact classification. Ordinary combat and administrative damage are never inferred as impacts. */
public final class ImpactDamageService {
    public static final TagKey<DamageType> MOVEMENT_IMPACT = TagKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "movement_impact"));
    private ImpactDamageService() { }
    public static boolean applies(LivingEntity target, DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && (source.is(DamageTypeTags.IS_FALL) || source.is(MOVEMENT_IMPACT))
                && CommittedSkillAccess.isEffective(target, SkillIds.IMPACT_CONTROL);
    }
    public static float incoming(LivingEntity target, DamageSource source, float amount) {
        if (!(target instanceof ServerPlayer player) || !Float.isFinite(amount) || amount <= 0
                || !applies(player, source)) return amount;
        // Native fall calculation already consumed this attribute. Transfer it only to non-fall
        // kinetic collisions, and never multiply a fall's existing protection twice.
        double fallMultiplier = source.is(DamageTypeTags.IS_FALL) ? 1
                : player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
        return MovementImpulseMath.impactDamage(amount,
                SkillEffectRuntime.resolvedSettings(player).mobility().impactControl().damageReduction(), fallMultiplier);
    }
}
