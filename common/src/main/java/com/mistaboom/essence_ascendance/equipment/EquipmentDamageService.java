package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.AttackCategory;
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
        rememberMainSwing(player);
        long token = SkillEffectRuntime.beginPrimaryAttack(player, target);
        PRIMARY_SKILL_ATTACK.set(new PrimarySkillAttack(player, target));
        var exertion = com.mistaboom.essence_ascendance.attunement.AttunementGameplay.beginExertion(player, "combat");
        try {
            action.run();
        } finally {
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
        double healthBefore = target.getHealth();
        samples.push(sample);
        boolean completed = false;
        try {
            action.run();
            completed = true;
        } finally {
            samples.pop();
            double totalLoss = Math.max(0.0, sample.before - healthAndAbsorption(target));
            for (SkillHealthSample parent : samples) {
                if (parent.target == target) {
                    parent.nestedLoss += totalLoss;
                    break;
                }
            }
            if (samples.isEmpty()) SKILL_HEALTH_SAMPLES.remove();
            PrimarySkillHitProbe probe = PRIMARY_SKILL_HIT_PROBE.get();
            SkillDamageFrame frame = SKILL_DAMAGE_FRAMES.get().peek();
            double ownLoss = Math.max(0.0, totalLoss - sample.nestedLoss);
            if (completed) com.mistaboom.essence_ascendance.attunement.AttunementGameplay.damageMeasured(
                    target, source, ownLoss, Math.min(ownLoss, Math.max(0, healthBefore - target.getHealth())));
            boolean realDamage = completed && Double.isFinite(ownLoss) && ownLoss > 0.0
                    && frame != null && !frame.nested && frame.target == target && frame.source == source
                    && !isReflectionInProgress() && SECONDARY_SKILL_DEPTH.get() == 0;
            if (realDamage) {
                ProjectileRuntime.confirmedDamage(target, source, ownLoss);
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
        frames.push(new SkillDamageFrame(target, source, !frames.isEmpty()));
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
    private static final Map<ServerPlayer, Deque<HealthSample>> HEALTH_SAMPLES = new WeakHashMap<>();
    private static final Map<ServerPlayer, ReflectionEvaluation> LAST_REFLECTION = new WeakHashMap<>();

    /** Loader wrappers pair this with endDamage in a finally block, including cancellations/exceptions. */
    public static void beginDamage(ServerPlayer player, DamageSource source) {
        DamageStatState preHitStats = evaluateStats(player);
        boolean preHitHeldShield = EquipmentShieldService.heldContext(player) != null;
        REFLECTION_FRAMES.computeIfAbsent(player, ignored -> new ArrayDeque<>())
                .push(new ReflectionFrame(
                        source,
                        isReflectionInProgress(),
                        preHitStats,
                        preHitHeldShield
                ));
    }

    public static void captureBlockingShield(ServerPlayer player, DamageSource source) {
        ReflectionFrame frame = frame(player, source);
        if (frame != null && !frame.suppressed) {
            frame.shield = EquipmentShieldService.blockingContext(player);
        }
    }

    /** Receives only the authoritative stopped portion, NEVER the shield durability cost. */
    public static void recordBlockedDamage(ServerPlayer player, DamageSource source, float stoppedDamage) {
        com.mistaboom.essence_ascendance.attunement.AttunementGameplay.blocked(player, source, stoppedDamage);
        ReflectionFrame frame = frame(player, source);
        if (frame != null && !frame.suppressed && frame.shield != null
                && Float.isFinite(stoppedDamage) && stoppedDamage > 0) {
            frame.blockedDamage = stoppedDamage;
        }
    }

    /** Successful normal completion of the native block path; raising alone never calls this. */
    public static void commitBlock(ServerPlayer player, DamageSource source) {
        ReflectionFrame frame = frame(player, source);
        if (frame != null) frame.blockCompleted = true;
    }

    public static void beginHealthMeasurement(ServerPlayer player, DamageSource source) {
        HEALTH_SAMPLES.computeIfAbsent(player, ignored -> new ArrayDeque<>())
                .push(new HealthSample(source, player.getHealth()));
    }

    public static void endHealthMeasurement(ServerPlayer player) {
        Deque<HealthSample> samples = HEALTH_SAMPLES.get(player);
        if (samples == null || samples.isEmpty()) return;
        HealthSample sample = samples.pop();
        float totalLoss = Math.max(0, sample.healthBefore - player.getHealth());
        float ownLoss = Math.max(0, totalLoss - sample.nestedLoss);
        if (!samples.isEmpty()) samples.peek().nestedLoss += totalLoss;
        else HEALTH_SAMPLES.remove(player);
        reflectAfterDamage(player, sample.source, ownLoss);
    }

    public static void reflectAfterDamage(ServerPlayer victim, DamageSource source, float actualHealthDamage) {
        if (isReflectionInProgress() || !Float.isFinite(actualHealthDamage) || actualHealthDamage <= 0) return;
        ReflectionFrame frame = frame(victim, source);
        DamageStatState stats = frame != null ? frame.preHitStats : evaluateStats(victim);
        boolean hasShield = frame != null
                ? frame.preHitHeldShield
                : EquipmentShieldService.heldContext(victim) != null;
        double ordinary = hasShield || source.isDirect()
                ? ShieldMath.reflectedPortion(actualHealthDamage, stats.damageReflectionPercent()) : 0;
        if (frame != null) {
            if (!frame.suppressed) {
                frame.healthLost += actualHealthDamage;
                frame.ordinaryDamage += ordinary;
                frame.ordinaryPercent = stats.damageReflectionPercent();
            }
        } else {
            // Retain the existing service entrypoint for direct, standalone post-damage callers.
            float reflected = applyReflection(victim, source, ordinary);
            recordDamageDiagnostic(victim, source, actualHealthDamage, stats.damageReflectionPercent(), reflected);
        }
    }

    /**
     * Compatibility entrypoint for integrations that cannot expose the native
     * hurt result. First-party loader hooks use the four-argument overload.
     */
    public static void endDamage(ServerPlayer victim, DamageSource source, boolean returnedNormally) {
        endDamage(victim, source, returnedNormally, true);
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
        if (!returnedNormally) {
            // Discard only this scope's unfinished probe; a nested failure must
            // not erase an enclosing damage measurement for the same player.
            discardHealthMeasurement(victim, source);
            return;
        }
        if (frame.source != source || frame.suppressed) return;

        /*
         * Vanilla returns false for a completely blocked hit even though its
         * authoritative shield-damage operation completed. Treat that commit
         * as success; a false return with no committed block is a rejected or
         * canceled hit and cannot retaliate.
         */
        if (!damageAccepted && !frame.blockCompleted) {
            discardHealthMeasurement(victim, source);
            return;
        }
        float blocked = frame.blockCompleted ? frame.blockedDamage : 0;
        double blockReflection = frame.shield == null ? 0
                : ShieldMath.reflectedPortion(blocked, frame.shield.blockedReflectionPercent());
        // Compute the two disjoint portions separately, then apply one normal defended hit.
        // Two hurt() calls would incorrectly lose one portion to the attacker's invulnerability timer.
        float reflected = applyReflection(victim, source, frame.ordinaryDamage + blockReflection);
        recordDamageDiagnostic(victim, source, frame.healthLost, frame.ordinaryPercent, reflected);
        LAST_REFLECTION.put(victim, new ReflectionEvaluation(blocked, frame.healthLost,
                frame.ordinaryPercent, frame.shield == null ? 0 : frame.shield.nativeReflectionPercent(),
                frame.shield == null ? 0 : frame.shield.investedReflectionPercent(),
                frame.shield == null ? 0 : frame.shield.amplification(),
                frame.ordinaryDamage, blockReflection, reflected));
    }

    private static void discardHealthMeasurement(ServerPlayer victim, DamageSource source) {
        Deque<HealthSample> samples = HEALTH_SAMPLES.get(victim);
        if (samples == null || samples.isEmpty() || samples.peek().source != source) return;

        HealthSample discarded = samples.pop();
        float totalLoss = Math.max(0, discarded.healthBefore - victim.getHealth());
        if (!samples.isEmpty()) {
            // If this was a failed nested call, exclude any health change it
            // managed before unwinding from its enclosing call's reflection.
            samples.peek().nestedLoss += totalLoss;
        } else {
            HEALTH_SAMPLES.remove(victim);
        }
    }

    private static float applyReflection(ServerPlayer victim, DamageSource source, double rawDamage) {
        float reflected = ShieldMath.safeDamage(rawDamage);
        Entity causingEntity = source.getEntity(); // A projectile's owner, not the direct projectile entity.
        if (reflected <= 0 || isReflectionInProgress() || !(causingEntity instanceof LivingEntity attacker)
                || attacker == victim || !attacker.isAlive() || attacker.isRemoved()
                || attacker.level() != victim.level()) return 0;
        if (attacker instanceof Player other && (!victim.server.isPvpAllowed()
                || !victim.canHarmPlayer(other))) return 0;
        if (victim.getTeam() != null && victim.isAlliedTo(attacker)
                && !victim.getTeam().isAllowFriendlyFire()) return 0;
        int previousDepth = REFLECTION_DEPTH.get();
        REFLECTION_DEPTH.set(previousDepth + 1);
        try {
            // Existing thorns-style rules: normal attribution/mitigation, no original projectile payload,
            // no player.attack(), melee/ranged bonuses, true damage or invulnerability reset.
            attacker.hurt(victim.damageSources().thorns(victim), reflected);
        } finally {
            if (previousDepth == 0) REFLECTION_DEPTH.remove();
            else REFLECTION_DEPTH.set(previousDepth);
        }
        return reflected; // Requested defended hit, not a claim about the target's final health loss.
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

    private static final class HealthSample {
        final DamageSource source;
        final float healthBefore;
        float nestedLoss;
        HealthSample(DamageSource source, float healthBefore) {
            this.source = source;
            this.healthBefore = healthBefore;
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
        HEALTH_SAMPLES.remove(player);
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
