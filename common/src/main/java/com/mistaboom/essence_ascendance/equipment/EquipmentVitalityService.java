package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodData;

import java.lang.StackWalker.StackFrame;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/*
 * Server-authoritative application layer for Ascendance vitality mechanics
 * that do not map cleanly to ordinary entity attributes.
 *
 * This service intentionally creates NO MobEffect instances.
 *
 * Semantics:
 *
 *   Health Regeneration
 *     Passive healing from worn Ascendance armor. It uses the normal heal()
 *     path so other mods can still observe healing, but a recursion/source
 *     guard prevents Healing Effectiveness from multiplying this stat.
 *
 *   Healing Effectiveness
 *     Multiplies external/discrete healing. Vanilla hunger-based natural
 *     regeneration and this service's own passive regeneration are excluded.
 *
 *   Hunger Efficiency
 *     Reduces exhaustion at the point where Player.causeFoodExhaustion(float)
 *     receives it. A 20% bonus therefore produces exactly 80% of normal
 *     exhaustion instead of attempting to refund food/saturation afterward.
 *
 *   Breath Hold
 *     Refunds a fraction of actual underwater air loss. Because it operates on
 *     observed air loss, vanilla mechanics that already prevent/reduce air loss
 *     remain meaningful.
 *
 *   Status Resistance
 *     Observes the server's authoritative active-effect list and shortens each
 *     newly active or refreshed non-beneficial finite timed effect exactly once. A 30%
 *     resistance turns a 20-second effect into 14 seconds. After changing the authoritative active instance, the service sends
 *     Minecraft's ordinary effect-update packet so the client HUD timer matches
 *     the server immediately.
 */
public final class EquipmentVitalityService {

    private static final double EPSILON = 0.0000001;
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double HEALTH_POINTS_PER_HEART = 2.0;

    private static final Map<ServerPlayer, RuntimeState> RUNTIME =
            new WeakHashMap<>();

    private static final ThreadLocal<Integer> INTERNAL_REGEN_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    private static final StackWalker STACK_WALKER =
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private EquipmentVitalityService() {
    }

    public static void tick(ServerPlayer player) {
        VitalityStatState stats = evaluateStats(player);
        RuntimeState runtime = state(player);

        applyPassiveRegeneration(player, stats, runtime);
        applyBreathHold(player, stats, runtime);
        applyStatusResistance(player, stats, runtime);
    }

    public static VitalityStatState evaluateStats(ServerPlayer player) {
        PlayerEssenceData playerData =
                EssenceSavedData
                        .get(player.server)
                        .getPlayerData(player.getUUID());

        EquipmentStatState worn =
                EquipmentStatResolver.evaluateWornArmor(player);

        return new VitalityStatState(
                value(playerData, worn, EssenceStats.HEALTH_REGENERATION),
                value(playerData, worn, EssenceStats.HEALING_EFFECTIVENESS),
                value(playerData, worn, EssenceStats.HUNGER_EFFICIENCY),
                value(playerData, worn, EssenceStats.BREATH_HOLD),
                value(playerData, worn, EssenceStats.STATUS_RESISTANCE)
        );
    }

    /*
     * Loader bridges call this from LivingEntity.heal(float).
     *
     * Fabric reaches it through a tiny mixin; NeoForge reaches it through
     * LivingHealEvent. The common service owns all actual stat math.
     */
    public static float modifyExternalHealing(
            ServerPlayer player,
            float requestedHealing
    ) {
        if (!Float.isFinite(requestedHealing) || requestedHealing <= 0.0F) {
            return requestedHealing;
        }

        VitalityStatState stats = evaluateStats(player);
        HealingBypassReason bypassReason = healingBypassReason();

        double healingPercent = Math.max(
                0.0,
                stats.healingEffectivenessPercent()
        );

        float resolvedHealing = requestedHealing;

        if (bypassReason == HealingBypassReason.NONE
                && healingPercent > EPSILON) {

            double resolved =
                    requestedHealing
                            * (1.0 + healingPercent / 100.0);

            resolvedHealing = finiteFloat(resolved);
        }

        if (bypassReason
                != HealingBypassReason.ASCENDANCE_REGENERATION) {
            state(player).lastHealing =
                    new HealingEvaluation(
                            requestedHealing,
                            resolvedHealing,
                            healingPercent,
                            bypassReason
                    );
        }

        return resolvedHealing;
    }

