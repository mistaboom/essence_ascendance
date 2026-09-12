package com.mistaboom.essence_ascendance.skill.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** Gameplay registration lives separately from immutable skill catalog metadata. */
public interface SkillEffectHandler {
    ResourceLocation id();

    /** Once per server game tick; use this for future periodic gameplay behavior. */
    default void tick(SkillEffectRuntime.Context context) { }
    /** Idempotent expiry/modifier reconciliation; may also run before a query or damage event. */
    default void reconcile(SkillEffectRuntime.Context context) { }
    /** All confirmed primary attack paths enter here; legacy stances remain melee-scoped. */
    default void successfulAttack(SkillEffectRuntime.Context context, AttackResultContext result) {
        if (result.primary() && result.category() == AttackCategory.MELEE) {
            primaryHit(context, result.target());
        }
    }
    default void primaryHit(SkillEffectRuntime.Context context, LivingEntity target) { }
    default void primaryMiss(SkillEffectRuntime.Context context) { }
    default void kill(SkillEffectRuntime.Context context, LivingEntity target) { }
    /** Every completed death is observable; default kill rewards remain ordinary player-attributed only. */
    default void deathObserved(SkillEffectRuntime.Context context, SkillDeathContext death) {
        if (death.ordinaryKillBy(context.player())) kill(context, death.victim());
    }
    default void targetRemoved(SkillEffectRuntime.Context context, Entity target) { }
    default double damageMultiplier(SkillEffectRuntime.Context context, LivingEntity target,
                                    DamageSource source, AttackCategory primaryCategory) { return 1.0; }
    /** Flat primary damage is added after ordinary multipliers but before native mitigation. */
    default double flatPrimaryDamageBonus(SkillEffectRuntime.Context context, LivingEntity target,
                                          DamageSource source, AttackCategory primaryCategory) { return 0.0; }

    /** Aggregators compose effective handlers; future speed buffs add no runtime special cases. */
    default double bowDrawSpeedMultiplier(SkillEffectRuntime.Context context) { return 1.0; }
    default double casterSpeedMultiplier(SkillEffectRuntime.Context context) { return 1.0; }

    /** Called only while effective. Return default entries for grace, or omit for immediate hiding. */
    default List<SkillEffectHudEntry> hudEntries(SkillEffectRuntime.Context context) { return List.of(); }

    /** Called even when an implementation has already become ineffective. */
    default void deactivate(SkillEffectRuntime.Context context) { context.discardState(id()); }
    default List<String> debugLines(SkillEffectRuntime.Context context) { return List.of(); }
}
