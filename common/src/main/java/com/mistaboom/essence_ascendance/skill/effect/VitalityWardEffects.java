package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import com.mistaboom.essence_ascendance.config.VitalityWardBalanceSettings;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;

import java.util.List;

import static com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudCards.compact;

/** One source-owned reservoir; mutually exclusive branches only change its policy or consume its break event. */
public final class VitalityWardEffects {
    private VitalityWardEffects() { }
    public static List<SkillEffectHandler> handlers() { return List.of(new Soul(), new Deep(), new Shattering()); }
    private static VitalityWardBalanceSettings settings(SkillEffectRuntime.Context context) { return context.settings().vitality().wards(); }
    private static Text text(String key, String... args) { return Text.translated("hud.essence_ascendance.vitality." + key, args); }
    /** Also used by generated build scenarios: actual current maximum HP, not missing/current health. */
    public static double capacity(double maximumHealth, VitalityWardBalanceSettings settings, boolean deep) {
        if (!Double.isFinite(maximumHealth) || maximumHealth <= 0) return 0;
        return maximumHealth * settings.soulWard().capacityHealthFraction()
                * (deep ? 1 + settings.deepWard().capacityBonusFraction() : 1);
    }
    private static boolean deep(SkillEffectRuntime.Context context) { return context.isEffective(SkillIds.DEEP_WARD); }
    private static double capacity(SkillEffectRuntime.Context context) { return capacity(context.player().getMaxHealth(), settings(context), deep(context)); }
    private static double amount(SkillEffectRuntime.Context context) { return AbsorptionPoolService.amount(context.player(), SkillIds.SOUL_WARD); }
    private static final class WardState implements SkillEffectState {
        final TimedStackState duration = TimedStackState.shared(), quiet = TimedStackState.shared();
        void refresh(SkillEffectRuntime.Context context) {
            duration.grant(context.now(), 1, settings(context).soulWard().durationTicks(), true);
            quiet.grant(context.now(), 1, settings(context).deepWard().combatTimeoutTicks(), true);
        }
        void reconcile(long now) { duration.reconcile(now, 1); quiet.reconcile(now, 1); }
        @Override public void clear() { duration.clear(); quiet.clear(); }
    }
    private static int quietRemaining(SkillEffectRuntime.Context context, WardState state) {
        return Math.max(RecentHostileCombat.remaining(context, settings(context).deepWard().combatTimeoutTicks()),
                (int) Math.max(0, state.quiet.nextExpiry() - context.now()));
    }
    private static final class Soul implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.SOUL_WARD; }
        @Override public void deathObserved(SkillEffectRuntime.Context context, SkillDeathContext death) {
            var player = context.player();
            var victim = death.victim();
            // Confirmed native death attribution includes bow/caster and attributed skill-proc kills.
            // Do not require the now-dead victim to pass a live-target acquisition predicate.
            if (death.attributedPlayer() != player || victim == player || victim.level() != player.level()
                    || !player.isAlive() || player.isRemoved() || player.isSpectator() || player.isAlliedTo(victim)) return;
            double gain = victim.getMaxHealth() * settings(context).soulWard().victimHealthFraction();
            if (!Double.isFinite(gain) || gain <= 0 || capacity(context) <= 0) return;
            WardState state = context.state(id(), WardState::new);
            reconcile(context);
            AbsorptionPoolService.grant(player, id(), gain);
            // Refresh even at capacity: a real kill can retain the reservoir without adding points.
            state.refresh(context);
        }
        @Override public void reconcile(SkillEffectRuntime.Context context) {
            WardState state = context.existingState(id());
            if (state == null) return;
            state.reconcile(context.now());
            AbsorptionPoolService.configure(context.player(), id(), capacity(context));
            if (!deep(context) && state.duration.count() == 0)
                AbsorptionPoolService.withdraw(context.player(), id(), amount(context));
        }
        @Override public void tick(SkillEffectRuntime.Context context) {
            reconcile(context);
            WardState state = context.existingState(id());
            if (state != null && deep(context) && quietRemaining(context, state) == 0)
                AbsorptionPoolService.withdraw(context.player(), id(), capacity(context) / settings(context).deepWard().decayTicks());
        }
        @Override public void deactivate(SkillEffectRuntime.Context context) {
            AbsorptionPoolService.remove(context.player(), id());
            context.discardState(id());
        }
        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            WardState state = context.existingState(id());
            double value = amount(context), cap = capacity(context);
            boolean enhanced = deep(context);
            int quiet = state == null ? 0 : quietRemaining(context, state);
            var meter = SkillEffectHudEntry.Meter.timer("hud.essence_ascendance.vitality.ward_time",
                    state == null ? 0 : state.duration.nextExpiry());
            Text detail = text("ward_temporary");
            if (enhanced) {
                if (quiet > 0) {
                    detail = text("ward_retained");
                    meter = SkillEffectHudEntry.Meter.timer("hud.essence_ascendance.vitality.ward_quiet", context.now() + quiet);
                } else {
                    detail = text("ward_decaying");
                    long remaining = cap > 0 ? (long) Math.ceil(value / cap * settings(context).deepWard().decayTicks()) : 0;
                    meter = SkillEffectHudEntry.Meter.timer("hud.essence_ascendance.vitality.ward_time", context.now() + remaining);
                }
            }
            return SkillEffectHudEntry.skill(id(), value > 0, AscendancePalette.VITALITY,
                    text("ward_hp", compact(value), compact(cap)), List.of(detail), meter);
        }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            return List.of("Owned ward HP=" + amount(context) + "/" + capacity(context)
                            + "; native total absorption=" + context.player().getAbsorptionAmount(),
                    "Victim max-HP fraction=" + settings(context).soulWard().victimHealthFraction()
                            + "; Deep Ward effective=" + deep(context),
                    "Kills refresh at cap; expiry/cap trims/refunds are not damage breaks. External potion absorption is not owned.");
        }
    }
    private static final class Deep implements SkillEffectHandler {
        @Override public ResourceLocation id() { return SkillIds.DEEP_WARD; }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            WardState state = context.existingState(SkillIds.SOUL_WARD);
            var s = settings(context).deepWard();
            return List.of("Extra Soul Ward capacity=" + s.capacityBonusFraction() + "; full-cap decay ticks=" + s.decayTicks(),
                    "Quiet ticks remaining=" + (state == null ? 0 : quietRemaining(context, state))
                            + "; accepted hostile damage dealt/taken or an attributed kill refreshes retention.");
        }
    }
    private static final class Shattering implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.SHATTERING_WARD; }
        private TimedStackState state(SkillEffectRuntime.Context context) { return context.existingState(id()); }
        @Override public void absorptionDepleted(SkillEffectRuntime.Context context, DamageSource source, ResourceLocation pool) {
            if (!pool.equals(SkillIds.SOUL_WARD) || !context.isEffective(SkillIds.SOUL_WARD) || deep(context)) return;
            var tuning = settings(context).shatteringWard();
            context.state(id(), TimedStackState::shared).grant(context.now(), 1, tuning.regenerationTicks(), true);
            CrowdControlPulseService.knockback(context.player(), tuning.radius(), tuning.maximumTargets(), tuning.knockback());
        }
        @Override public void reconcile(SkillEffectRuntime.Context context) {
            TimedStackState state = state(context);
            if (state != null) state.reconcile(context.now(), 1);
        }
        @Override public void tick(SkillEffectRuntime.Context context) {
            reconcile(context);
            TimedStackState state = state(context);
            if (state != null && state.count() > 0 && context.isEffective(SkillIds.SOUL_WARD) && !deep(context)) {
                double healing = context.player().getMaxHealth() * settings(context).shatteringWard().healingFractionPerSecond() / 20;
                if (Double.isFinite(healing) && healing > 0) context.player().heal((float) healing);
            }
        }
        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            TimedStackState state = state(context);
            var s = settings(context).shatteringWard();
            return SkillEffectHudCards.timed(id(), state != null && state.count() > 0, AscendancePalette.VITALITY,
                    text("ward_regenerating"), List.of(text("ward_healing", compact(context.player().getMaxHealth() * s.healingFractionPerSecond()))),
                    "hud.essence_ascendance.vitality.ward_regeneration_time", state == null ? 0 : state.nextExpiry());
        }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            TimedStackState state = state(context);
            var s = settings(context).shatteringWard();
            return List.of("Regeneration active=" + (state != null && state.count() > 0)
                            + "; max-HP healing fraction/sec=" + s.healingFractionPerSecond(),
                    "Pulse radius=" + s.radius() + "; eligible-target cap=" + s.maximumTargets() + "; native knockback=" + s.knockback(),
                    "Only accepted damage emptying Soul Ward triggers; unrelated hearts, decay and cleanup never trigger. Refreshes, never stacks.");
        }
    }
}
