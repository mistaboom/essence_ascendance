package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

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
