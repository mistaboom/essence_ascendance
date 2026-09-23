package com.mistaboom.essence_ascendance.vitality;

import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.network.ProgressionVisualFeedback;
import com.mistaboom.essence_ascendance.skill.SkillGroups;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.AbilityCooldownService;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectState;
import com.mistaboom.essence_ascendance.skill.effect.TimedAbilityState;
import com.mistaboom.essence_ascendance.status.StatusInterceptionService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

/** Shared lethal interception, persistent group cooldown and temporary post-save state for Vitality death defiance. */
public final class VitalityDeathDefianceService {
    private VitalityDeathDefianceService() { }

    private static final class SecondWindState implements SkillEffectState {
        final TimedAbilityState window = new TimedAbilityState();
        double healingFractionPerTick, damageBonus, clearedDebt;
        int clearedEffects;
        @Override public void clear() {
            window.clear(); healingFractionPerTick = damageBonus = clearedDebt = 0; clearedEffects = 0;
        }
    }

    private static final class SpiritWalkState implements SkillEffectState {
        final TimedAbilityState window = new TimedAbilityState();
        double reformHealthFraction;
        @Override public void clear() { window.clear(); reformHealthFraction = 0; }
    }

    public record Snapshot(boolean active, long activeUntil, long cooldownUntil,
                           double recoveryHealthPerSecond, double damageBonus,
                           double reformHealthFraction, double clearedDebt, int clearedEffects) { }

    /** Called while the player is still alive, before Player.actuallyHurt commits a lethal health write. */
    public static float interceptHealthWrite(ServerPlayer player, DamageSource source,
                                             float healthBefore, float proposedHealth) {
        if (healthBefore <= 0 || proposedHealth > 0 || player.isRemoved() || player.isSpectator()
                || player.getAbilities().invulnerable || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return proposedHealth;
        }
        var context = SkillEffectRuntime.context(player);
        long now = context.now();
        if (!AbilityCooldownService.ready(player, SkillGroups.VITALITY_DEATH_DEFIANCE, now)) return proposedHealth;

        if (context.isEffective(SkillIds.SECOND_WIND)) {
            var tuning = context.settings().vitality().deathDefiance().secondWind();
            if (!AbilityCooldownService.trigger(player, SkillGroups.VITALITY_DEATH_DEFIANCE, now, tuning.cooldownTicks()))
                return proposedHealth;
            SecondWindState state = context.state(SkillIds.SECOND_WIND, SecondWindState::new);
            state.clear();
            state.window.activate(now, tuning.recoveryTicks());
            state.healingFractionPerTick = tuning.recoveryHealthFraction() / tuning.recoveryTicks();
            state.damageBonus = tuning.strengthDamageBonus();
            state.clearedDebt = VitalityDamageService.clearDelayedDamage(player);
            state.clearedEffects = StatusInterceptionService.purgeHarmful(player);
            ProgressionVisualFeedback.deathDefiance(player, false, false);
            return survivingHealth(player);
        }

        if (context.isEffective(SkillIds.SPIRIT_WALK)) {
            var tuning = context.settings().vitality().deathDefiance().spiritWalk();
            if (!AbilityCooldownService.trigger(player, SkillGroups.VITALITY_DEATH_DEFIANCE, now, tuning.cooldownTicks()))
                return proposedHealth;
            SpiritWalkState state = context.state(SkillIds.SPIRIT_WALK, SpiritWalkState::new);
            state.clear();
            state.window.activate(now, tuning.durationTicks());
            state.reformHealthFraction = tuning.reformHealthFraction();
            player.fallDistance = 0;
            ProgressionVisualFeedback.deathDefiance(player, true, false);
            return survivingHealth(player);
        }
        return proposedHealth;
    }

