package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.AttackCategory;
import com.mistaboom.essence_ascendance.skill.effect.GuardCounterattackService;
import com.mistaboom.essence_ascendance.guard.*;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import com.mistaboom.essence_ascendance.projectile.MagicBoltEntity;
import com.mistaboom.essence_ascendance.projectile.ProjectileRuntime;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/*
 * Server-authoritative application layer for event-driven Ascendance damage
 * mechanics.
 *
 * This service contains only common gameplay math/classification. Fabric and
 * NeoForge provide thin loader hooks at their native damage modification
 * points and delegate into this class.
 *
 * Exactly ONE resistance category is selected for an incoming hit. This avoids
 * accidental multiplicative stacking for compound sources such as explosive or
 * fiery projectiles. Classification priority is explicit and deterministic:
 *
 *   FALL -> EXPLOSION -> FIRE -> MAGIC -> RANGED -> MELEE -> NONE
 *
 * Damage Reflection is calculated from actual health lost after Minecraft's
 * normal armor/enchantment/absorption pipeline. Armor-only retaliation retains
 * its direct-attacker rule. A functional held shield also handles responsible
 * projectile shooters. A single recursion guard protects ordinary AND
 * successfully blocked reflection without bypassing the retaliation target's
 * own defensive equipment.
 */
public final class EquipmentDamageService {

    private static final double EPSILON = 0.0000001;

    private static final Map<ServerPlayer, DamageEvaluation> LAST_DAMAGE =
            new WeakHashMap<>();

    private static final ThreadLocal<Integer> REFLECTION_DEPTH =
            ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<MeasuredDamage> MEASURED_DAMAGE = new ThreadLocal<>();
    private static final Map<ServerPlayer, GuardOutcome> LAST_GUARD = new WeakHashMap<>();
    private static long nextGuardEvent;
    private record ExplosionScope(Entity target, DamageSource source) { }
    private record PendingExplosion(DamageSource source, GuardOutcome outcome, boolean wardEligible) { }
    private static final ThreadLocal<ExplosionScope> EXPLOSION_HIT = new ThreadLocal<>();
    private static final Map<ServerPlayer, PendingExplosion> EXPLOSION_IMPULSES = new WeakHashMap<>();

    public record DamageResult(boolean accepted, double loss) { }
    private static final class MeasuredDamage {
        final LivingEntity target; final DamageSource source; double loss;
        SkillDamageFrame frame;
        SkillHealthSample sample;
        MeasuredDamage(LivingEntity target, DamageSource source) { this.target = target; this.source = source; }
    }
    /** Exact health/absorption-write measurement excludes nested sources and Totem restoration. */
    public static DamageResult measureDamage(LivingEntity target, DamageSource source, BooleanSupplier action) {
        MeasuredDamage previous = MEASURED_DAMAGE.get(), measurement = new MeasuredDamage(target, source);
        MEASURED_DAMAGE.set(measurement);
        try { return new DamageResult(action.getAsBoolean(), measurement.loss); }
        finally { if (previous == null) MEASURED_DAMAGE.remove(); else MEASURED_DAMAGE.set(previous); }
    }
    public static boolean withReflection(BooleanSupplier action) {
        int previous = REFLECTION_DEPTH.get(); REFLECTION_DEPTH.set(previous + 1);
        try { return action.getAsBoolean(); }
        finally { if (previous == 0) REFLECTION_DEPTH.remove(); else REFLECTION_DEPTH.set(previous); }
    }

    /* All scopes are server-thread-only, bounded to the native call and closed
     * in finally. No context or combat input is persisted on items/entities. */
    private static final ThreadLocal<PrimarySkillAttack> PRIMARY_SKILL_ATTACK = new ThreadLocal<>();
    private static final ThreadLocal<SweepSkillDamage> SWEEP_SKILL_DAMAGE = new ThreadLocal<>();
    private static final ThreadLocal<PrimarySkillHitProbe> PRIMARY_SKILL_HIT_PROBE = new ThreadLocal<>();
    private static final ThreadLocal<Deque<SkillHealthSample>> SKILL_HEALTH_SAMPLES =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Integer> SECONDARY_SKILL_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Deque<SkillDamageFrame>> SKILL_DAMAGE_FRAMES =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<ServerPlayer, Deque<Long>> EXPECTED_MAIN_SWINGS = new WeakHashMap<>();
    private static final int SWING_MATCH_TICKS = 2;

    /** Native Player.attack scope, including its cancellation/early-return paths. */
    public static void runPrimarySkillAttack(ServerPlayer player, Entity target, Runnable action) {
        if (PRIMARY_SKILL_ATTACK.get() != null || isReflectionInProgress() || SECONDARY_SKILL_DEPTH.get() > 0) {
            withSecondarySkillDamage(action);
            return;
        }
        boolean intercepted = com.mistaboom.essence_ascendance.projectile.ProjectileInterceptionService.attack(player);
        rememberMainSwing(player);
        long token = SkillEffectRuntime.beginPrimaryAttack(player, target);
        PRIMARY_SKILL_ATTACK.set(new PrimarySkillAttack(player, target));
        var exertion = com.mistaboom.essence_ascendance.attunement.AttunementGameplay.beginExertion(player, "combat");
        try {
            action.run();
        } finally {
            if (intercepted) player.resetAttackStrengthTicker();
            try {
                SkillEffectRuntime.finishPrimaryAttack(player, token);
            } finally {
                PRIMARY_SKILL_ATTACK.remove();
                com.mistaboom.essence_ascendance.attunement.AttunementGameplay.endExertion(exertion);
            }
        }
    }