    /*
     * Loader mixins call this at Player.causeFoodExhaustion(float).
     *
     * This is intentionally NOT a food/saturation refund. Scaling exhaustion at
     * its source makes the stat linear, predictable, and independent of how
     * frequently FoodData happens to convert exhaustion into saturation/food.
     */
    public static float modifyFoodExhaustion(
            ServerPlayer player,
            float requestedExhaustion
    ) {
        if (!Float.isFinite(requestedExhaustion)
                || requestedExhaustion <= 0.0F) {
            return requestedExhaustion;
        }

        VitalityStatState stats = evaluateStats(player);

        double efficiencyFraction =
                clamp(stats.hungerEfficiencyPercent(), 0.0, 100.0)
                        / 100.0;

        float resolvedExhaustion = finiteFloat(
                requestedExhaustion
                        * (1.0 - efficiencyFraction)
        );

        state(player).lastExhaustion =
                new ExhaustionEvaluation(
                        requestedExhaustion,
                        resolvedExhaustion,
                        efficiencyFraction * 100.0
                );

        return resolvedExhaustion;
    }

    /*
     * Status Resistance operates on the authoritative instance stored in
     * LivingEntity#getActiveEffectsMap(). Loader mixins add
     * MobEffectDurationAccess to MobEffectInstance so we can write the real
     * private duration field rather than relying on mapDuration().
     *
     * Each newly active/refreshed finite non-beneficial effect is shortened
     * exactly once. A 30% resistance turns 400 ticks into 280 ticks. The
     * resulting duration is immediately verified by reading getDuration() back
     * from the same authoritative object before we update diagnostics or send
     * a client packet.
     */
    private static void applyStatusResistance(
            ServerPlayer player,
            VitalityStatState stats,
            RuntimeState runtime
    ) {
        double resistanceFraction =
                clamp(stats.statusResistancePercent(), 0.0, 100.0)
                        / 100.0;

        Set<Holder<MobEffect>> activeResistible = new HashSet<>();

        for (Map.Entry<Holder<MobEffect>, MobEffectInstance> entry :
                new ArrayList<>(player.getActiveEffectsMap().entrySet())) {

            Holder<MobEffect> effect = entry.getKey();
            MobEffectInstance instance = entry.getValue();

            if (instance == null
                    || effect.value().isBeneficial()
                    || instance.isInfiniteDuration()
                    || instance.getDuration() <= 0
                    || effect.value().isInstantenous()) {
                runtime.statusTrackers.remove(effect);
                continue;
            }

            activeResistible.add(effect);

            int currentDuration = instance.getDuration();
            int currentAmplifier = instance.getAmplifier();

            StatusTracker tracker = runtime.statusTrackers.get(effect);

            boolean firstObservation = tracker == null;
            boolean durationRefreshed =
                    tracker != null
                            && currentDuration > tracker.lastDurationTicks() + 1;
            boolean strongerReplacement =
                    tracker != null
                            && currentAmplifier > tracker.amplifier();

            boolean shouldProcess =
                    resistanceFraction > EPSILON
                            && (firstObservation
                            || durationRefreshed
                            || strongerReplacement);

            if (shouldProcess) {
                int originalDuration = currentDuration;

                int targetDuration;
                if (resistanceFraction >= 1.0 - EPSILON) {
                    targetDuration = 1;
                } else {
                    targetDuration = Math.max(
                            1,
                            (int) Math.ceil(
                                    originalDuration
                                            * (1.0 - resistanceFraction)
                            )
                    );
                }

                if (targetDuration < originalDuration) {
                    if (!(instance instanceof MobEffectDurationAccess access)) {
                        throw new IllegalStateException(
                                "MobEffectInstance duration bridge was not applied. "
                                        + "Check the loader vitality mixin configuration."
                        );
                    }

                    access.essenceAscendance$setDurationTicks(
                            targetDuration
                    );

                    /*
                     * Do not trust the requested target. Re-read the actual
                     * server object so the debug command can never report a
                     * successful shortening that did not really happen.
                     */
                    currentDuration = instance.getDuration();

                    if (currentDuration != targetDuration) {
                        throw new IllegalStateException(
                                "Failed to set MobEffectInstance duration for "
                                        + instance.getDescriptionId()
                                        + ": requested "
                                        + targetDuration
                                        + " ticks but server instance reports "
                                        + currentDuration
                    );
                    }

                    player.connection.send(
                            new ClientboundUpdateMobEffectPacket(
                                    player.getId(),
                                    instance,
                                    false
                            )
                    );

                    com.mistaboom.essence_ascendance.attunement.AttunementGameplay.effectShortened(
                            player, instance, originalDuration, currentDuration);

                    runtime.lastStatusEffect =
                            new StatusEffectEvaluation(
                                    instance.getDescriptionId(),
                                    originalDuration,
                                    currentDuration,
                                    resistanceFraction * 100.0
                            );
                }
            }

            runtime.statusTrackers.put(
                    effect,
                    new StatusTracker(
                            currentDuration,
                            currentAmplifier
                    )
            );
        }

        runtime.statusTrackers
                .keySet()
                .removeIf(effect -> !activeResistible.contains(effect));
    }