    /** Progresses triggered windows even if the player changes the selected choice during the cooldown. */
    public static void tick(SkillEffectRuntime.Context context) {
        ServerPlayer player = context.player();
        long now = context.now();

        SecondWindState second = context.existingState(SkillIds.SECOND_WIND);
        if (second != null) {
            if (second.window.active(now)) {
                double healing = player.getMaxHealth() * second.healingFractionPerTick;
                if (Double.isFinite(healing) && healing > 0)
                    EquipmentDamageService.withSecondarySkillDamage(() -> player.heal((float)Math.min(Float.MAX_VALUE, healing)));
            } else if (second.window.finishIfExpired(now)) {
                context.discardState(SkillIds.SECOND_WIND);
            }
        }

        SpiritWalkState spirit = context.existingState(SkillIds.SPIRIT_WALK);
        if (spirit != null) {
            if (spirit.window.active(now)) {
                // Normal collision/movement remains native; only damage interaction is intangible.
                player.fallDistance = 0;
            } else if (spirit.window.finishIfExpired(now)) {
                double target = Math.max(1.0, player.getMaxHealth() * spirit.reformHealthFraction);
                if (player.getHealth() < target)
                    player.setHealth((float)Math.min(player.getMaxHealth(), target));
                player.fallDistance = 0;
                ProgressionVisualFeedback.deathDefiance(player, true, true);
                context.discardState(SkillIds.SPIRIT_WALK);
            }
        }
    }

    /** Spirit Walk ignores ordinary incoming damage, but never overrides native bypass-invulnerability sources. */
    public static boolean blocksIncomingDamage(ServerPlayer player, DamageSource source) {
        if (player.isRemoved() || !player.isAlive() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        var context = SkillEffectRuntime.context(player);
        SpiritWalkState state = context.existingState(SkillIds.SPIRIT_WALK);
        return state != null && state.window.active(context.now());
    }

    /** Runs before the normal outgoing skill pipeline so Spirit Walk also suppresses secondary/reflective player damage. */
    public static float modifyOutgoingDamage(DamageSource source, float amount) {
        if (!Float.isFinite(amount) || amount <= 0 || !(source.getEntity() instanceof ServerPlayer player)
                || !player.isAlive() || player.isRemoved()) return amount;
        var context = SkillEffectRuntime.context(player);
        SpiritWalkState spirit = context.existingState(SkillIds.SPIRIT_WALK);
        if (spirit != null && spirit.window.active(context.now())) return 0;

        SecondWindState second = context.existingState(SkillIds.SECOND_WIND);
        if (second != null && second.window.active(context.now()) && second.damageBonus > 0
                && !EquipmentDamageService.isReflectionInProgress() && !EquipmentDamageService.isSecondarySkillDamage()) {
            return (float)Math.min(Float.MAX_VALUE, amount * (1 + second.damageBonus));
        }
        return amount;
    }

    public static Snapshot snapshot(SkillEffectRuntime.Context context, ResourceLocation skill) {
        long now = context.now();
        long cooldown = AbilityCooldownService.readyAt(context.player(), SkillGroups.VITALITY_DEATH_DEFIANCE, now);
        if (skill.equals(SkillIds.SECOND_WIND)) {
            SecondWindState state = context.existingState(skill);
            boolean active = state != null && state.window.active(now);
            double healing = state == null ? 0 : state.healingFractionPerTick * 20 * context.player().getMaxHealth();
            return new Snapshot(active, state == null ? 0 : state.window.activeUntil(), cooldown,
                    healing, state == null ? 0 : state.damageBonus, 0,
                    state == null ? 0 : state.clearedDebt, state == null ? 0 : state.clearedEffects);
        }
        SpiritWalkState state = context.existingState(skill);
        return new Snapshot(state != null && state.window.active(now), state == null ? 0 : state.window.activeUntil(), cooldown,
                0, 0, state == null ? 0 : state.reformHealthFraction, 0, 0);
    }

    /** Selection changes cannot cancel an already-triggered window; stale inactive presentation state may be dropped. */
    public static void deactivate(SkillEffectRuntime.Context context, ResourceLocation skill) {
        Snapshot snapshot = snapshot(context, skill);
        if (!snapshot.active()) context.discardState(skill);
    }

    private static float survivingHealth(ServerPlayer player) {
        return (float)Math.min(1.0, Math.max(Math.ulp(1.0F), player.getMaxHealth()));
    }
}