    /** Wrap only Player.attack's Entity.hurt invocation, never its sweep loop. */
    public static boolean observePrimarySkillHit(Entity target, DamageSource source, BooleanSupplier action) {
        LivingEntity living = target instanceof LivingEntity entity ? entity : null;
        PrimarySkillAttack attack = PRIMARY_SKILL_ATTACK.get();
        if (living == null || attack == null || !isPrimarySkillMelee(attack.player, living, source)) {
            return action.getAsBoolean();
        }
        PrimarySkillHitProbe previous = PRIMARY_SKILL_HIT_PROBE.get();
        PrimarySkillHitProbe probe = new PrimarySkillHitProbe(living, source);
        PRIMARY_SKILL_HIT_PROBE.set(probe);
        try {
            boolean accepted = action.getAsBoolean();
            if (accepted && probe.damaged) {
                SkillEffectRuntime.onPrimaryMeleeSuccess(attack.player, living, source, probe.damageDealt);
            }
            return accepted;
        } finally {
            if (previous == null) PRIMARY_SKILL_HIT_PROBE.remove();
            else PRIMARY_SKILL_HIT_PROBE.set(previous);
        }
    }

    /** Measures the mitigated native health/absorption write before hurt() can
     * activate a Totem and heal the target. Nested damage has its own sample and
     * is subtracted from its enclosing sample, not credited to the primary hit. */
    public static void observeSkillHealthDamage(LivingEntity target, DamageSource source, Runnable action) {
        if (target.level().isClientSide) {
            action.run();
            return;
        }
        Deque<SkillHealthSample> samples = SKILL_HEALTH_SAMPLES.get();
        SkillHealthSample sample = new SkillHealthSample(target, healthAndAbsorption(target));
        MeasuredDamage measurement = MEASURED_DAMAGE.get();
        // DamageSource instances may be reused by nested hits. Bind this request to
        // its native call and exact health write, not just the source/target pair.
        if (measurement != null && measurement.target == target && measurement.source == source
                && measurement.sample == null
                && (measurement.frame == null || measurement.frame == SKILL_DAMAGE_FRAMES.get().peek()))
            measurement.sample = sample;
        double healthBefore = target.getHealth();
        samples.push(sample);
        boolean completed = false;
        try {
            action.run();
            completed = true;
        } finally {
            samples.pop();
            double totalLoss = Math.max(0.0, sample.before - healthAndAbsorption(target));
            double totalHealthLoss = Math.max(0, healthBefore - target.getHealth());
            for (SkillHealthSample parent : samples) {
                if (parent.target == target) {
                    parent.nestedLoss += totalLoss;
                    parent.nestedHealthLoss += totalHealthLoss;
                    break;
                }
            }
            if (samples.isEmpty()) SKILL_HEALTH_SAMPLES.remove();
            PrimarySkillHitProbe probe = PRIMARY_SKILL_HIT_PROBE.get();
            SkillDamageFrame frame = SKILL_DAMAGE_FRAMES.get().peek();
            double ownLoss = Math.max(0.0, totalLoss - sample.nestedLoss);
            double ownHealthLoss = Math.min(ownLoss, Math.max(0, totalHealthLoss - sample.nestedHealthLoss));
            if (completed && measurement != null && measurement.sample == sample) measurement.loss += ownLoss;
            if (completed && target instanceof ServerPlayer defender) {
                ReflectionFrame guard = frame(defender, source);
                if (guard != null) {
                    guard.healthLost += (float) ownHealthLoss;
                    guard.absorptionLost += ownLoss - ownHealthLoss;
                    if (!guard.suppressed && ownHealthLoss > 0) {
                        guard.ordinaryPercent = guard.preHitStats.damageReflectionPercent();
                        if (guard.preHitHeldShield || source.isDirect()) guard.ordinaryDamage +=
                                ShieldMath.reflectedPortion((float) ownHealthLoss, guard.ordinaryPercent);
                    }
                }
            }
            if (completed) com.mistaboom.essence_ascendance.attunement.AttunementGameplay.damageMeasured(
                    target, source, ownLoss, ownHealthLoss);
            // The exact direct-projectile probe also observes sanitized returned shots. Their
            // secondary scope suppresses offensive procs, not measured native collision success.
            if (completed && Double.isFinite(ownLoss) && ownLoss > 0 && !isReflectionInProgress())
                ProjectileRuntime.confirmedDamage(target, source, ownLoss);
            boolean realDamage = completed && Double.isFinite(ownLoss) && ownLoss > 0.0
                    && frame != null && !frame.nested && frame.target == target && frame.source == source
                    && !isReflectionInProgress() && SECONDARY_SKILL_DEPTH.get() == 0;
            if (realDamage) {
                AttackCategory category = primaryAttackCategory(target, source);
                if (category == AttackCategory.MELEE) {
                    if (probe != null && probe.target == target && probe.source == source) {
                        probe.damaged = true;
                        probe.damageDealt = ownLoss;
                    }
                } else if (category != null && !frame.primaryReported) {
                    frame.primaryReported = true;
                    ServerPlayer player = skillDamagePlayer(target, source);
                    if (player != null) {
                        SkillEffectRuntime.onPrimaryAttackSuccess(player, target, category, source, ownLoss);
                    }
                }
            }
        }
    }

