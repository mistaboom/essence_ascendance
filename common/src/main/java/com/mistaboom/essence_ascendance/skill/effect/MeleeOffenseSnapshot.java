package com.mistaboom.essence_ascendance.skill.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import java.util.Map;
import java.util.Set;

/** Captured attacker values with target-specific conditions evaluated at contact. Never copies mutable skill state. */
public record MeleeOffenseSnapshot(Object runtimeIdentity, Set<ResourceLocation> effective,
                                   double vitalityMultiplier, Map<ResourceLocation,Modifier> modifiers) {
    public MeleeOffenseSnapshot {
        effective=Set.copyOf(effective);
        modifiers=java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(modifiers));
    }
    @FunctionalInterface public interface Modifier {
        Values at(LivingEntity target, DamageSource source, boolean primaryEligible);
    }
    public record Values(double multiplier, double flat) { }
    public static final Modifier NEUTRAL = (target,source,primary) -> new Values(1,0);
    public float apply(LivingEntity target, DamageSource source, float amount, boolean primaryEligible) {
        double multiplier = vitalityMultiplier, flat = 0;
        for (Modifier modifier : modifiers.values()) {
            Values values = modifier.at(target,source,primaryEligible);
            multiplier *= SkillEffectMath.clamp(values.multiplier,0,100_001);
            flat += SkillEffectMath.clamp(values.flat,0,Float.MAX_VALUE);
        }
        return (float)Math.min(Float.MAX_VALUE,amount*multiplier+flat);
    }
}