    public static Optional<HealingEvaluation> lastHealing(
            ServerPlayer player
    ) {
        RuntimeState runtime = RUNTIME.get(player);
        if (runtime == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(runtime.lastHealing);
    }

    public static Optional<ExhaustionEvaluation> lastExhaustion(
            ServerPlayer player
    ) {
        RuntimeState runtime = RUNTIME.get(player);
        if (runtime == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(runtime.lastExhaustion);
    }

    public static Optional<StatusEffectEvaluation> lastStatusEffect(
            ServerPlayer player
    ) {
        RuntimeState runtime = RUNTIME.get(player);
        if (runtime == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(runtime.lastStatusEffect);
    }

    public static VitalityRuntimeSnapshot runtimeSnapshot(
            ServerPlayer player
    ) {
        RuntimeState runtime = state(player);
        FoodData foodData = player.getFoodData();

        int maxAir = Math.max(1, player.getMaxAirSupply());
        double vanillaBreathSeconds = maxAir / TICKS_PER_SECOND;
        VitalityStatState stats = evaluateStats(player);

        return new VitalityRuntimeSnapshot(
                stats,
                player.getHealth(),
                player.getMaxHealth(),
                foodData.getFoodLevel(),
                foodData.getSaturationLevel(),
                foodData.getExhaustionLevel(),
                player.getAirSupply(),
                maxAir,
                vanillaBreathSeconds + Math.max(0.0, stats.breathHoldSeconds()),
                runtime.lastPassiveRegenHealthPoints,
                runtime.lastAirRefund
        );
    }

    public static void forget(ServerPlayer player) {
        RUNTIME.remove(player);
    }

    private static void applyPassiveRegeneration(
            ServerPlayer player,
            VitalityStatState stats,
            RuntimeState runtime
    ) {
        if (!player.isAlive()
                || player.getHealth() >= player.getMaxHealth()) {
            return;
        }

        double heartsPerSecond = Math.max(
                0.0,
                stats.healthRegenerationHeartsPerSecond()
        );

        if (heartsPerSecond <= EPSILON) {
            return;
        }

        float healthPointsThisTick = finiteFloat(
                heartsPerSecond
                        * HEALTH_POINTS_PER_HEART
                        / TICKS_PER_SECOND
        );

        if (healthPointsThisTick <= 0.0F) {
            return;
        }

        int previousDepth = INTERNAL_REGEN_DEPTH.get();
        INTERNAL_REGEN_DEPTH.set(previousDepth + 1);

        try {
            float before = player.getHealth();
            player.heal(healthPointsThisTick);
            runtime.lastPassiveRegenHealthPoints =
                    Math.max(0.0F, player.getHealth() - before);
        } finally {
            if (previousDepth == 0) {
                INTERNAL_REGEN_DEPTH.remove();
            } else {
                INTERNAL_REGEN_DEPTH.set(previousDepth);
            }
        }
    }

    private static void applyBreathHold(
            ServerPlayer player,
            VitalityStatState stats,
            RuntimeState runtime
    ) {
        int currentAir = player.getAirSupply();

        if (!runtime.airInitialized) {
            runtime.lastAirSupply = currentAir;
            runtime.airInitialized = true;
            return;
        }

        double bonusSeconds = Math.max(
                0.0,
                stats.breathHoldSeconds()
        );

        if (!player.isUnderWater()
                || bonusSeconds <= EPSILON) {
            runtime.breathRefundAccumulator = 0.0;
            runtime.lastAirSupply = currentAir;
            return;
        }

        int airLoss = Math.max(
                0,
                runtime.lastAirSupply - currentAir
        );

        if (airLoss <= 0) {
            runtime.lastAirSupply = currentAir;
            return;
        }

        int maxAir = Math.max(1, player.getMaxAirSupply());
        double vanillaSeconds = maxAir / TICKS_PER_SECOND;

        double refundFraction =
                bonusSeconds
                        / (vanillaSeconds + bonusSeconds);

        runtime.breathRefundAccumulator +=
                airLoss * refundFraction;

        int wholeRefund =
                (int) Math.floor(runtime.breathRefundAccumulator);

        if (wholeRefund > 0) {
            int restoredAir = Math.min(
                    maxAir,
                    currentAir + wholeRefund
            );

            int actualRefund = Math.max(
                    0,
                    restoredAir - currentAir
            );

            if (actualRefund > 0) {
                player.setAirSupply(restoredAir);
                currentAir = restoredAir;
                runtime.breathRefundAccumulator -= actualRefund;
                runtime.lastAirRefund = actualRefund;
            }
        }

        runtime.lastAirSupply = currentAir;
    }

    private static HealingBypassReason healingBypassReason() {
        if (INTERNAL_REGEN_DEPTH.get() > 0) {
            return HealingBypassReason.ASCENDANCE_REGENERATION;
        }

        if (isVanillaFoodHealingCall()) {
            return HealingBypassReason.NATURAL_HUNGER_REGENERATION;
        }

        return HealingBypassReason.NONE;
    }

    private static boolean isVanillaFoodHealingCall() {
        return STACK_WALKER.walk(
                frames -> frames.anyMatch(
                        EquipmentVitalityService::isFoodDataFrame
                )
        );
    }

    private static boolean isFoodDataFrame(StackFrame frame) {
        return frame.getDeclaringClass() == FoodData.class;
    }

    private static double value(
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

    private static RuntimeState state(ServerPlayer player) {
        return RUNTIME.computeIfAbsent(
                player,
                ignored -> new RuntimeState()
        );
    }

    private static float finiteFloat(double value) {
        if (!Double.isFinite(value)) {
            return Float.MAX_VALUE;
        }

        if (value <= 0.0) {
            return 0.0F;
        }

        return (float) Math.min(
                value,
                Float.MAX_VALUE
        );
    }

    private static double clamp(
            double value,
            double min,
            double max
    ) {
        return Math.max(min, Math.min(max, value));
    }

    public enum HealingBypassReason {
        NONE,
        ASCENDANCE_REGENERATION,
        NATURAL_HUNGER_REGENERATION
    }

    public record VitalityStatState(
            double healthRegenerationHeartsPerSecond,
            double healingEffectivenessPercent,
            double hungerEfficiencyPercent,
            double breathHoldSeconds,
            double statusResistancePercent
    ) {
    }

    public record HealingEvaluation(
            float requestedHealing,
            float resolvedHealing,
            double healingEffectivenessPercent,
            HealingBypassReason bypassReason
    ) {
    }

    public record ExhaustionEvaluation(
            float requestedExhaustion,
            float resolvedExhaustion,
            double hungerEfficiencyPercent
    ) {
    }

    public record StatusEffectEvaluation(
            String effectDescriptionId,
            int originalDurationTicks,
            int resolvedDurationTicks,
            double statusResistancePercent
    ) {
    }

    public record VitalityRuntimeSnapshot(
            VitalityStatState stats,
            float health,
            float maxHealth,
            int foodLevel,
            float saturationLevel,
            float exhaustionLevel,
            int airSupply,
            int maxAirSupply,
            double estimatedTotalBreathSeconds,
            float lastPassiveRegenHealthPoints,
            int lastAirRefund
    ) {
    }

    private record StatusTracker(
            int lastDurationTicks,
            int amplifier
    ) {
    }

    private static final class RuntimeState {
        private boolean airInitialized = false;
        private int lastAirSupply = 0;
        private double breathRefundAccumulator = 0.0;

        private final Map<Holder<MobEffect>, StatusTracker> statusTrackers =
                new java.util.HashMap<>();

        private HealingEvaluation lastHealing = null;
        private ExhaustionEvaluation lastExhaustion = null;
        private StatusEffectEvaluation lastStatusEffect = null;

        private float lastPassiveRegenHealthPoints = 0.0F;
        private int lastAirRefund = 0;
    }
}