    private static double healthAndAbsorption(LivingEntity target) {
        return (double) target.getHealth() + target.getAbsorptionAmount();
    }

    /** Only the actual native sweep call gets this scope. It is ordinary melee
     * for Desperation/kill credit, but never a primary hit for Frenzy/Armor Crack. */
    public static boolean observeNativeSkillSweep(ServerPlayer player, LivingEntity target,
                                                 DamageSource source, BooleanSupplier action) {
        PrimarySkillAttack attack = PRIMARY_SKILL_ATTACK.get();
        if (attack == null || attack.player != player) return action.getAsBoolean();
        SweepSkillDamage previous = SWEEP_SKILL_DAMAGE.get();
        SWEEP_SKILL_DAMAGE.set(new SweepSkillDamage(player, target, source));
        try {
            return action.getAsBoolean();
        } finally {
            if (previous == null) SWEEP_SKILL_DAMAGE.remove();
            else SWEEP_SKILL_DAMAGE.set(previous);
        }
    }

    /** Wrap native LivingEntity.hurt on each loader; rejects nested damage effects. */
    public static boolean withSkillDamageFrame(LivingEntity target, DamageSource source, BooleanSupplier action) {
        if (target.level().isClientSide) return action.getAsBoolean();
        Deque<SkillDamageFrame> frames = SKILL_DAMAGE_FRAMES.get();
        SkillDamageFrame frame = new SkillDamageFrame(target, source, !frames.isEmpty());
        frames.push(frame);
        MeasuredDamage measurement = MEASURED_DAMAGE.get();
        if (measurement != null && measurement.frame == null && measurement.sample == null
                && measurement.target == target && measurement.source == source) measurement.frame = frame;
        try {
            return action.getAsBoolean();
        } finally {
            frames.pop();
            if (frames.isEmpty()) SKILL_DAMAGE_FRAMES.remove();
        }
    }

    /** Equipment/enchantments supply native outgoing damage first; skill
     * percentages apply once before target equipment resistance and vanilla
     * armor, toughness, enchantments and absorption. No extra hurt call. */
    public static float modifyOutgoingSkillDamage(LivingEntity target, DamageSource source, float amount) {
        Deque<SkillDamageFrame> frames = SKILL_DAMAGE_FRAMES.get();
        SkillDamageFrame frame = frames.peek();
        if (frame == null || frame.target != target || frame.source != source || frame.modified) return amount;
        frame.modified = true;
        return SkillEffectRuntime.modifyOutgoingDamage(target, source, amount);
    }

