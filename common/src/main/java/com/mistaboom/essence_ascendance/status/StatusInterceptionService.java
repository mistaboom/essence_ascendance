package com.mistaboom.essence_ascendance.status;

import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.mixin.StatusEffectInstanceAccess;
import com.mistaboom.essence_ascendance.projectile.ProjectileOwnership;
import com.mistaboom.essence_ascendance.projectile.ProjectileRuntime;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Server-thread application transaction. Only one scalar cooldown and one immutable outcome per effective player. */
public final class StatusInterceptionService {
    private static long sequence;
    private static final ThreadLocal<Integer> TRANSFER_DEPTH = ThreadLocal.withInitial(() -> 0);
    private record InstantDispatch(MobEffect effect, LivingEntity target) { }
    private static final ThreadLocal<InstantDispatch> INSTANT_NATIVE = new ThreadLocal<>();
    private record Restoration(ServerPlayer player, MobEffectInstance instance) { }
    private static final ThreadLocal<Restoration> RESTORING = new ThreadLocal<>();
    private record ApplicationSource(LivingEntity target, MobEffectInstance effect, Entity direct, Entity owner) { }
    private static final ThreadLocal<ApplicationSource> APPLICATION_SOURCE = new ThreadLocal<>();
    private StatusInterceptionService() { }

    static final class State implements SkillEffectState {
        long cooldownUntil, lastTick = Long.MIN_VALUE;
        int entityId;
        boolean intercepting;
        String dimension;
        StatusOutcome latest;
        @Override public void clear() { cooldownUntil = 0; latest = null; dimension = null; lastTick = Long.MIN_VALUE; intercepting = false; }
    }
    static State state(SkillEffectRuntime.Context context, ResourceLocation id) {
        State state = context.state(id, State::new);
        String dimension = context.player().level().dimension().location().toString();
        if (state.dimension != null && (!state.dimension.equals(dimension) || state.entityId != context.player().getId()
                || context.now() < state.lastTick)) state.clear();
        state.entityId = context.player().getId(); state.dimension = dimension; state.lastTick = context.now();
        return state;
    }
    /** Shared native harmful-effect purge used by recovery/death-defiance mechanics. */
    public static int purgeHarmful(ServerPlayer player) {
        List<Holder<MobEffect>> harmful = new ArrayList<>();
        for (Holder<MobEffect> effect : new ArrayList<>(player.getActiveEffectsMap().keySet())) {
            if (effect.value().getCategory() == MobEffectCategory.HARMFUL) harmful.add(effect);
        }
        int removed = 0;
        for (Holder<MobEffect> effect : harmful) if (player.removeEffect(effect)) removed++;
        return removed;
    }

