package com.mistaboom.essence_ascendance.vitality;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.projectile.ProjectileOwnership;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** The sole common post-armor/post-absorption routing boundary. No loader-specific skill logic. */
public final class VitalityDamageService {
    private static final ResourceLocation TRAUMA = ResourceLocation.fromNamespaceAndPath("essence_ascendance", "vitality_trauma");
    private VitalityDamageService() { }
    public static VitalityDamageLedger ledger(ServerPlayer player) {
        return EssenceSavedData.get(player.server).getPlayerData(player.getUUID()).vitalityDamage();
    }
    /** Reuses the existing persisted fractional-cost channels; no parallel food account or Nexus revision. */
    public static double carry(ServerPlayer player, ResourceLocation channel) {
        return EssenceSavedData.get(player.server).getPlayerData(player.getUUID()).getFractionalResourceCostCarry(channel);
    }
    public static void carry(ServerPlayer player, ResourceLocation channel, double value) {
        if (EssenceSavedData.get(player.server).getPlayerData(player.getUUID()).setFractionalResourceCostCarry(channel, value))
            dirty(player);
    }
    public static void dirty(ServerPlayer player) { EssenceSavedData.get(player.server).setDirty(); }
    /** Cancels only queued Staggered Pain obligations; Trauma and other Vitality state are unrelated. */
    public static double clearDelayedDamage(ServerPlayer player) {
        var delayed = ledger(player).delayed;
        double cleared = delayed.total();
        if (cleared > 0) { delayed.clear(); dirty(player); }
        return cleared;
    }
    public static float route(ServerPlayer player, DamageSource source, float amount) {
        if (!Float.isFinite(amount) || amount <= 0 || !player.isAlive() || player.isRemoved()
                || player.isSpectator() || player.getAbilities().invulnerable || DeferredDamageService.paying(player)
                || DeferredDamageService.deferred(source) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
        var context = SkillEffectRuntime.context(player);
        var tuning = context.settings().vitality().damage();
        var ledger = ledger(player);
        double remaining = amount;
        ResourceLocation routedBy = null;
        if (context.isEffective(SkillIds.HUNGER_WARD)) {
            routedBy = SkillIds.HUNGER_WARD;
            var food = player.getFoodData();
            var debit = DamageRoutingMath.ward(amount, tuning.hungerWard().damageShare(), tuning.hungerWard().healthPerFoodPoint(),
                    food.getFoodLevel(), food.getSaturationLevel(), carry(player, SkillIds.HUNGER_WARD));
            food.setFoodLevel(debit.food()); food.setSaturation((float)debit.saturation());
            carry(player, SkillIds.HUNGER_WARD, debit.prepaidFood()); remaining = debit.remainingDamage();
        } else if (context.isEffective(SkillIds.STAGGERED_PAIN)) {
            routedBy = SkillIds.STAGGERED_PAIN;
            // A mod may create a direct holder; provenance must not turn an otherwise valid hit into an exception.
            var key = source.typeHolder().unwrapKey().map(type -> type.location().toString()).orElse(source.getMsgId());
            var owner = source.getEntity();
            var provenance = new VitalityDamageLedger.Source(key, owner == null ? null : owner.getUUID());
            // A bounded ledger fails open to ordinary immediate damage, never discards debt.
            if (ledger.delayed.enqueue(provenance, amount, tuning.staggeredPain().paymentTicks())) remaining = 0;
        } else if (context.isEffective(SkillIds.DAMAGE_CEILING)) {
            routedBy = SkillIds.DAMAGE_CEILING;
            var ceiling = tuning.damageCeiling();
            // Resolve against the actual equipped maximum before this hit changes it.
            reconcileTrauma(player);
            double healthBefore = player.getHealth();
            var result = DamageRoutingMath.ceiling(amount, player.getMaxHealth(), healthBefore, ledger.traumaFraction,
                    ceiling.damageTakenFraction(), Attributes.MAX_HEALTH.value().sanitizeValue(0));
            remaining = result.healthDamage();
            if (result.traumaAdded() > 0) {
                ledger.traumaFraction += result.traumaAdded();
                // Commit the maximum-health cost AFTER the native health write, never before it.
                // VitalityHealthCommitMixin shares that accepted-write boundary on both loaders.
            }
            if (result.convertedDamage() > 0 && ledger.traumaFraction > 0)
                ledger.traumaQuietTicks = ceiling.combatTimeoutTicks();
        }
        if (routedBy != null) context.state(routedBy, DamageRoutingState::new).record(amount, remaining, context.now());
        if (remaining < amount) {
            // Original hostile hits count as combat even when all health loss becomes food/debt/Trauma.
            RecentHostileCombat.acceptedDamage(player, ProjectileOwnership.damageSource(source, player));
            CombatHudActivity.confirmedDefense(player, source);
            dirty(player);
        }
        return (float)Math.max(0, Math.min(amount, remaining));
    }
    /** Runs independently of selected skills: switching and refunds cannot erase old obligations. */
    public static void tick(SkillEffectRuntime.Context context) {
        ServerPlayer player = context.player();
        var ledger = ledger(player);
        long now = player.server.overworld().getGameTime();
        if (ledger.lastOnlineTick == now) return;
        ledger.lastOnlineTick = now;
        VitalityDeathDefianceService.tick(context);
        if (!ledger.delayed.isEmpty()) {
            ledger.delayed.settleAmount(payment -> DeferredDamageService.pay(player, payment));
            dirty(player);
        }
        if (!player.isAlive() || player.isRemoved()) return;
        if (ledger.traumaFraction > 0) {
            int timeout = context.settings().vitality().damage().damageCeiling().combatTimeoutTicks();
            ledger.traumaQuietTicks = Math.max(ledger.traumaQuietTicks, RecentHostileCombat.remaining(context, timeout));
            if (ledger.traumaQuietTicks > 0) ledger.traumaQuietTicks--;
            if (ledger.traumaQuietTicks == 0) ledger.traumaFraction = 0;
            dirty(player);
        }
        reconcileTrauma(player);
    }
    /** Called after Player.actuallyHurt's native health write, before its outer death check. */
    public static void commitHealthDamage(ServerPlayer player, DamageSource source,
                                          float healthBefore, float maximumBefore) {
        if (!player.isAlive() || player.isRemoved()) return;
        // Capture actual loss before reconciling maximum capacity or applying any reward.
        double lost = DamageRoutingMath.healthLost(healthBefore, player.getHealth());
        if (!DeferredDamageService.paying(player) && !DeferredDamageService.deferred(source)
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && !player.isSpectator() && !player.getAbilities().invulnerable) {
            VitalityDamageEffects.onHealthDamage(SkillEffectRuntime.context(player), lost, maximumBefore);
        }
        if (ledger(player).traumaFraction > 0) {
            double before = player.getMaxHealth();
            reconcileTrauma(player);
            if (player.getMaxHealth() != before)
                com.mistaboom.essence_ascendance.network.VanillaPlayerAttributeSyncService.syncOwnerHealth(player);
        }
        // Publish the completed hit before the next tick can expire a short timed reward.
        // This is the ordinary shared snapshot/codec, not a separate skill packet or client trigger.
        // Delayed-payment settlement has not finished here; its normal end-of-tick snapshot stays authoritative.
        if (!DeferredDamageService.paying(player))
            com.mistaboom.essence_ascendance.network.SkillEffectHudSyncService.syncIfNeeded(player);
    }
    public static void reconcileTrauma(ServerPlayer player) {
        SkillEffectAttributes.apply(player, Attributes.MAX_HEALTH, TRAUMA, -ledger(player).traumaFraction,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
    /** The same live attribute, with only our own total multiplier factored out for presentation. */
    public static double ordinaryMaximumHealth(ServerPlayer player) {
        var attribute = player.getAttribute(Attributes.MAX_HEALTH);
        var modifier = attribute == null ? null : attribute.getModifier(TRAUMA);
        double multiplier = modifier == null ? 1 : 1 + modifier.amount();
        return multiplier > 0 ? player.getMaxHealth() / multiplier : player.getMaxHealth();
    }
    public static void removeModifier(ServerPlayer player) {
        SkillEffectAttributes.apply(player, Attributes.MAX_HEALTH, TRAUMA, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
    public static void onDeath(ServerPlayer player) {
        ledger(player).clear();
        carry(player, SkillIds.HUNGER_WARD, 0);
        carry(player, SkillIds.METABOLIC_CONVERSION, 0);
        removeModifier(player); dirty(player);
    }
}