    /** Exact authoritative sources accepted by this batch; unknown damage fails closed. */
    public static ServerPlayer skillDamagePlayer(LivingEntity target, DamageSource source) {
        if (target.level().isClientSide || isReflectionInProgress() || SECONDARY_SKILL_DEPTH.get() > 0
                || source.is(DamageTypeTags.IS_EXPLOSION)
                || !(source.getEntity() instanceof ServerPlayer player) || !canSkillHarm(player, target)) return null;
        SkillDamageFrame frame = SKILL_DAMAGE_FRAMES.get().peek();
        if (frame != null && (frame.nested || frame.target != target || frame.source != source)) return null;
        if (isPrimarySkillMelee(player, target, source)) return player;
        SweepSkillDamage sweep = SWEEP_SKILL_DAMAGE.get();
        if (sweep != null && sweep.player == player && sweep.target == target && sweep.source == source
                && source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == player) return player;
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile && ProjectileRuntime.state(projectile) != null
                && ProjectileRuntime.state(projectile).redirected) return null;
        if (direct instanceof Projectile projectile && !ProjectileRuntime.secondary(projectile) && projectile.getOwner() == player
                && source.is(DamageTypeTags.IS_PROJECTILE)) return player;
        return null;
    }

    public static boolean isPrimarySkillMelee(ServerPlayer player, LivingEntity target, DamageSource source) {
        PrimarySkillAttack attack = PRIMARY_SKILL_ATTACK.get();
        return !isReflectionInProgress() && SECONDARY_SKILL_DEPTH.get() == 0
                && SWEEP_SKILL_DAMAGE.get() == null
                && attack != null && attack.player == player && attack.target == target
                && source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == player
                && source.getEntity() == player;
    }

    /** Null means the source is not a qualifying first-party primary attack. */
    public static AttackCategory primaryAttackCategory(ServerPlayer player, LivingEntity target,
                                                       DamageSource source) {
        if (isPrimarySkillMelee(player, target, source)) return AttackCategory.MELEE;
        if (isReflectionInProgress() || SECONDARY_SKILL_DEPTH.get() > 0) return null;
        Entity direct = source.getDirectEntity();
        if (direct instanceof MagicBoltEntity bolt && bolt.getOwner() == player) return AttackCategory.CASTER;
        if (direct instanceof Projectile projectile && !ProjectileRuntime.secondary(projectile) && projectile.getOwner() == player
                && source.is(DamageTypeTags.IS_PROJECTILE)) return AttackCategory.RANGED;
        return null;
    }

    public static AttackCategory primaryAttackCategory(LivingEntity target, DamageSource source) {
        ServerPlayer player = skillDamagePlayer(target, source);
        return player == null ? null : primaryAttackCategory(player, target, source);
    }

    /** Future chain/lightning/shard effects must run their damage inside this scope. */
    public static void withSecondarySkillDamage(Runnable action) {
        int previous = SECONDARY_SKILL_DEPTH.get();
        SECONDARY_SKILL_DEPTH.set(previous + 1);
        try {
            action.run();
        } finally {
            if (previous == 0) SECONDARY_SKILL_DEPTH.remove();
            else SECONDARY_SKILL_DEPTH.set(previous);
        }
    }

    public static boolean isSecondarySkillDamage() { return SECONDARY_SKILL_DEPTH.get() > 0; }

    public static boolean canSkillHarm(ServerPlayer player, Entity target) {
        if (target == player || target.level() != player.level() || !player.isAlive() || player.isSpectator()
                || target.isRemoved() || !(target instanceof LivingEntity)) return false;
        if (target instanceof Player other && (!player.server.isPvpAllowed() || !player.canHarmPlayer(other))) return false;
        return player.getTeam() == null || !player.isAlliedTo(target) || player.getTeam().isAllowFriendlyFire();
    }

    /** Called only after the server processes a native main-hand swing packet.
     * A matched attack animation carries no success assertion. Unmatched air
     * swings break the chain, while authoritative block ray hits are mining. */
    public static void onServerSkillSwing(ServerPlayer player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.isAlive() || player.isSpectator()) return;
        // Projectile interception runs before ServerPlayer.swing consumes native attack readiness.
        tickSkillInput(player);
        Deque<Long> expected = EXPECTED_MAIN_SWINGS.get(player);
        if (expected != null && !expected.isEmpty()) {
            expected.removeFirst();
            if (expected.isEmpty()) EXPECTED_MAIN_SWINGS.remove(player);
            return;
        }
        if (player.isUsingItem()) return;
        HitResult block = player.pick(player.blockInteractionRange(), 0.0F, false);
        if (block.getType() == HitResult.Type.BLOCK) return;
        SkillEffectRuntime.onAirSwing(player);
    }

    public static void rememberMainSwing(ServerPlayer player) {
        tickSkillInput(player);
        Deque<Long> expected = EXPECTED_MAIN_SWINGS.computeIfAbsent(player, ignored -> new ArrayDeque<>());
        if (expected.size() >= 16) expected.removeFirst();
        expected.addLast(player.serverLevel().getGameTime());
    }

    public static void tickSkillInput(ServerPlayer player) {
        PendingExplosion pending = EXPLOSION_IMPULSES.get(player);
        if (pending != null && pending.outcome.tick() != player.level().getGameTime()) EXPLOSION_IMPULSES.remove(player);
        Deque<Long> expected = EXPECTED_MAIN_SWINGS.get(player);
        if (expected == null) return;
        long tick = player.serverLevel().getGameTime();
        while (!expected.isEmpty() && tick - expected.peekFirst() > SWING_MATCH_TICKS) expected.removeFirst();
        if (expected.isEmpty()) EXPECTED_MAIN_SWINGS.remove(player);
    }

    public static void forgetSkillInput(ServerPlayer player) {
        EXPECTED_MAIN_SWINGS.remove(player);
    }

    public static void clearSkillInput() {
        EXPECTED_MAIN_SWINGS.clear();
        EXPLOSION_IMPULSES.clear(); LAST_GUARD.clear(); REFLECTION_FRAMES.clear();
        LAST_DAMAGE.clear(); LAST_REFLECTION.clear(); nextGuardEvent = 0;
    }

    private record PrimarySkillAttack(ServerPlayer player, Entity target) {}
    private record SweepSkillDamage(ServerPlayer player, Entity target, DamageSource source) {}
    private static final class PrimarySkillHitProbe {
        final LivingEntity target;
        final DamageSource source;
        boolean damaged;
        double damageDealt;
        PrimarySkillHitProbe(LivingEntity target, DamageSource source) {
            this.target = target;
            this.source = source;
        }
    }
    private static final class SkillHealthSample {
        final LivingEntity target;
        final double before;
        double nestedLoss;
        double nestedHealthLoss;
        SkillHealthSample(LivingEntity target, double before) {
            this.target = target;
            this.before = before;
        }
    }
    private static final class SkillDamageFrame {
        final LivingEntity target;
        final DamageSource source;
        final boolean nested;
        boolean modified;
        boolean primaryReported;
        SkillDamageFrame(LivingEntity target, DamageSource source, boolean nested) {
            this.target = target;
            this.source = source;
            this.nested = nested;
        }
    }

    private EquipmentDamageService() {
    }

    public static float modifyIncomingDamage(
            ServerPlayer player,
            DamageSource source,
            float incomingDamage
    ) {
        if (!Float.isFinite(incomingDamage) || incomingDamage <= 0.0F) {
            return incomingDamage;
        }

        /*
         * Reflection emission is suppressed separately. Do not return early
         * here: the retaliation target keeps its ordinary Ascendance defenses
         * just as it keeps vanilla armor/enchantment defenses.
         */
        ReflectionFrame frame = frame(player, source);
        DamageStatState stats = frame != null
                ? frame.preHitStats
                : evaluateStats(player);
        DamageCategory category = classify(source);
        double resistancePercent = stats.resistanceFor(category);
        double clampedResistance = clamp(resistancePercent, 0.0, 100.0);

        float resolvedDamage = (float) Math.max(
                0.0,
                incomingDamage * (1.0 - clampedResistance / 100.0)
        );
        if (frame != null) {
            frame.incoming = incomingDamage;
            frame.mitigated = Math.max(0, incomingDamage - resolvedDamage);
        }

        com.mistaboom.essence_ascendance.attunement.AttunementGameplay.prevented(player, source, incomingDamage, resolvedDamage);

        LAST_DAMAGE.put(
                player,
                new DamageEvaluation(
                        category,
                        incomingDamage,
                        resolvedDamage,
                        clampedResistance,
                        -1.0F,
                        stats.damageReflectionPercent(),
                        0.0F
                )
        );

        return resolvedDamage;
    }

    private static final Map<ServerPlayer, Deque<ReflectionFrame>> REFLECTION_FRAMES = new WeakHashMap<>();
    private static final Map<ServerPlayer, ReflectionEvaluation> LAST_REFLECTION = new WeakHashMap<>();

    /** Loader wrappers pair this with endDamage in a finally block, including cancellations/exceptions. */
    public static void beginDamage(ServerPlayer player, DamageSource source, float incoming) {
        DamageStatState preHitStats = evaluateStats(player);
        boolean preHitHeldShield = EquipmentShieldService.heldContext(player) != null;
        REFLECTION_FRAMES.computeIfAbsent(player, ignored -> new ArrayDeque<>())
                .push(new ReflectionFrame(
                        source,
                        isReflectionInProgress() || isSecondarySkillDamage(),
                        preHitStats,
                        preHitHeldShield
                ));
        ReflectionFrame frame = frame(player, source);
        frame.event = ++nextGuardEvent;
        frame.incoming = Float.isFinite(incoming) ? Math.max(0, incoming) : 0;
        frame.skillShield = EquipmentShieldService.canGuard(player, player.getMainHandItem())
                || EquipmentShieldService.canGuard(player, player.getOffhandItem());
        frame.wardPercent = frame.skillShield ? preHitStats.damageReflectionPercent() : armorReflectionPercent(player);
        frame.guard = GuardLifecycle.observe(player);
        frame.tick = player.level().getGameTime();
        ExplosionScope explosion = EXPLOSION_HIT.get();
        frame.deferEcho = explosion != null && explosion.target == player && explosion.source == source;
    }

    public static void captureBlockingShield(ServerPlayer player, DamageSource source) {
        ReflectionFrame frame = frame(player, source);
        if (frame != null && !frame.suppressed) {
            frame.shield = EquipmentShieldService.blockingContext(player);
        }
    }

    /** Receives only the authoritative stopped portion, NEVER the shield durability cost. */
    public static void recordBlockedDamage(ServerPlayer player, DamageSource source, float stoppedDamage) {
        ReflectionFrame frame = frame(player, source);
        if (frame != null && !frame.blockRecorded
                && Float.isFinite(stoppedDamage) && stoppedDamage > 0) {
            frame.blockRecorded = true;
            frame.blockedDamage = stoppedDamage;
            com.mistaboom.essence_ascendance.attunement.AttunementGameplay.blocked(player, source, stoppedDamage);
        }
    }

    /** Successful normal completion of the native block path; raising alone never calls this. */
    public static void commitBlock(ServerPlayer player, DamageSource source) {
        ReflectionFrame frame = frame(player, source);
        if (frame != null) frame.blockCompleted = true;
    }

    /**
     * Closes one damage scope. A rejected/canceled hurt cannot retaliate;
     * vanilla's false result for a fully blocked hit is distinguished by the
     * authoritative block commit recorded inside LivingEntity#hurt.
     */
    public static void endDamage(
            ServerPlayer victim,
            DamageSource source,
            boolean returnedNormally,
            boolean damageAccepted
    ) {
        Deque<ReflectionFrame> frames = REFLECTION_FRAMES.get(victim);
        if (frames == null || frames.isEmpty()) return;
        ReflectionFrame frame = frames.pop();
        if (frames.isEmpty()) REFLECTION_FRAMES.remove(victim);
        if (!returnedNormally) return;
        if (frame.source != source) return;
        float blocked = frame.blockCompleted ? frame.blockedDamage : 0;
        double blockReflection = frame.shield == null ? 0
                : ShieldMath.reflectedPortion(blocked, frame.shield.blockedReflectionPercent());
        boolean actualHit = damageAccepted || frame.blockCompleted && blocked > 0;
        boolean validDefender = !victim.isRemoved() && !victim.isSpectator() && !victim.getAbilities().invulnerable;
        var resolvedSource = ReflectionRouter.source(victim, source);
        var context = SkillEffectRuntime.context(victim);
        boolean perfect = actualHit && !frame.suppressed && frame.guard.functional()
                && PerfectGuardFramework.perfect(frame.tick, frame.guard.readyTick(), context.settings().guard().perfectGuard().windowTicks(),
                        frame.guard.nativeReady(), blocked);
        // Rewards commit before reflection, so the earning block may use its new amplifier.
        if (actualHit && !frame.suppressed && validDefender && blocked > 0 && frame.guard.functional()) {
            GuardReflectionEffects.onBlock(victim, frame.event, blocked, perfect);
            GuardCounterattackService.onBlock(victim, frame.event, blocked, perfect);
        }
        boolean ward = context.isEffective(SkillIds.REFLEXIVE_WARD) && resolvedSource.eligible()
                && com.mistaboom.essence_ascendance.projectile.ProjectileOwnership.hostileDamageSource(victim, resolvedSource.target());
        double extension = ReflectionRouter.wardExtension(blocked + frame.mitigated, blocked, frame.healthLost + frame.absorptionLost,
                frame.wardPercent, context.settings().guard().ward().preventedReflectionScale(),
                actualHit && validDefender && ward && !frame.suppressed && (frame.skillShield || source.isDirect()));
        double multiplier = GuardReflectionEffects.multiplier(context);
        double requested = actualHit && validDefender ? ReflectionRouter.compose(frame.ordinaryDamage, blockReflection, extension, multiplier) : 0;
        var result = ReflectionRouter.reflect(victim, resolvedSource, requested, frame.suppressed || !actualHit || !validDefender);
        boolean echoEligible = actualHit && validDefender && ward && !frame.suppressed;
        var echo = echoEligible && !frame.deferEcho
                ? KnockbackEchoService.echo(victim, resolvedSource.target(), frame.attemptedKnockback, context.settings().guard().ward())
                : KnockbackEchoService.Result.none(frame.deferEcho ? "awaiting_correlated_explosion_impulse" : "ineligible_hit");
        if (result.confirmed() > 0) ReflectionRouter.reprisal(victim, resolvedSource.target(), result.confirmed(), context);
        recordDamageDiagnostic(victim, source, frame.healthLost, frame.ordinaryPercent, (float) result.requested());
        LAST_REFLECTION.put(victim, new ReflectionEvaluation(blocked, frame.healthLost,
                frame.ordinaryPercent, frame.shield == null ? 0 : frame.shield.nativeReflectionPercent(),
                frame.shield == null ? 0 : frame.shield.investedReflectionPercent(),
                frame.shield == null ? 0 : frame.shield.amplification(),
                frame.ordinaryDamage, blockReflection, (float) result.requested()));
        LAST_GUARD.put(victim, new GuardOutcome(frame.event, frame.tick, victim.getUUID(), frame.guard,
                source.getMsgId(), source.getDirectEntity() == null ? null : source.getDirectEntity().getUUID(),
                resolvedSource.target() == null ? null : resolvedSource.target().getUUID(), resolvedSource.decision(),
                frame.incoming, blocked, frame.mitigated, frame.healthLost, frame.absorptionLost, damageAccepted,
                frame.blockCompleted && blocked > 0, perfect, frame.suppressed ? 1 : 0,
                frame.attemptedKnockback, frame.acceptedKnockback, frame.knockbackDecision, echo,
                frame.ordinaryDamage, frame.shield == null ? 0 : ShieldMath.reflectedPortion(blocked, frame.shield.nativeBlockedReflectionPercent()),
                frame.shield == null ? 0 : ShieldMath.reflectedPortion(blocked, frame.shield.investedBlockedReflectionPercent()),
                extension, multiplier, result.requested(), result.confirmed(), !actualHit ? "unconfirmed_native_hit" : result.decision()));
        if (frame.deferEcho) EXPLOSION_IMPULSES.put(victim, new PendingExplosion(source, LAST_GUARD.get(victim), echoEligible));
    }

    private static ReflectionFrame frame(ServerPlayer player, DamageSource source) {
        Deque<ReflectionFrame> frames = REFLECTION_FRAMES.get(player);
        return frames == null || frames.isEmpty() || frames.peek().source != source ? null : frames.peek();
    }

    private static void recordDamageDiagnostic(ServerPlayer victim, DamageSource source, float healthDamage,
                                              double ordinaryPercent, float reflected) {
        DamageEvaluation previous = LAST_DAMAGE.get(victim);
        LAST_DAMAGE.put(victim, new DamageEvaluation(classify(source),
                previous == null ? -1 : previous.incomingDamage(),
                previous == null ? -1 : previous.resolvedIncomingDamage(),
                previous == null ? 0 : previous.resistancePercent(),
                healthDamage, ordinaryPercent, reflected));
    }

    public static Optional<ReflectionEvaluation> lastReflection(ServerPlayer player) {
        return Optional.ofNullable(LAST_REFLECTION.get(player));
    }

    public static double armorReflectionPercent(ServerPlayer player) {
        return percent(EquipmentShieldService.playerData(player),
                EquipmentStatResolver.evaluateWornArmor(player), EssenceStats.DAMAGE_REFLECTION);
    }

    private static final class ReflectionFrame {
        final DamageSource source;
        final boolean suppressed;
        final DamageStatState preHitStats;
        final boolean preHitHeldShield;
        EquipmentShieldService.Context shield;
        float blockedDamage;
        float healthLost;
        double ordinaryDamage;
        double ordinaryPercent;
        boolean blockCompleted;
        boolean blockRecorded;
        boolean skillShield;
        boolean deferEcho;
        long event, tick;
        double incoming, mitigated, absorptionLost, wardPercent;
        GuardLifecycle.Snapshot guard = GuardLifecycle.Snapshot.empty();
        Vec3 attemptedKnockback = Vec3.ZERO, acceptedKnockback = Vec3.ZERO;
        String knockbackDecision = "no_correlated_native_attempt";
        ReflectionFrame(
                DamageSource source,
                boolean suppressed,
                DamageStatState preHitStats,
                boolean preHitHeldShield
        ) {
            this.source = source;
            this.suppressed = suppressed;
            this.preHitStats = preHitStats;
            this.preHitHeldShield = preHitHeldShield;
        }
    }

    public record ReflectionEvaluation(float blockedDamage, float actualHealthLost, double ordinaryPercent,
                                       double nativePercent, double shieldInvestedPercent, double amplification,
                                       double ordinaryReflectedDamage, double blockReflectedDamage,
                                       float requestedRetaliationDamage) {}

    public static DamageStatState evaluateStats(ServerPlayer player) {
        PlayerEssenceData playerData =
                EssenceSavedData
                        .get(player.server)
                        .getPlayerData(player.getUUID());

        EquipmentStatState worn =
                EquipmentStatResolver.evaluateWornArmor(player);

        double reflection = percent(playerData, worn, EssenceStats.DAMAGE_REFLECTION);
        EquipmentShieldService.Context shield = EquipmentShieldService.heldContext(player);
        if (shield != null) reflection = shield.ordinaryReflectionPercent(reflection);

        return new DamageStatState(
                percent(playerData, worn, EssenceStats.MELEE_RESISTANCE),
                percent(playerData, worn, EssenceStats.RANGED_RESISTANCE),
                percent(playerData, worn, EssenceStats.MAGIC_RESISTANCE),
                percent(playerData, worn, EssenceStats.FALL_RESISTANCE),
                percent(playerData, worn, EssenceStats.FIRE_RESISTANCE),
                percent(playerData, worn, EssenceStats.EXPLOSION_RESISTANCE),
                reflection
        );
    }

    public static Optional<DamageEvaluation> lastDamage(ServerPlayer player) {
        return Optional.ofNullable(LAST_DAMAGE.get(player));
    }

    public static void forget(ServerPlayer player) {
        forgetSkillInput(player);
        LAST_DAMAGE.remove(player);
        LAST_REFLECTION.remove(player);
        REFLECTION_FRAMES.remove(player);
        LAST_GUARD.remove(player);
        GuardLifecycle.forget(player);
        ReflectionRouter.forget(player);
        EXPLOSION_IMPULSES.remove(player);
    }

    public static Optional<GuardOutcome> lastGuardOutcome(ServerPlayer player) { return Optional.ofNullable(LAST_GUARD.get(player)); }

    /** Pair an explosion's exact hurt invocation with its later resistance-adjusted native impulse. */
    public static boolean withExplosionHit(Entity target, DamageSource source, BooleanSupplier original) {
        ExplosionScope previous = EXPLOSION_HIT.get();
        EXPLOSION_HIT.set(new ExplosionScope(target, source));
        if (target instanceof ServerPlayer player) EXPLOSION_IMPULSES.remove(player);
        try { return original.getAsBoolean(); }
        finally { if (previous == null) EXPLOSION_HIT.remove(); else EXPLOSION_HIT.set(previous); }
    }
    public static void explosionKnockback(Entity entity, DamageSource source, Vec3 rawAttempt, Vec3 proposed, Runnable original) {
        Vec3 before = entity.getDeltaMovement();
        boolean protection = GuardCounterattackService.suppressDisplacement(entity);
        if (!protection) original.run();
        if (!(entity instanceof ServerPlayer player)) return;
        PendingExplosion pending = EXPLOSION_IMPULSES.remove(player);
        GuardOutcome current = LAST_GUARD.get(player);
        if (pending == null || pending.source != source || current == null || current.eventId() != pending.outcome.eventId()
                || pending.outcome.tick() != player.level().getGameTime()) return;
        Vec3 bounded = KnockbackEchoService.bounded(rawAttempt);
        Vec3 attempt = KnockbackEchoService.bounded(current.attemptedKnockback().add(bounded));
        var context = SkillEffectRuntime.context(player);
        var responsible = ReflectionRouter.source(player, source);
        var echo = pending.wardEligible && responsible.eligible() && context.isEffective(SkillIds.REFLEXIVE_WARD)
                ? KnockbackEchoService.echo(player, responsible.target(), attempt, context.settings().guard().ward())
                : KnockbackEchoService.Result.none("ineligible_explosion_source_or_hit");
        LAST_GUARD.put(player, current.withKnockback(attempt,
                current.acceptedKnockback().add(entity.getDeltaMovement().subtract(before)),
                protection ? "explosion_riposte_suppressed" : "native_explosion_resolved", echo));
    }

    /** The real native knockback invocation is sampled even when resistance/protection suppresses it. */
    public static void observeGuardKnockback(LivingEntity target, double strength, double x, double z, Runnable original) {
        ReflectionFrame guard = target instanceof ServerPlayer player ? activeFrame(player) : null;
        Vec3 attempt = KnockbackEchoService.attempt(strength, x, z);
        Vec3 before = target.getDeltaMovement();
        boolean protectedAttack = GuardCounterattackService.suppressDisplacement(target);
        if (!protectedAttack) original.run();
        if (guard != null && !guard.suppressed) {
            guard.attemptedKnockback = KnockbackEchoService.bounded(guard.attemptedKnockback.add(attempt));
            guard.acceptedKnockback = guard.acceptedKnockback.add(target.getDeltaMovement().subtract(before));
            guard.knockbackDecision = protectedAttack ? "riposte_resolution_suppressed" : guard.acceptedKnockback.lengthSqr() == 0
                    ? "native_resisted_or_canceled" : "native_accepted";
        }
    }
    private static ReflectionFrame activeFrame(ServerPlayer player) {
        var frames = REFLECTION_FRAMES.get(player); return frames == null ? null : frames.peek();
    }

    /** Native armor and magic reduction share the existing measured-prevention hooks. */
    public static void recordGuardPrevention(LivingEntity target, DamageSource source, double before, double after) {
        if (!(target instanceof ServerPlayer player) || !Double.isFinite(before) || !Double.isFinite(after)) return;
        ReflectionFrame frame = frame(player, source);
        if (frame != null) frame.mitigated += Math.max(0, before - after);
    }

    public static DamageCategory classify(DamageSource source) {
        if (source.is(AscendanceDamageTypeTags.FALL)) {
            return DamageCategory.FALL;
        }

        if (source.is(AscendanceDamageTypeTags.EXPLOSION)) {
            return DamageCategory.EXPLOSION;
        }

        if (source.is(AscendanceDamageTypeTags.FIRE)) {
            return DamageCategory.FIRE;
        }

        if (source.is(AscendanceDamageTypeTags.MAGIC)) {
            return DamageCategory.MAGIC;
        }

        if (source.is(AscendanceDamageTypeTags.RANGED)) {
            return DamageCategory.RANGED;
        }

        if (source.isDirect()
                && source.getEntity() instanceof LivingEntity) {
            return DamageCategory.MELEE;
        }

        return DamageCategory.NONE;
    }

    public static boolean isReflectionInProgress() {
        return REFLECTION_DEPTH.get() > 0;
    }

    private static double percent(
            PlayerEssenceData playerData,
            EquipmentStatState worn,
            StatDefinition stat
    ) {
        return EquipmentValueService.scaledBonus(
                playerData,
                stat,
                worn.strength(stat)
        );
    }

    private static double clamp(
            double value,
            double min,
            double max
    ) {
        return Math.max(min, Math.min(max, value));
    }

    public enum DamageCategory {
        NONE,
        MELEE,
        RANGED,
        MAGIC,
        FALL,
        FIRE,
        EXPLOSION
    }

    public record DamageStatState(
            double meleeResistancePercent,
            double rangedResistancePercent,
            double magicResistancePercent,
            double fallResistancePercent,
            double fireResistancePercent,
            double explosionResistancePercent,
            double damageReflectionPercent
    ) {
        public double resistanceFor(DamageCategory category) {
            return switch (category) {
                case MELEE -> meleeResistancePercent;
                case RANGED -> rangedResistancePercent;
                case MAGIC -> magicResistancePercent;
                case FALL -> fallResistancePercent;
                case FIRE -> fireResistancePercent;
                case EXPLOSION -> explosionResistancePercent;
                case NONE -> 0.0;
            };
        }
    }

    public record DamageEvaluation(
            DamageCategory category,
            float incomingDamage,
            float resolvedIncomingDamage,
            double resistancePercent,
            float actualHealthDamage,
            double reflectionPercent,
            float reflectedDamage
    ) {
    }
}