    public static Optional<StatusOutcome> lastOutcome(ServerPlayer player) {
        var context = SkillEffectRuntime.context(player);
        ResourceLocation id = selected(context);
        return id == null ? Optional.empty() : Optional.ofNullable(state(context, id).latest);
    }
    public static List<String> debugLines(ServerPlayer player) {
        var context = SkillEffectRuntime.context(player);
        ResourceLocation id = selected(context);
        if (id == null) return List.of("Status choice: none effective; existing effects unchanged.");
        State state = state(context, id);
        List<String> result = new ArrayList<>();
        result.add("Status choice=" + id + "; cooldown=" + Math.max(0, state.cooldownUntil - context.now()) + " ticks");
        if (state.latest == null) result.add("Latest status event: none");
        else {
            var o = state.latest;
            result.add("Status event=" + o.eventId() + " effect=" + o.effect() + " category=" + o.category()
                    + " request=" + o.requested() + " native=" + o.nativeAcceptance());
            result.add("Source=" + o.responsibleSource() + " direct=" + o.directEntity() + " relationship=" + o.relationship()
                    + " attribution=" + o.attribution() + " recursion=" + o.recursionDepth());
            result.add("Prior=" + o.priorDefender() + " native result=" + o.nativeDefender() + " final=" + o.resultingDefender());
            result.add("Prevented=" + o.prevented() + " removed=" + o.removed() + " copy=" + o.copied()
                    + " target=" + o.targetAcceptance() + "/" + o.targetResult() + " reason=" + o.rejectionReason());
            if (o.instant() != null) result.add("Instant native resources=" + o.instant());
        }
        return List.copyOf(result);
    }
    private static ResourceLocation selected(SkillEffectRuntime.Context context) {
        // The committed evaluator owns exclusivity. A corrupt dual-effective set fails to immunity without a transfer.
        return context.isEffective(SkillIds.PURE_STATE) ? SkillIds.PURE_STATE
                : context.isEffective(SkillIds.STATUS_MIRROR) ? SkillIds.STATUS_MIRROR : null;
    }
    public static boolean secondary() {
        return TRANSFER_DEPTH.get() > 0 || EquipmentDamageService.isSecondarySkillDamage()
                || EquipmentDamageService.isReflectionInProgress();
    }
    private static boolean secondary(Entity direct) {
        return secondary() || direct instanceof Projectile projectile && ProjectileRuntime.secondary(projectile);
    }
    private static boolean active(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && player.server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    /** Native addEffect/forceAddEffect keeps its own merge and acceptance semantics. */
    public static boolean apply(LivingEntity target, MobEffectInstance incoming, Entity source, BooleanSupplier nativeApply) {
        Restoration restoration = RESTORING.get();
        if (!(target instanceof ServerPlayer defender) || !active(defender)
                || restoration != null && restoration.player == target && restoration.instance == incoming) return nativeApply.getAsBoolean();
        if (SkillStatusImmunityService.blocks(defender, incoming.getEffect())) return false;
        var context = SkillEffectRuntime.context(defender);
        ResourceLocation selected = selected(context);
        if (selected == null) return nativeApply.getAsBoolean();
        State state = state(context, selected);
        long event = nextEvent();
        var requested = capture(incoming);
        var before = capture(defender.getEffect(incoming.getEffect()));
        ApplicationSource application = APPLICATION_SOURCE.get();
        boolean matches = application != null && application.target == target && application.effect == incoming;
        Entity direct = matches ? application.direct : source;
        Entity owner = matches ? application.owner : null;
        Source responsible = source(defender, direct, owner);
        boolean harmful = incoming.getEffect().value().getCategory() == MobEffectCategory.HARMFUL;
        boolean nonempty = StatusPolicy.liveDuration(incoming.getDuration());
        boolean secondary = secondary(direct);
        boolean pure = selected.equals(SkillIds.PURE_STATE) && harmful && nonempty;
        if (pure) {
            com.mistaboom.essence_ascendance.skill.effect.SkillHudEvents.record(defender, SkillIds.PURE_STATE, "blocked", 1);
            state.latest = outcome(event, context, selected, incoming.getEffect(), requested.values, before.values, direct,
                    responsible, "prevented_before_native_application", before.values, before.values, true, false,
                    List.of(), "not_requested", List.of(), state, secondary, "pure_state_no_cleansing");
            feedback(context, secondary);
            return false;
        }
        boolean mirror = nonempty && !state.intercepting && StatusPolicy.mirrorReady(harmful, selected.equals(SkillIds.STATUS_MIRROR), secondary,
                responsible.eligible(), context.now(), state.cooldownUntil) && !defender.getAbilities().invulnerable;
        // Bounded restoration must never discard an unusually deep custom chain.
        boolean restorable = requested.complete && before.complete
                && requested.values.size() + before.values.size() <= StatusOutcome.MAX_CHAIN;
        MobEffectInstance priorCopy = mirror && restorable ? copyPrior(defender.getEffect(incoming.getEffect())) : null;
        boolean accepted = nativeApply.getAsBoolean();
        var nativeResult = capture(defender.getEffect(incoming.getEffect()));
        boolean changed = !nativeResult.values.equals(before.values) || nativeResult.complete != before.complete;
        String reason = !harmful ? "nonharmful_preserved" : !nonempty ? "empty_duration"
                : secondary ? "secondary_no_mirror" : !responsible.eligible() ? responsible.decision
                : context.now() < state.cooldownUntil ? "cooldown" : !restorable ? "chain_bound_no_interception"
                : !changed ? "native_rejected_or_no_state_change" : "not_effective";
        List<StatusOutcome.Instance> copied = List.of(), targetResult = List.of();
        String transfer = "not_requested";
        boolean removed = false;
        var refreshed = SkillEffectRuntime.context(defender);
        if (mirror && !state.intercepting && context.now() >= state.cooldownUntil && restorable && changed && nativeResult.complete && active(defender)
                && selected.equals(selected(refreshed)) && refreshed.existingState(selected) == state) {
            // Restore the prior exact chain, not a weaker replacement and not unrelated pre-existing effects.
            state.intercepting = true;
            Restoration previousRestoration = RESTORING.get();
            try {
                if (priorCopy != null) {
                    // Restore with native replacement first. A loader veto leaves the received state intact;
                    // it can never accidentally cleanse the old effect by rejecting a remove-then-add sequence.
                    RESTORING.set(new Restoration(defender, priorCopy));
                    defender.forceAddEffect(priorCopy, null);
                    removed = capture(defender.getEffect(incoming.getEffect())).values.equals(before.values);
                } else removed = defender.removeEffect(incoming.getEffect());
            } finally {
                if (previousRestoration == null) RESTORING.remove(); else RESTORING.set(previousRestoration);
                state.intercepting = false;
            }
            if (removed && capture(defender.getEffect(incoming.getEffect())).values.equals(before.values)) {
                state.cooldownUntil = StatusPolicy.cooldown(context.now(), context.settings().status());
                copied = requested.values.stream().map(value -> StatusPolicy.copy(value, context.settings().status())).toList();
                Source current = source(defender, direct, owner);
                if (current.eligible() && current.target == responsible.target) {
                    MobEffectInstance copy = reconstruct(incoming.getEffect(), copied);
                    final boolean[] targetAccepted = {false};
                    var targetBefore = capture(current.target.getEffect(incoming.getEffect()));
                    withTransfer(() -> targetAccepted[0] = current.target.addEffect(copy, defender));
                    targetResult = capture(current.target.getEffect(incoming.getEffect())).values;
                    transfer = targetAccepted[0] ? "native_accepted" : targetResult.equals(targetBefore.values)
                            ? "native_rejected_unchanged" : "native_false_hidden_chain_changed";
                } else transfer = "source_invalidated_after_interception";
                reason = "confirmed_interception_cooldown_committed";
                feedback(context, false);
            } else { removed = false; reason = "native_removal_or_restore_rejected"; }
        }
        var after = capture(defender.getEffect(incoming.getEffect()));
        state.latest = outcome(event, context, selected, incoming.getEffect(), requested.values, before.values, direct,
                responsible, accepted ? "native_true" : changed ? "native_false_hidden_chain_changed" : "native_false_unchanged",
                nativeResult.values, after.values, removed, removed, copied, transfer, targetResult, state, secondary, reason);
        // Return the final defender acceptance; a removed interception is not a currently accepted new effect.
        return accepted && !removed;
    }

    /** Narrow native caller annotation: the addEffect API itself often carries only the shooter, losing the direct projectile. */
    public static boolean withSource(LivingEntity target, MobEffectInstance effect, Entity direct, Entity owner,
                                     BooleanSupplier nativeApply) {
        ApplicationSource previous = APPLICATION_SOURCE.get();
        APPLICATION_SOURCE.set(new ApplicationSource(target, effect, direct, owner));
        try { return nativeApply.getAsBoolean(); }
        finally { if (previous == null) APPLICATION_SOURCE.remove(); else APPLICATION_SOURCE.set(previous); }
    }

    /** Generic call-site adapter protects even modded instant overrides used by potions/clouds.
     * Instant effects have no accepted stored instance; interception is explicitly native eligibility preflight.
     * Arbitrary custom direct dispatchers can call this same adapter, rather than requiring a class-name list. */
    public static void instant(MobEffect effect, Entity direct, Entity owner, LivingEntity target,
                               int amplifier, double potency, Runnable nativeApply) {
        InstantDispatch dispatch = INSTANT_NATIVE.get();
        if (dispatch != null && dispatch.effect == effect && dispatch.target == target
                || !(target instanceof ServerPlayer defender) || !active(defender)) {
            nativeApply.run(); return;
        }
        var context = SkillEffectRuntime.context(defender);
        ResourceLocation selected = selected(context);
        if (selected == null || effect.getCategory() != MobEffectCategory.HARMFUL) { runInstant(effect, target, nativeApply); return; }
        Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
        MobEffectInstance probe = new MobEffectInstance(holder, 1, Math.clamp(amplifier, 0, 255));
        State state = state(context, selected);
        Source source = source(defender, direct, owner);
        boolean secondary = secondary(direct);
        boolean eligible = Double.isFinite(potency) && potency > 0 && amplifier >= 0 && defender.canBeAffected(probe);
        boolean pure = selected.equals(SkillIds.PURE_STATE);
        boolean mirror = eligible && !defender.getAbilities().invulnerable && StatusPolicy.mirrorReady(true,
                selected.equals(SkillIds.STATUS_MIRROR), secondary, source.eligible(), context.now(), state.cooldownUntil);
        long event = nextEvent();
        var before = capture(defender.getEffect(holder)).values;
        float healthBefore = defender.getHealth(), absorptionBefore = defender.getAbsorptionAmount();
        float targetHealthBefore = source.target == null ? 0 : source.target.getHealth();
        float targetAbsorptionBefore = source.target == null ? 0 : source.target.getAbsorptionAmount();
        List<StatusOutcome.Instance> copy = List.of();
        String transfer = "not_requested";
        if (pure || mirror) {
            if (mirror) {
                state.cooldownUntil = StatusPolicy.cooldown(context.now(), context.settings().status());
                var value = StatusPolicy.copy(capture(probe).values.getFirst(), context.settings().status());
                copy = List.of(value);
                double boundedPotency = Math.clamp(potency, 0, 1);
                withTransfer(() -> instant(effect, defender, defender, source.target, value.amplifier(), boundedPotency,
                        () -> effect.applyInstantenousEffect(defender, defender, source.target, value.amplifier(), boundedPotency)));
                transfer = "native_instant_dispatched_no_boolean_acceptance";
                if (source.target instanceof ServerPlayer other && lastOutcome(other)
                        .filter(outcome -> outcome.eventId() > event && outcome.prevented()).isPresent())
                    transfer = "target_status_prevention";
            }
            feedback(context, secondary);
        } else runInstant(effect, target, nativeApply);
        state.latest = outcome(event, context, selected, holder,
                List.of(new StatusOutcome.Instance(1, amplifier, false, true, true)), before, direct, source,
                pure || mirror ? "eligible_instant_preflight_intercepted" : "instant_native_dispatched_no_boolean_acceptance",
                before, capture(defender.getEffect(holder)).values, pure || mirror, false, copy, transfer,
                source.target == null ? List.of() : capture(source.target.getEffect(holder)).values, state, secondary,
                pure ? "pure_state_instant_prevention" : mirror ? "instant_preflight_cooldown_committed"
                        : !eligible ? "invalid_instant_preflight" : secondary ? "secondary_no_mirror" : source.decision)
                .withInstant(new StatusOutcome.Instant(Double.toString(potency), mirror ? Math.clamp(potency, 0, 1) : 0,
                        healthBefore, defender.getHealth(), absorptionBefore, defender.getAbsorptionAmount(),
                        targetHealthBefore, source.target == null ? 0 : source.target.getHealth(), targetAbsorptionBefore,
                        source.target == null ? 0 : source.target.getAbsorptionAmount()));
    }
    private static void runInstant(MobEffect effect, LivingEntity target, Runnable action) {
        InstantDispatch prior = INSTANT_NATIVE.get(); INSTANT_NATIVE.set(new InstantDispatch(effect, target));
        try { action.run(); } finally { if (prior == null) INSTANT_NATIVE.remove(); else INSTANT_NATIVE.set(prior); }
    }
    private static void feedback(SkillEffectRuntime.Context context, boolean secondary) {
        int particles = context.settings().projectiles().payloadParticleCount();
        if (!secondary && particles > 0) context.player().serverLevel().sendParticles(
                net.minecraft.core.particles.ParticleTypes.ENCHANT, context.player().getX(),
                context.player().getY(.55), context.player().getZ(), particles, .2, .2, .2, 0);
    }
    private static void withTransfer(Runnable action) {
        int prior = TRANSFER_DEPTH.get(); TRANSFER_DEPTH.set(Math.min(StatusOutcome.MAX_CHAIN, prior + 1));
        try { EquipmentDamageService.withSecondarySkillDamage(action); }
        finally { if (prior == 0) TRANSFER_DEPTH.remove(); else TRANSFER_DEPTH.set(prior); }
    }
    private static long nextEvent() { if (++sequence <= 0) sequence = 1; return sequence; }
    private record Capture(List<StatusOutcome.Instance> values, boolean complete) { }
    private static Capture capture(MobEffectInstance effect) {
        List<StatusOutcome.Instance> values = new ArrayList<>();
        Holder<MobEffect> root = effect == null ? null : effect.getEffect();
        boolean compatible = true;
        while (effect != null && values.size() < StatusOutcome.MAX_CHAIN) {
            compatible &= effect.getEffect().equals(root);
            values.add(new StatusOutcome.Instance(effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon()));
            effect = ((StatusEffectInstanceAccess)(Object)effect).essenceAscendance$hiddenEffect();
        }
        return new Capture(List.copyOf(values), effect == null && compatible);
    }
    private static MobEffectInstance reconstruct(Holder<MobEffect> effect, List<StatusOutcome.Instance> chain) {
        MobEffectInstance result = null;
        for (int i = chain.size() - 1; i >= 0; i--) {
            var v = chain.get(i);
            result = new MobEffectInstance(effect, v.duration(), v.amplifier(), v.ambient(), v.particles(), v.icon(), result);
        }
        return result;
    }
    /** Preserve loader-native fields (notably NeoForge cure sets) rather than reconstructing pre-existing effects from diagnostics. */
    private static MobEffectInstance copyPrior(MobEffectInstance effect) {
        List<MobEffectInstance> chain = new ArrayList<>();
        while (effect != null && chain.size() < StatusOutcome.MAX_CHAIN) {
            chain.add(effect); effect = ((StatusEffectInstanceAccess)(Object)effect).essenceAscendance$hiddenEffect();
        }
        if (effect != null) throw new IllegalArgumentException("Prior effect chain exceeds validated restoration bound");
        MobEffectInstance result = null;
        for (int i = chain.size() - 1; i >= 0; i--) {
            MobEffectInstance copy = new MobEffectInstance(chain.get(i));
            copy.copyBlendState(chain.get(i));
            ((StatusEffectInstanceAccess)(Object)copy).essenceAscendance$hiddenEffect(result);
            result = copy;
        }
        return result;
    }
    private record Source(LivingEntity target, String decision) { boolean eligible() { return target != null && decision.equals("hostile_native_source"); } }
    private static Source source(ServerPlayer defender, Entity direct, Entity explicitOwner) {
        Entity responsible = explicitOwner != null ? explicitOwner
                : direct instanceof Projectile projectile ? projectile.getOwner()
                : direct instanceof AreaEffectCloud cloud ? cloud.getOwner() : direct;
        if (!(responsible instanceof LivingEntity living)) return new Source(null, "no_responsible_living_source");
        if (!living.isAlive() || living.isRemoved() || living.isSpectator() || living.level() != defender.level()
                || direct != null && (direct.isRemoved() || direct.level() != defender.level())) return new Source(living, "invalid_source_lifecycle");
        if (direct instanceof Projectile projectile && (projectile.getOwner() != living
                || ProjectileRuntime.state(projectile) != null && !ProjectileRuntime.validOwnership(projectile, ProjectileRuntime.state(projectile))))
            return new Source(living, "projectile_owner_mismatch");
        var relationship = ProjectileOwnership.damageSourceRelationship(defender, living);
        if (relationship != ProjectileOwnership.Decision.HOSTILE)
            return new Source(living, relationship.name().toLowerCase(java.util.Locale.ROOT));
        if (living.isInvulnerable() || living instanceof Player player && player.getAbilities().invulnerable)
            return new Source(living, "invulnerable_source");
        return new Source(living, "hostile_native_source");
    }
    private static StatusOutcome outcome(long event, SkillEffectRuntime.Context context, ResourceLocation selected,
            Holder<MobEffect> effect, List<StatusOutcome.Instance> requested, List<StatusOutcome.Instance> before,
            Entity direct, Source source, String acceptance, List<StatusOutcome.Instance> nativeResult,
            List<StatusOutcome.Instance> after, boolean prevented, boolean removed, List<StatusOutcome.Instance> copy,
            String transfer, List<StatusOutcome.Instance> targetResult, State state, boolean secondary, String reason) {
        return new StatusOutcome(event, context.now(), context.player().getUUID(), context.player().getId(), state.dimension,
                selected.toString(), effect.unwrapKey().map(key -> key.location().toString()).orElse("unregistered_effect"),
                effect.value().getCategory().name(), requested, before, direct == null ? null : direct.getUUID(),
                source.target == null ? null : source.target.getUUID(), source.decision, acceptance, nativeResult, after,
                prevented, removed, copy, transfer, targetResult, state.cooldownUntil,
                Math.max(secondary ? 1 : 0, TRANSFER_DEPTH.get()), copy.isEmpty() ? null : context.player().getUUID(), reason, null);
    }
}
