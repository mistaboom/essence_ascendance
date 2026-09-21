package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.vitality.RecoveryChain;
import com.mistaboom.essence_ascendance.vitality.RecoveryMath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import com.mistaboom.essence_ascendance.vitality.NaturalRecoveryClockAccess;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;

/** Committed Vitality recovery effects using the shared attack, lifecycle and HUD runtime. */
public final class VitalityRecoveryEffects {
    private VitalityRecoveryEffects() { }

    public static List<SkillEffectHandler> handlers() { return List.of(new RisingRecovery(), new LifeSteal()); }

    /**
     * Shared missing-health multiplier for both native food regeneration and
     * the existing Nexus passive health-regeneration bonus.
     */
    public static double regenerationSpeed(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isRemoved()) return 1.0;
        var context = SkillEffectRuntime.context(player);
        if (!context.isEffective(SkillIds.RISING_RECOVERY)) return 1.0;
        var tuning = context.settings().vitality().risingRecovery();
        return RecoveryMath.speed(player.getHealth(), player.getMaxHealth(),
                tuning.maxSpeedBonus(), tuning.recoveryCurveExponent());
    }

    /** Invoked only at the two native regeneration timer increments, after native eligibility. */
    public static int advanceNaturalTimer(Player player, FoodData food, int nativeValue) {
        if (!(player instanceof ServerPlayer server) || !server.isAlive()) return nativeValue;
        int beforeReconciliation = food instanceof NaturalRecoveryClockAccess access ? access.essenceAscendance$naturalTimer() : 0;
        var context = SkillEffectRuntime.context(server);
        // Reconciliation may have removed a deselected clock's injected surplus after native loaded its old value.
        if (food instanceof NaturalRecoveryClockAccess access)
            nativeValue += access.essenceAscendance$naturalTimer() - beforeReconciliation;
        if (!context.isEffective(SkillIds.RISING_RECOVERY)) return nativeValue;
        NaturalClock state = context.state(SkillIds.RISING_RECOVERY, NaturalClock::new);
        var tuning = context.settings().vitality().risingRecovery();
        ResourceLocation dimension = player.level().dimension().location();
        if (!dimension.equals(state.dimension) || context.now() < state.tick) {
            nativeValue = Math.max(1, nativeValue - state.contributedTicks);
            state.clear();
        }
        state.dimension = dimension;
        state.food = new WeakReference<>(food);
        if (state.tick == context.now()) return nativeValue;
        state.tick = context.now();
        double speed = RecoveryMath.speed(player.getHealth(), player.getMaxHealth(),
                tuning.maxSpeedBonus(), tuning.recoveryCurveExponent());
        state.lastEligibleTick = context.now();
        state.lastSpeed = speed;
        state.fraction += speed - 1;
        int extra = (int) Math.min(Integer.MAX_VALUE - (long) nativeValue, Math.floor(state.fraction));
        state.fraction -= extra;
        state.contributedTicks += extra;
        if (food instanceof NaturalRecoveryClockAccess access) access.essenceAscendance$naturalSurplus(state.contributedTicks);
        return nativeValue + extra;
    }

    /** Vanilla shares this clock with starvation; no regeneration acceleration may carry into that branch. */
    public static int leaveNaturalTimer(Player player, FoodData food, int nativeValue) {
        if (!(player instanceof ServerPlayer server)) return nativeValue;
        int beforeReconciliation = food instanceof NaturalRecoveryClockAccess access ? access.essenceAscendance$naturalTimer() : 0;
        var context = SkillEffectRuntime.context(server);
        if (nativeValue != 0 && food instanceof NaturalRecoveryClockAccess access)
            nativeValue += access.essenceAscendance$naturalTimer() - beforeReconciliation;
        NaturalClock state = context.existingState(SkillIds.RISING_RECOVERY);
        if (state == null || state.food.get() != food) return nativeValue;
        int resolved = nativeValue == 0 ? 0 : Math.max(0, nativeValue - state.contributedTicks);
        state.contributedTicks = 0;
        if (food instanceof NaturalRecoveryClockAccess access) access.essenceAscendance$naturalSurplus(0);
        if (nativeValue != 0) state.fraction = 0;
        return resolved;
    }

    public static int chainHits(ServerPlayer player) {
        var context = SkillEffectRuntime.context(player);
        LifeChain state = context.existingState(SkillIds.LIFE_STEAL);
        return context.isEffective(SkillIds.LIFE_STEAL) && state != null ? state.chain.hits() : 0;
    }

    private static final class NaturalClock implements SkillEffectState {
        double fraction;
        long tick = Long.MIN_VALUE;
        long lastEligibleTick = Long.MIN_VALUE;
        double lastSpeed = 1;
        ResourceLocation dimension;
        int contributedTicks;
        WeakReference<FoodData> food = new WeakReference<>(null);
        public void clear() {
            fraction = 0;
            contributedTicks = 0;
            tick = Long.MIN_VALUE;
            lastEligibleTick = Long.MIN_VALUE;
            lastSpeed = 1;
            dimension = null;
            food.clear();
        }
    }

    private static final class RisingRecovery implements SkillEffectHudHandler {
        public ResourceLocation id() { return SkillIds.RISING_RECOVERY; }
        public void deactivate(SkillEffectRuntime.Context context) {
            NaturalClock state = context.existingState(id());
            if (state != null && state.food.get() instanceof NaturalRecoveryClockAccess access) {
                access.essenceAscendance$naturalTimer(Math.max(0, access.essenceAscendance$naturalTimer() - state.contributedTicks));
                access.essenceAscendance$naturalSurplus(0);
            }
            context.discardState(id());
        }
        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            NaturalClock state = context.existingState(id());
            long last = state == null ? Long.MIN_VALUE : state.lastEligibleTick;
            boolean passive = com.mistaboom.essence_ascendance.equipment.EquipmentVitalityService
                    .passiveRegenerationAvailable(context.player());
            boolean active = passive || (last != Long.MIN_VALUE && context.now() - last <= 40);
            double speed = state != null && last != Long.MIN_VALUE
                    ? state.lastSpeed : regenerationSpeed(context.player());
            // Keep the card compact like the other skill cards. A natural
            // regeneration timer is an internal native cadence, so showing
            // its seconds countdown only flickers between tiny values and is
            // not useful feedback to a player.
            return SkillEffectHudEntry.skill(id(), active,
                    com.mistaboom.essence_ascendance.visual.AscendancePalette.VITALITY,
                    SkillEffectHudEntry.Text.translated("hud.essence_ascendance.recovery.active"),
                    List.of(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.recovery.speed", decimal(speed))),
                    SkillEffectHudEntry.Meter.none());
        }
        public List<String> debugLines(SkillEffectRuntime.Context context) {
            var tuning = context.settings().vitality().risingRecovery();
            return List.of("Natural regeneration speed=" + decimal(RecoveryMath.speed(context.player().getHealth(),
                    context.player().getMaxHealth(), tuning.maxSpeedBonus(), tuning.recoveryCurveExponent()))
                    + "x; native hunger, saturation, gamerule and healing cost preserved.");
        }
    }

    private static final class LifeChain implements SkillEffectState {
        final RecoveryChain chain = new RecoveryChain();
        WeakReference<LivingEntity> target = new WeakReference<>(null);
        ResourceLocation dimension;
        public void clear() { chain.clear(); target.clear(); dimension = null; }
    }

    private static final class LifeSteal implements SkillEffectHudHandler {
        public ResourceLocation id() { return SkillIds.LIFE_STEAL; }
        public void reconcile(SkillEffectRuntime.Context context) {
            LifeChain state = context.existingState(id());
            if (state == null) return;
            LivingEntity target = state.target.get();
            state.chain.expire(context.now(), context.settings().vitality().lifeSteal().chainTimeoutTicks());
            boolean pending = target != null && EquipmentDamageService.pendingAcceptedAttack(context.player(), target);
            if (target == null || (!target.isAlive() || target.isRemoved()) && !pending || target.level() != context.player().level()
                    || !context.player().level().dimension().location().equals(state.dimension)) state.clear();
        }
        public void primaryMiss(SkillEffectRuntime.Context context) { context.discardState(id()); }
        public void targetRemoved(SkillEffectRuntime.Context context, Entity target) {
            LifeChain state = context.existingState(id());
            if (state != null && state.target.get() == target && (!(target instanceof LivingEntity living)
                    || !EquipmentDamageService.pendingAcceptedAttack(context.player(), living))) state.clear();
        }
        public void acceptedAttack(SkillEffectRuntime.Context context, AttackResultContext result) {
            if (!result.primary() || result.attacker() != context.player() || !context.player().isAlive()) return;
            var tuning = context.settings().vitality().lifeSteal();
            LifeChain state = context.state(id(), LifeChain::new);
            state.dimension = context.player().level().dimension().location();
            state.target = new WeakReference<>(result.target());
            int hits = state.chain.hit(result.target().getUUID(), context.now(), tuning.chainTimeoutTicks(), tuning.maxChainHits());
            double sourceMultiplier = 1 + Math.max(0, com.mistaboom.essence_ascendance.equipment.EquipmentVitalityService
                    .evaluateStats(context.player()).healingEffectivenessPercent()) / 100;
            double amount = RecoveryMath.healing(result.damageDealt(), com.mistaboom.essence_ascendance.vitality.HealingRecoveryService.usefulHealing(context.player()) / sourceMultiplier,
                    tuning.baseHealingFraction(), tuning.perHitHealingFraction(), hits, tuning.maxChainHits());
            // Native heal observers own Vitality Attunement exactly once. A healing callback cannot recursively proc attacks.
            if (amount > 0) EquipmentDamageService.withSecondarySkillDamage(() -> context.player().heal((float) amount));
            if (!result.target().isAlive() || result.target().isRemoved()) state.clear();
        }
        public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            LifeChain state = context.existingState(id());
            int hits = state == null ? 0 : state.chain.hits();
            var tuning = context.settings().vitality().lifeSteal();
            double fraction = tuning.baseHealingFraction() + Math.max(0, hits - 1) * tuning.perHitHealingFraction();
            return SkillEffectHudEntry.skill(id(), hits > 0,
                    com.mistaboom.essence_ascendance.visual.AscendancePalette.VITALITY,
                    SkillEffectHudEntry.Text.translated("hud.essence_ascendance.recovery.chain", Integer.toString(hits)),
                    List.of(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.recovery.healing",
                            SkillEffectHudCards.decimal(fraction * 100))),
                    SkillEffectHudEntry.Meter.timer("hud.essence_ascendance.recovery.timeout", state == null ? 0 : state.chain.expiresAt(tuning.chainTimeoutTicks())));
        }
        public List<String> debugLines(SkillEffectRuntime.Context context) {
            LifeChain state = context.existingState(id());
            return List.of("Accepted direct weapon chain=" + (state == null ? 0 : state.chain.hits())
                    + "; target=" + (state == null ? "none" : state.chain.target())
                    + "; timeout=" + context.settings().vitality().lifeSteal().chainTimeoutTicks() + " ticks.");
        }
    }
    private static String decimal(double value) { return String.format(Locale.ROOT, "%.2f", value); }
}
