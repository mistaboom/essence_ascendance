package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.skill.CommittedSkillService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Common, server-thread-only dispatch for committed skill effects. Nothing in
 * this service is serialized. The lifecycle service owns reset/forget calls.
 * Handlers never inspect client state or treat purchase ownership as activation.
 */
public final class SkillEffectRuntime {
    private static final Map<UUID, PlayerRuntime> PLAYERS = new HashMap<>();
    private static long nextAttemptToken;

    private SkillEffectRuntime() { }

    /** Common extension point for registered effects reporting measured, completed native outcomes.
     * Reuse the native action identity to deduplicate an effect and its ordinary adapter. */
    public static void reportOutcome(ServerPlayer player,
                                    com.mistaboom.essence_ascendance.attunement.AttunementEvent outcome) {
        com.mistaboom.essence_ascendance.attunement.AttunementService.submit(player, outcome);
    }

    public static Set<ResourceLocation> implementedIds() { return SkillEffectRegistry.implementedIds(); }
    public static boolean isImplemented(ResourceLocation id) { return SkillEffectRegistry.isImplemented(id); }

    public static void tick(ServerPlayer player) {
        com.mistaboom.essence_ascendance.guard.GuardLifecycle.observe(player);
        if (!player.isAlive() || player.isRemoved()) {
            reset(player);
            return;
        }
        Context context = current(player);
        if (context.runtime.lastGameplayTick != context.now()) {
            context.runtime.lastGameplayTick = context.now();
            // Centralized targeted status immunity keeps future skill rules on the same native
            // application/purge path instead of requiring per-skill effect-removal loops.
            com.mistaboom.essence_ascendance.status.SkillStatusImmunityService.reconcile(context);
            com.mistaboom.essence_ascendance.vitality.VitalityDamageService.tick(context);
            if (!player.isAlive() || player.isRemoved()) return;
            for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) {
                if (context.isEffective(handler.id())) handler.tick(context);
            }
        }
    }

    /** Progression refresh preserves still-effective effects; config changes clear transient combat. */
    public static void refresh(ServerPlayer player) {
        if (player.isAlive() && !player.isRemoved())
            com.mistaboom.essence_ascendance.vitality.VitalityDamageService.reconcileTrauma(player);
        if (player.isAlive() && !player.isRemoved()) current(player);
        else reset(player);
    }

    public static void reset(ServerPlayer player) {
        com.mistaboom.essence_ascendance.vitality.VitalityDamageService.removeModifier(player);
        GuardCounterattackService.reset(player);
        com.mistaboom.essence_ascendance.guard.GuardLifecycle.forget(player);
        GuardMobilityController.remove(player);
        com.mistaboom.essence_ascendance.guard.ReflectionRouter.forget(player);
        PlayerRuntime runtime = PLAYERS.remove(player.getUUID());
        if (runtime == null) {
            // Also remove known stable modifiers when recovering an old player instance.
            runtime = new PlayerRuntime(player, EssenceConfigManager.skillEffects());
        }
        clear(new Context(player, runtime));
    }

    public static void forget(ServerPlayer player) { reset(player); }

    public static void movementDiscontinuity(ServerPlayer player) {
        PlayerRuntime runtime = PLAYERS.get(player.getUUID());
        if (runtime == null || runtime.player.get() != player) return;
        Context context = new Context(player, runtime);
        for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) handler.movementDiscontinuity(context);
    }

    public static void clearAll() {
        CombatHudActivity.clear();
        GuardCounterattackService.clearAll();
        com.mistaboom.essence_ascendance.posture.PostureService.clear();
        for (PlayerRuntime runtime : List.copyOf(PLAYERS.values())) {
            ServerPlayer player = runtime.player.get();
            if (player != null) clear(new Context(player, runtime));
            else runtime.states.values().forEach(SkillEffectState::clear);
        }
        PLAYERS.clear();
        AbsorptionPoolService.clearAll();
        com.mistaboom.essence_ascendance.guard.GuardLifecycle.clear();
        GuardMobilityController.clear();
        com.mistaboom.essence_ascendance.guard.ReflectionRouter.clear();
        nextAttemptToken = 0;
    }

    /** Returns zero for nested/rejected attempts. All scopes must close in a finally block. */
    public static long beginPrimaryAttack(ServerPlayer player, Entity target) {
        if (!player.isAlive() || player.isRemoved()) return 0;
        Context context = current(player);
        if (context.runtime.attempt != null) return 0;
        long token = ++nextAttemptToken;
        if (token == 0) token = ++nextAttemptToken;
        context.runtime.attempt = new Attempt(token, target, context.now(), context.runtime.effective);
        GuardCounterattackService.begin(context, token, target);
        return token;
    }

    /** The adapter supplies only an accepted primary hurt with observed health/absorption loss. */
    public static void onPrimaryMeleeSuccess(ServerPlayer player, LivingEntity target,
                                             DamageSource source, double damageDealt) {
        PlayerRuntime runtime = PLAYERS.get(player.getUUID());
        Attempt attempt = runtime == null ? null : runtime.attempt;
        if (attempt != null && !attempt.success && attempt.matches(target)) {
            attempt.success = true;
            attempt.source = source;
            attempt.damageDealt = damageDealt;
        }
    }

    /** Projectile and Caster adapters call this only after observed native damage. */
    public static void onPrimaryAttackSuccess(ServerPlayer player, LivingEntity target,
                                              AttackCategory category, DamageSource source,
                                              double damageDealt) {
        if (category == AttackCategory.MELEE) return;
        PlayerRuntime runtime = PLAYERS.get(player.getUUID());
        if (runtime == null || runtime.player.get() != player) return;
        // Outgoing-damage classification called current() immediately before
        // this health write. Avoid another reconciliation here because a
        // lethal projectile/Caster hit has set health to zero but its native
        // death callback still needs to consume burning/frozen conditions.
        Context context = new Context(player, runtime);
        dispatchSuccessfulAttack(context,
                AttackResultContext.primary(player, target, category, source, damageDealt));
    }

    /** Completed native hurt outcome for effects that require its final acceptance, such as healing. */
    public static Object attackRuntimeIdentity(ServerPlayer player) { return current(player).runtime.attackIdentity; }

    public static void onAcceptedAttackSuccess(ServerPlayer player, LivingEntity target, AttackCategory category,
                                               DamageSource source, double damageDealt,
                                               Set<ResourceLocation> effectiveAtDamage, Object runtimeAtDamage,
                                               boolean directWeapon) {
        if (!player.isAlive() || player.isRemoved() || !Double.isFinite(damageDealt) || damageDealt <= 0) return;
        Context context = current(player);
        if (context.runtime.attackIdentity != runtimeAtDamage || !context.runtime.effective.equals(effectiveAtDamage)) return;
        RecentHostileCombat.acceptedOutgoingDamage(player, target);
        if (!directWeapon) return;
        AttackResultContext result = AttackResultContext.primary(player, target, category, source, damageDealt);
        for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) {
            if (context.isEffective(handler.id())) handler.acceptedAttack(context, result);
        }
    }

    /** Newly earned stacks are committed after Player.attack, so they cannot affect the earning hit. */
    public static void finishPrimaryAttack(ServerPlayer player, long token) {
        if (token == 0) return;
        try {
            finishPrimaryAttackOutcome(player, token);
        } finally {
            GuardCounterattackService.close(player, token);
        }
    }

    private static void finishPrimaryAttackOutcome(ServerPlayer player, long token) {
        PlayerRuntime runtime = PLAYERS.get(player.getUUID());
        Attempt attempt = runtime == null ? null : runtime.attempt;
        if (attempt == null || attempt.token != token) return;
        Context context = current(player);
        // Refresh can deactivate effects, change settings or replace the player runtime.
        // Never award the captured hit to a different loadout after that transition.
        if (context.runtime != runtime || runtime.attempt != attempt
                || !attempt.effectiveAtStart.equals(context.runtime.effective)) return;
        runtime.attempt = null;
        GuardCounterattackService.finish(context, token, attempt.success, attempt.damageDealt);
        Entity target = attempt.target.get();
        if (attempt.success && attempt.source != null && target instanceof LivingEntity living && player.isAlive()) {
            dispatchSuccessfulAttack(context,
                    AttackResultContext.primary(player, living, AttackCategory.MELEE,
                            attempt.source, attempt.damageDealt));
        } else dispatchMiss(context);
    }

    /** Called only after the server adapter distinguishes an air miss from mining and a paired hit. */
    public static void onAirSwing(ServerPlayer player) {
        Context context = current(player);
        if (context.runtime.attempt == null) dispatchMiss(context);
    }

    /**
     * Hook order: equipment/enchantment outgoing amount -> this multiplier ->
     * equipment incoming resistance and vanilla target mitigation. The native
     * hurt adapter owns exactly-once mutation; this function never calls hurt.
     */
    public static float modifyOutgoingDamage(LivingEntity target, DamageSource source, float amount) {
        if (!Float.isFinite(amount) || amount <= 0.0F) return amount;
        ServerPlayer player = EquipmentDamageService.skillDamagePlayer(target, source);
        if (player == null || !player.isAlive()) return amount;
        Context context = current(player);
        AttackCategory primary = EquipmentDamageService.primaryAttackCategory(player, target, source);
        double multiplier = 1.0;
        double flatBonus = 0.0;
        for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) {
            if (context.isEffective(handler.id())) {
                multiplier *= SkillEffectMath.clamp(
                        handler.damageMultiplier(context, target, source, primary), 0.0, 100_001.0);
                flatBonus += SkillEffectMath.clamp(
                        handler.flatPrimaryDamageBonus(context, target, source, primary), 0.0, Float.MAX_VALUE);
            }
        }
        return GuardCounterattackService.modify(context, target, primary,
                (float) Math.min(Float.MAX_VALUE, amount * multiplier + flatBonus));
    }

    /** Native confirmed death, while its authoritative source/caster scope still exists. */
    public static void onLivingDeath(LivingEntity victim, DamageSource source) {
        SkillProcDamageService.ProcContext proc = SkillProcDamageService.current();
        ServerPlayer attributed = proc == null
                ? EquipmentDamageService.skillDamagePlayer(victim, source) : proc.owner();
        SkillDeathContext death = new SkillDeathContext(victim, source, attributed, proc);
        for (PlayerRuntime runtime : List.copyOf(PLAYERS.values())) {
            ServerPlayer player = runtime.player.get();
            if (player == null || !player.isAlive() || player == victim) continue;
            // Do not run ordinary reconciliation before death handlers: a dead
            // target must retain its still-timed burning/frozen record long
            // enough for Combustion or Shatter to consume it exactly once.
            Context context = new Context(player, runtime);
            for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) {
                if (context.isEffective(handler.id())) handler.deathObserved(context, death);
            }
        }
        onEntityRemoved(victim);
    }

    /** Unload/death cleanup supplements weak references and the owner-tick identity check. */
    public static void onEntityRemoved(Entity entity) {
        for (PlayerRuntime runtime : List.copyOf(PLAYERS.values())) {
            ServerPlayer player = runtime.player.get();
            if (player == null) {
                runtime.states.values().forEach(SkillEffectState::clear);
                PLAYERS.values().remove(runtime);
                continue;
            }
            Context context = new Context(player, runtime);
            for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) handler.targetRemoved(context, entity);
        }
    }

    public static double bowDrawSpeedMultiplier(ServerPlayer player) {
        Context context = current(player);
        double multiplier = 1.0;
        for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) {
            if (context.isEffective(handler.id())) {
                multiplier = SkillEffectMath.clamp(multiplier * SkillEffectMath.clamp(
                        handler.bowDrawSpeedMultiplier(context), 0.0, 100_001.0), 0.0, 100_001.0);
            }
        }
        return multiplier;
    }

    public static double casterSpeedMultiplier(ServerPlayer player) {
        Context context = current(player);
        double multiplier = 1.0;
        for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) {
            if (context.isEffective(handler.id())) {
                multiplier = SkillEffectMath.clamp(multiplier * SkillEffectMath.clamp(
                        handler.casterSpeedMultiplier(context), 0.0, 100_001.0), 0.0, 100_001.0);
            }
        }
        return multiplier;
    }

    /** Read-only presentation state for the local player's server-synchronized combat HUD. */
    public static SkillEffectHudSnapshot hudSnapshot(ServerPlayer player) {
        List<SkillEffectHudEntry> entries = new ArrayList<>();
        if (player.isAlive() && !player.isRemoved()) {
            Context context = current(player);
            for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) {
                if (!context.isEffective(handler.id()) && !handler.hudWhileIneffective(context)) continue;
                for (SkillEffectHudEntry entry : handler.hudEntries(context)) {
                    if (!entry.sourceSkill().equals(handler.id())) {
                        throw new IllegalArgumentException("HUD source does not match handler: " + handler.id());
                    }
                    entries.add(entry);
                }
            }
        }
        Context context = current(player);
        return new SkillEffectHudSnapshot(player.level().getGameTime(), player.getId(),
                player.level().dimension().location(), entries, GuardCounterattackService.bonusReach(context),
                GuardCounterattackService.reachExpiresAt(context));
    }

    public static List<String> debugLines(ServerPlayer player, ResourceLocation skillId) {
        SkillEffectHandler handler = SkillEffectRegistry.get(skillId);
        if (handler == null) return List.of("Gameplay effect: deferred (framework only).");
        Context context = current(player);
        List<String> lines = new ArrayList<>();
        lines.add("Gameplay effect: implemented; committed effective=" + context.isEffective(skillId));
        lines.addAll(handler.debugLines(context));
        lines.add("All listed skill-effect tuning is provisional server balance.");
        return List.copyOf(lines);
    }

    public static List<String> validateInvariants() { return SkillEffectDiagnostics.validate(); }

    /** Launch snapshots and all effect handlers share the same resolved rank-adjusted values. */
    public static SkillEffectBalanceSettings resolvedSettings(ServerPlayer player) { return current(player).settings(); }

    /** Reconciled shared effect context; server gameplay callers use the same effective loadout. */
    public static Context context(ServerPlayer player) { return current(player); }

    /**
     * Shared post-enchantment durability pipeline. Loader adapters call this exactly once before
     * the artifact fracture guard, so maintenance skills compose with Unbreaking and durability efficiency.
     */
    public static int modifyDurabilityLoss(ServerPlayer player, ItemStack stack, int actualDamage) {
        if (player == null || stack == null || stack.isEmpty() || actualDamage < 0) return Math.max(0, actualDamage);
        Context context = current(player);
        int resolved = actualDamage;
        for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) {
            if (!context.isEffective(handler.id())) continue;
            resolved = Math.max(0, handler.durabilityLoss(context, stack, resolved));
        }
        return resolved;
    }

    /** Shared completed-food event. Native hunger, saturation, effects and containers have already resolved. */
    public static void onFoodConsumed(ServerPlayer player, ItemStack source, int nutrition, double saturationPoints) {
        if (player == null || source == null || source.isEmpty() || nutrition < 0
                || !Double.isFinite(saturationPoints) || saturationPoints < 0) return;
        Context context = current(player);
        for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) {
            if (context.isEffective(handler.id())) handler.foodConsumed(context, source, nutrition, saturationPoints);
        }
    }

    /** Completed native incoming damage, shared by combat-state and reactive food consumers. */
    public static void onAcceptedDamage(ServerPlayer player, DamageSource source,
                                        double healthLost, double absorptionLost) {
        if (com.mistaboom.essence_ascendance.vitality.DeferredDamageService.deferred(source)) return;
        if (!player.isAlive() || player.isRemoved() || !Double.isFinite(healthLost)
                || !Double.isFinite(absorptionLost) || healthLost + absorptionLost <= 0) return;
        RecentHostileCombat.acceptedDamage(player,
                com.mistaboom.essence_ascendance.projectile.ProjectileOwnership.damageSource(source, player));
        Context context = current(player);
        for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) {
            if (context.isEffective(handler.id())) handler.acceptedDamage(context, source, healthLost, absorptionLost);
        }
    }

    /** Shared native pool-break outcome. Expiry, cleanup and external effect removal never enter here. */
    public static void onAbsorptionDepleted(ServerPlayer player, DamageSource source, ResourceLocation pool) {
        if (!player.isAlive() || player.isRemoved()
                || com.mistaboom.essence_ascendance.vitality.DeferredDamageService.deferred(source)) return;
        RecentHostileCombat.acceptedDamage(player,
                com.mistaboom.essence_ascendance.projectile.ProjectileOwnership.damageSource(source, player));
        Context context = current(player);
        for (SkillEffectHandler handler : SkillEffectRegistry.handlers())
            if (context.isEffective(handler.id())) handler.absorptionDepleted(context, source, pool);
    }

    private static Context current(ServerPlayer player) {
        PlayerRuntime runtime = PLAYERS.get(player.getUUID());
        if (runtime != null && runtime.player.get() != player) {
            ServerPlayer previous = runtime.player.get();
            if (previous != null) clear(new Context(previous, runtime));
            else runtime.states.values().forEach(SkillEffectState::clear);
            PLAYERS.remove(player.getUUID());
            runtime = null;
        }
        SkillEffectBalanceSettings settings = EssenceConfigManager.skillEffects();
        if (runtime == null) {
            runtime = new PlayerRuntime(player, settings);
            PLAYERS.put(player.getUUID(), runtime);
        }
        Context context = new Context(player, runtime);
        Set<ResourceLocation> effective = player.isAlive() && !player.isRemoved()
                ? CommittedSkillService.effectiveIds(player) : Set.of();
        Map<ResourceLocation, Integer> ranks = new java.util.TreeMap<>();
        if (!effective.isEmpty()) {
            var committed = CommittedSkillService.context(player);
            effective.forEach(id -> ranks.put(id, committed.authoritativeRank(id)));
        }
        if (runtime.baseSettings != settings || !runtime.ranks.equals(ranks)) {
            runtime.attackIdentity = new Object();
            if (runtime.baseSettings != settings) clear(context);
            else for (var entry : ranks.entrySet()) {
                Integer previousRank = runtime.ranks.get(entry.getKey());
                if (previousRank != null && !previousRank.equals(entry.getValue())) {
                    SkillEffectHandler handler = SkillEffectRegistry.get(entry.getKey());
                    if (handler != null) handler.deactivate(context);
                    runtime.attempt = null;
                }
            }
            runtime.baseSettings = settings;
            runtime.ranks = Map.copyOf(ranks);
            runtime.settings = com.mistaboom.essence_ascendance.skill.balance.SkillRankEffectScaling.apply(settings, ranks);
        }
        if (!runtime.effective.equals(effective)) {
            // No in-flight hit may be attributed to a changed effective loadout.
            // This applies equally to future branches without naming any skill.
            runtime.attempt = null;
            runtime.attackIdentity = new Object();
        }
        for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) {
            if (runtime.effective.contains(handler.id()) && !effective.contains(handler.id())) handler.deactivate(context);
        }
        runtime.effective = effective;
        // A wrapper normally closes in the same tick; this bounds even an interrupted adapter.
        if (runtime.attempt != null && context.now() - runtime.attempt.startedAt > 1) {
            runtime.attempt = null;
            dispatchMiss(context);
        }
        for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) {
            if (context.isEffective(handler.id())) handler.reconcile(context);
        }
        return context;
    }

    private static void clear(Context context) {
        CombatHudActivity.forget(context.player());
        for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) handler.deactivate(context);
        context.runtime.states.values().forEach(SkillEffectState::clear);
        context.runtime.states.clear();
        context.runtime.effective = Set.of();
        context.runtime.attempt = null;
        context.runtime.attackIdentity = new Object();
    }

    private static void dispatchMiss(Context context) {
        for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) {
            if (context.isEffective(handler.id())) handler.primaryMiss(context);
        }
    }

    private static void dispatchSuccessfulAttack(Context context, AttackResultContext result) {
        for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) {
            if (context.isEffective(handler.id())) handler.successfulAttack(context, result);
        }
    }

    public static final class Context {
        private final ServerPlayer player;
        private final PlayerRuntime runtime;

        private Context(ServerPlayer player, PlayerRuntime runtime) {
            this.player = player;
            this.runtime = runtime;
        }

        public ServerPlayer player() { return player; }
        public long now() { return player.level().getGameTime(); }
        public SkillEffectBalanceSettings settings() { return runtime.settings; }
        public boolean isEffective(ResourceLocation id) { return runtime.effective.contains(id); }
        public int rank(ResourceLocation id) { return runtime.ranks.getOrDefault(id, 0); }
        public double effectScale(ResourceLocation id) {
            return rank(id) == 0 ? 0.0 : com.mistaboom.essence_ascendance.skill.balance.SkillRankEffectScaling.factor(id, rank(id));
        }

        @SuppressWarnings("unchecked")
        public <T extends SkillEffectState> T state(ResourceLocation id, Supplier<T> factory) {
            return (T) runtime.states.computeIfAbsent(id, ignored -> factory.get());
        }

        @SuppressWarnings("unchecked")
        public <T extends SkillEffectState> T existingState(ResourceLocation id) {
            return (T) runtime.states.get(id);
        }

        public void discardState(ResourceLocation id) {
            SkillEffectState removed = runtime.states.remove(id);
            if (removed != null) removed.clear();
        }

        public boolean matchesPrimaryTarget(LivingEntity target) {
            return runtime.attempt != null && runtime.attempt.matches(target);
        }
    }

    private static final class PlayerRuntime {
        final WeakReference<ServerPlayer> player;
        final Map<ResourceLocation, SkillEffectState> states = new HashMap<>();
        Set<ResourceLocation> effective = Set.of();
        SkillEffectBalanceSettings baseSettings;
        SkillEffectBalanceSettings settings;
        Map<ResourceLocation, Integer> ranks = Map.of();
        Attempt attempt;
        Object attackIdentity = new Object();
        long lastGameplayTick = Long.MIN_VALUE;

        PlayerRuntime(ServerPlayer player, SkillEffectBalanceSettings settings) {
            this.player = new WeakReference<>(player);
            this.baseSettings = settings;
            this.settings = settings;
        }
    }

    private static final class Attempt {
        final long token;
        final WeakReference<Entity> target;
        final long startedAt;
        final Set<ResourceLocation> effectiveAtStart;
        boolean success;
        DamageSource source;
        double damageDealt;

        Attempt(long token, Entity target, long startedAt, Set<ResourceLocation> effectiveAtStart) {
            this.token = token;
            this.target = new WeakReference<>(target);
            this.startedAt = startedAt;
            this.effectiveAtStart = Set.copyOf(effectiveAtStart);
        }

        boolean matches(Entity entity) { return target.get() == entity; }
    }
}
