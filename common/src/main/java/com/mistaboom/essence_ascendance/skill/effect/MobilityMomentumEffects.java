package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.config.MobilityBalanceSettings;
import com.mistaboom.essence_ascendance.movement.PlayerMotionTracker;
import com.mistaboom.essence_ascendance.network.ProgressionVisualFeedback;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;

import static com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudCards.compact;

/** One normalized movement reservoir; branches change obstacle handling or temporarily retain that reservoir. */
public final class MobilityMomentumEffects {
    private MobilityMomentumEffects() { }
    public static List<SkillEffectHandler> handlers() { return List.of(new Running(), new Vault(), new Rush()); }
    private static MobilityBalanceSettings settings(SkillEffectRuntime.Context context) { return context.settings().mobility(); }
    private static Text text(String key, String... args) { return Text.translated("hud.essence_ascendance.mobility." + key, args); }
    private static final class State implements SkillEffectState {
        final ContinuousMomentumState momentum = new ContinuousMomentumState();
        PlayerMotionTracker.Sample sample;
        @Override public void clear() { momentum.clear(); sample = null; }
    }
    private static State state(SkillEffectRuntime.Context context) { return context.existingState(SkillIds.RUNNING_MOMENTUM); }
    private static double amount(SkillEffectRuntime.Context context) {
        State state = state(context); return state == null ? 0 : state.momentum.amount();
    }
    private static boolean landMode(ServerPlayer player) {
        // Ordinary sprint jumps may carry earned momentum, but only grounded accepted movement builds it.
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && !player.isSleeping()
                && !player.isPassenger() && !player.isFallFlying() && !player.getAbilities().flying
                && !player.isInWater() && !player.isInLava() && !player.onClimbable() && !player.hasEffect(MobEffects.LEVITATION);
    }
    private static boolean sprinting(ServerPlayer player) {
        return landMode(player) && player.isSprinting() && !player.isShiftKeyDown();
    }
    private static boolean retained(SkillEffectRuntime.Context context) {
        if (!context.isEffective(SkillIds.RUSH)) return false;
        TimedStackState timer = context.existingState(SkillIds.RUSH);
        if (timer == null) return false;
        timer.reconcile(context.now(), 1);
        return timer.count() > 0;
    }
    private static boolean vaultReady(SkillEffectRuntime.Context context) {
        State state = state(context);
        return context.isEffective(SkillIds.RUNNING_MOMENTUM) && context.isEffective(SkillIds.MOMENTUM_VAULT)
                && settings(context).momentumVault().stepHeight() > 0
                && state != null && state.momentum.amount() > 0
                && state.momentum.amount() >= settings(context).momentumVault().minimumMomentum()
                && state.sample != null && !state.sample.discontinuity() && !state.sample.forced()
                && state.sample.intentional() && (state.sample.moving() || state.sample.recentMovement())
                && sprinting(context.player()) && context.player().onGround();
    }
    private static void apply(SkillEffectRuntime.Context context) {
        double speed = context.isEffective(SkillIds.RUNNING_MOMENTUM) && sprinting(context.player())
                ? amount(context) * settings(context).runningMomentum().maximumSpeedBonus() : 0;
        SkillEffectAttributes.apply(context.player(), Attributes.MOVEMENT_SPEED, SkillIds.RUNNING_MOMENTUM,
                speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        applyVault(context);
    }
    private static void applyVault(SkillEffectRuntime.Context context) {
        // Raise the effective native step floor, never add a second full step bonus or lower another mod's step height.
        SkillEffectAttributes.minimum(context.player(), Attributes.STEP_HEIGHT, SkillIds.MOMENTUM_VAULT,
                vaultReady(context) ? settings(context).momentumVault().stepHeight() : 0);
    }
    private static void clearMovement(SkillEffectRuntime.Context context) {
        context.discardState(SkillIds.RUNNING_MOMENTUM);
        context.discardState(SkillIds.RUSH);
        PlayerMotionTracker.forget(context.player());
        SkillEffectAttributes.apply(context.player(), Attributes.MOVEMENT_SPEED, SkillIds.RUNNING_MOMENTUM,
                0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        SkillEffectAttributes.minimum(context.player(), Attributes.STEP_HEIGHT, SkillIds.MOMENTUM_VAULT, 0);
    }
    private static final class Running implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.RUNNING_MOMENTUM; }
        @Override public void reconcile(SkillEffectRuntime.Context context) {
            if (!landMode(context.player())) { clearMovement(context); return; }
            PlayerMotionTracker.track(context.player(), context.settings().posture().movement());
            apply(context);
        }
        @Override public void tick(SkillEffectRuntime.Context context) {
            if (!landMode(context.player())) { clearMovement(context); return; }
            PlayerMotionTracker.track(context.player(), context.settings().posture().movement());
            var sample = PlayerMotionTracker.sample(context.player());
            if (sample.discontinuity()) { clearMovement(context); return; }
            State state = context.state(id(), State::new);
            state.sample = sample;
            var tuning = settings(context).runningMomentum();
            var action = ContinuousMomentumState.Action.DRAIN;
            if (sprinting(context.player()) && sample.intentional() && sample.turnDegrees() < tuning.sharpTurnDegrees()) {
                if (sample.forced() || !context.player().onGround()) action = ContinuousMomentumState.Action.HOLD;
                else if (sample.moving()) action = ContinuousMomentumState.Action.BUILD;
                else if (sample.recentMovement()) action = ContinuousMomentumState.Action.HOLD;
            }
            state.momentum.advance(context.now(), action, tuning.buildTicks(), tuning.drainTicks(), retained(context));
            apply(context);
        }
        @Override public void deactivate(SkillEffectRuntime.Context context) { clearMovement(context); }
        @Override public void movementDiscontinuity(SkillEffectRuntime.Context context) { clearMovement(context); }
        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            double value = amount(context);
            double speed = sprinting(context.player()) ? value * settings(context).runningMomentum().maximumSpeedBonus() : 0;
            List<Text> details = new ArrayList<>();
            details.add(text("momentum", compact(value * 100)));
            if (vaultReady(context)) details.add(text("vault_ready", compact(settings(context).momentumVault().stepHeight())));
            return SkillEffectHudCards.progress(id(), value > 0, AscendancePalette.MOBILITY,
                    text("sprint_speed", compact(speed * 100)), details, value);
        }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            State state = state(context); var tuning = settings(context).runningMomentum();
            return List.of("Momentum=" + amount(context) + "; sprint speed bonus=" + tuning.maximumSpeedBonus()
                            + "; build/drain ticks=" + tuning.buildTicks() + "/" + tuning.drainTicks(),
                    "Sharp-turn threshold=" + tuning.sharpTurnDegrees() + "; retained=" + retained(context)
                            + "; evidence=" + (state == null || state.sample == null ? "awaiting_native_movement" : state.sample.reason()),
                    "One transition per server tick; native grounded movement plus fresh input builds. Jumps/forced recovery hold, never build. Teleports/mode changes reset.");
        }
    }
    private static final class Vault implements SkillEffectHudHandler {
        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            return SkillEffectHudEntry.skill(id(), vaultReady(context), 0,
                    Text.translated("hud.essence_ascendance.event.vault", compact(settings(context).momentumVault().stepHeight())),
                    List.of(text("momentum", compact(amount(context) * 100))), SkillEffectHudEntry.Meter.none());
        }
        @Override public ResourceLocation id() { return SkillIds.MOMENTUM_VAULT; }
        @Override public void reconcile(SkillEffectRuntime.Context context) { applyVault(context); }
        @Override public void deactivate(SkillEffectRuntime.Context context) {
            SkillEffectAttributes.minimum(context.player(), Attributes.STEP_HEIGHT, id(), 0);
            context.discardState(id());
        }
        @Override public void movementDiscontinuity(SkillEffectRuntime.Context context) { deactivate(context); }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var tuning = settings(context).momentumVault();
            return List.of("Vault ready=" + vaultReady(context) + "; momentum threshold=" + tuning.minimumMomentum()
                            + "; native step floor=" + tuning.stepHeight(),
                    "Normal collision and headroom checks; crouch disables. Exact-ID modifier preserves other step modifiers. No teleport, impulse or fall-distance reset.");
        }
    }
    private static final class Rush implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.RUSH; }
        @Override public void deathObserved(SkillEffectRuntime.Context context, SkillDeathContext death) {
            var player = context.player(); var victim = death.victim();
            // Confirmed native attribution includes bow, caster and skill-proc kills, not fabricated kill callbacks.
            if (!context.isEffective(SkillIds.RUNNING_MOMENTUM) || !landMode(player)
                    || death.attributedPlayer() != player || victim == player || victim.level() != player.level()
                    || player.isAlliedTo(victim) || victim.isAlliedTo(player)) return;
            context.state(SkillIds.RUNNING_MOMENTUM, State::new).momentum.fill();
            context.state(id(), TimedStackState::shared).grant(context.now(), 1, settings(context).rush().durationTicks(), true);
            apply(context);
            ProgressionVisualFeedback.momentumSurge(player);
        }
        @Override public void reconcile(SkillEffectRuntime.Context context) {
            if (!context.isEffective(SkillIds.RUNNING_MOMENTUM) || !landMode(context.player())) {
                context.discardState(id()); return;
            }
            TimedStackState timer = context.existingState(id());
            if (timer != null) timer.reconcile(context.now(), 1);
        }
        @Override public void tick(SkillEffectRuntime.Context context) { reconcile(context); }
        @Override public void movementDiscontinuity(SkillEffectRuntime.Context context) { context.discardState(id()); }
        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            TimedStackState timer = context.existingState(id());
            return SkillEffectHudCards.timed(id(), retained(context), AscendancePalette.MOBILITY,
                    text("rush_held"), List.of(text("rush_detail")), "hud.essence_ascendance.mobility.rush_time",
                    timer == null ? 0 : timer.nextExpiry());
        }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            TimedStackState timer = context.existingState(id());
            return List.of("Momentum retention active=" + retained(context) + "; generated duration ticks=" + settings(context).rush().durationTicks()
                            + "; remaining=" + (timer == null ? 0 : Math.max(0, timer.nextExpiry() - context.now())),
                    "Attributed nonallied kills fill the shared reservoir and refresh one window. Retention does not add speed or bypass movement-mode/teleport resets.");
        }
    }
}
