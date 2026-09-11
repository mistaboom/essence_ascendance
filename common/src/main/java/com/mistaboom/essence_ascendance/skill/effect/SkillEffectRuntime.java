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

    public static Set<ResourceLocation> implementedIds() { return SkillEffectRegistry.implementedIds(); }
    public static boolean isImplemented(ResourceLocation id) { return SkillEffectRegistry.isImplemented(id); }

    public static void tick(ServerPlayer player) {
        if (!player.isAlive() || player.isRemoved()) {
            reset(player);
            return;
        }
        Context context = current(player);
        if (context.runtime.lastGameplayTick != context.now()) {
            context.runtime.lastGameplayTick = context.now();
            for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) {
                if (context.isEffective(handler.id())) handler.tick(context);
            }
        }
    }

    /** Progression refresh preserves still-effective effects; config changes clear transient combat. */
    public static void refresh(ServerPlayer player) {
        if (player.isAlive() && !player.isRemoved()) current(player);
        else reset(player);
    }

    public static void reset(ServerPlayer player) {
        PlayerRuntime runtime = PLAYERS.remove(player.getUUID());
        if (runtime == null) {
            // Also remove known stable modifiers when recovering an old player instance.
            runtime = new PlayerRuntime(player, EssenceConfigManager.skillEffects());
        }
        clear(new Context(player, runtime));
    }

    public static void forget(ServerPlayer player) { reset(player); }

    public static void clearAll() {
        for (PlayerRuntime runtime : List.copyOf(PLAYERS.values())) {
            ServerPlayer player = runtime.player.get();
            if (player != null) clear(new Context(player, runtime));
            else runtime.states.values().forEach(SkillEffectState::clear);
        }
        PLAYERS.clear();
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
        return token;
    }

    /** The adapter supplies only an accepted primary hurt with observed health/absorption loss. */
    public static void onPrimaryMeleeSuccess(ServerPlayer player, LivingEntity target) {
        PlayerRuntime runtime = PLAYERS.get(player.getUUID());
        Attempt attempt = runtime == null ? null : runtime.attempt;
        if (attempt != null && !attempt.success && attempt.matches(target)) attempt.success = true;
    }

    /** Newly earned stacks are committed after Player.attack, so they cannot affect the earning hit. */
    public static void finishPrimaryAttack(ServerPlayer player, long token) {
        if (token == 0) return;
        PlayerRuntime runtime = PLAYERS.get(player.getUUID());
        Attempt attempt = runtime == null ? null : runtime.attempt;
        if (attempt == null || attempt.token != token) return;
        Context context = current(player);
        // Refresh can deactivate effects, change settings or replace the player runtime.
        // Never award the captured hit to a different loadout after that transition.
        if (context.runtime != runtime || runtime.attempt != attempt
                || !attempt.effectiveAtStart.equals(context.runtime.effective)) return;
        runtime.attempt = null;
        Entity target = attempt.target.get();
        if (attempt.success && target instanceof LivingEntity living && player.isAlive()) {
            for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) {
                if (context.isEffective(handler.id())) handler.primaryHit(context, living);
            }
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
        boolean primary = EquipmentDamageService.isPrimarySkillMelee(player, target, source);
        double multiplier = 1.0;
        for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) {
            if (context.isEffective(handler.id())) {
                multiplier *= SkillEffectMath.clamp(
                        handler.damageMultiplier(context, target, source, primary), 0.0, 100_001.0);
            }
        }
        return (float) Math.min(Float.MAX_VALUE, amount * multiplier);
    }

    /** Native confirmed death, while its authoritative source/caster scope still exists. */
    public static void onLivingDeath(LivingEntity victim, DamageSource source) {
        onEntityRemoved(victim);
        ServerPlayer player = EquipmentDamageService.skillDamagePlayer(victim, source);
        if (player == null || !player.isAlive() || player == victim) return;
        Context context = current(player);
        for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) {
            if (context.isEffective(handler.id())) handler.kill(context, victim);
        }
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
                if (!context.isEffective(handler.id())) continue;
                for (SkillEffectHudEntry entry : handler.hudEntries(context)) {
                    if (!entry.sourceSkill().equals(handler.id())) {
                        throw new IllegalArgumentException("HUD source does not match handler: " + handler.id());
                    }
                    entries.add(entry);
                }
            }
        }
        return new SkillEffectHudSnapshot(player.level().getGameTime(), player.getId(),
                player.level().dimension().location(), entries);
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
        if (runtime.settings != settings) {
            clear(context);
            runtime.settings = settings;
        }
        Set<ResourceLocation> effective = player.isAlive() && !player.isRemoved()
                ? CommittedSkillService.effectiveIds(player) : Set.of();
        if (!runtime.effective.equals(effective)) {
            // No in-flight hit may be attributed to a changed effective loadout.
            // This applies equally to future branches without naming any skill.
            runtime.attempt = null;
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
        for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) handler.deactivate(context);
        context.runtime.states.values().forEach(SkillEffectState::clear);
        context.runtime.states.clear();
        context.runtime.effective = Set.of();
        context.runtime.attempt = null;
    }

    private static void dispatchMiss(Context context) {
        for (SkillEffectHandler handler : SkillEffectRegistry.handlers()) {
            if (context.isEffective(handler.id())) handler.primaryMiss(context);
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
        SkillEffectBalanceSettings settings;
        Attempt attempt;
        long lastGameplayTick = Long.MIN_VALUE;

        PlayerRuntime(ServerPlayer player, SkillEffectBalanceSettings settings) {
            this.player = new WeakReference<>(player);
            this.settings = settings;
        }
    }

    private static final class Attempt {
        final long token;
        final WeakReference<Entity> target;
        final long startedAt;
        final Set<ResourceLocation> effectiveAtStart;
        boolean success;

        Attempt(long token, Entity target, long startedAt, Set<ResourceLocation> effectiveAtStart) {
            this.token = token;
            this.target = new WeakReference<>(target);
            this.startedAt = startedAt;
            this.effectiveAtStart = Set.copyOf(effectiveAtStart);
        }

        boolean matches(Entity entity) { return target.get() == entity; }
    }
}
