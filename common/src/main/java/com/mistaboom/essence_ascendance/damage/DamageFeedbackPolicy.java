package com.mistaboom.essence_ascendance.damage;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/** Shared presentation policy for periodic damage that is not a new physical impact. */
public final class DamageFeedbackPolicy {
    public static final TagKey<DamageType> QUIET = TagKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("essence_ascendance", "quiet_feedback"));
    private DamageFeedbackPolicy() { }
    public static boolean quiet(DamageSource source) { return source.is(QUIET); }
}
