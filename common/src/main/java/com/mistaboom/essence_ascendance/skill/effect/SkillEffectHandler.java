package com.mistaboom.essence_ascendance.skill.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

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
    /** Direct weapon outcome dispatched only after native hurt returns accepted and positive. */
    default void acceptedAttack(SkillEffectRuntime.Context context, AttackResultContext result) { }
    default void primaryHit(SkillEffectRuntime.Context context, LivingEntity target) { }
    default void primaryMiss(SkillEffectRuntime.Context context) { }
    /** Native hurt returned true with measured positive loss; canceled and zero damage never enter. */
    default void acceptedDamage(SkillEffectRuntime.Context context, DamageSource source,
                                double healthLost, double absorptionLost) { }
    /** One owned absorption source crossed from positive to empty during accepted native damage. */
    default void absorptionDepleted(SkillEffectRuntime.Context context, DamageSource source,
                                     ResourceLocation pool) { }
    default void kill(SkillEffectRuntime.Context context, LivingEntity target) { }
    /** Every completed death is observable; default kill rewards remain ordinary player-attributed only. */
    default void deathObserved(SkillEffectRuntime.Context context, SkillDeathContext death) {
        if (death.ordinaryKillBy(context.player())) kill(context, death.victim());
    }
    default void targetRemoved(SkillEffectRuntime.Context context, Entity target) { }
    /** Post-enchantment native durability loss. Return the remaining loss that vanilla should apply. */
    default void durabilityAttempt(SkillEffectRuntime.Context context, ItemStack stack) { }
    default int durabilityLoss(SkillEffectRuntime.Context context, ItemStack stack, int actualDamage) { return actualDamage; }
    /** Completed native food consumption; saturationPoints is the food's intrinsic vanilla saturation contribution. */
    default void foodConsumed(SkillEffectRuntime.Context context, ItemStack source, int nutrition,
                              double saturationPoints) { }
    /** Teleport/correction invalidates movement evidence, not unrelated combat state. */
    default void movementDiscontinuity(SkillEffectRuntime.Context context) { }
    default double damageMultiplier(SkillEffectRuntime.Context context, LivingEntity target,
                                    DamageSource source, AttackCategory primaryCategory) { return 1.0; }
    /** Flat primary damage is added after ordinary multipliers but before native mitigation. */
    default double flatPrimaryDamageBonus(SkillEffectRuntime.Context context, LivingEntity target,
                                          DamageSource source, AttackCategory primaryCategory) { return 0.0; }

    /** Aggregators compose effective handlers; future speed buffs add no runtime special cases. */
    default double bowDrawSpeedMultiplier(SkillEffectRuntime.Context context) { return 1.0; }
    default double casterSpeedMultiplier(SkillEffectRuntime.Context context) { return 1.0; }

    /**
     * Called only while effective. Prefer SkillEffectHudHandler for standard cards.
     * Multi-card providers keep stable IDs and emit inactive entries after rewards
     * end so the shared closing delay can run. Omission immediately removes a card.
     */
    default List<SkillEffectHudEntry> hudEntries(SkillEffectRuntime.Context context) {
        return List.of(SkillHudEvents.card(context, id()));
    }

    /** Called even when an implementation has already become ineffective. */
    default void deactivate(SkillEffectRuntime.Context context) { context.discardState(id()); }
    default List<String> debugLines(SkillEffectRuntime.Context context) { return List.of(); }
}
