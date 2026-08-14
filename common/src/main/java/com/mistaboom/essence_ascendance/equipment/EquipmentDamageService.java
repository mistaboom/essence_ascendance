package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

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
 * normal armor/enchantment/absorption pipeline. It is limited to direct living
 * attackers and uses a recursion guard so reflection can never reflect itself.
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
         * Reflected damage is intentionally not fed back through Ascendance
         * resistance/reflection. Vanilla armor and other normal mechanics may
         * still mitigate the thorns-style reflected hit.
         */
        if (isReflectionInProgress()) {
            return incomingDamage;
        }

        DamageStatState stats = evaluateStats(player);
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

    public static void reflectAfterDamage(
            ServerPlayer victim,
            DamageSource source,
            float actualHealthDamage
    ) {
        if (isReflectionInProgress()) {
            return;
        }

        DamageStatState stats = evaluateStats(victim);
        float reflectedDamage = 0.0F;

        if (Float.isFinite(actualHealthDamage)
                && actualHealthDamage > 0.0F
                && stats.damageReflectionPercent() > EPSILON
                && source.isDirect()) {

            Entity causingEntity = source.getEntity();

            if (causingEntity instanceof LivingEntity attacker
                    && attacker != victim
                    && attacker.isAlive()) {

                reflectedDamage = (float) Math.max(
                        0.0,
                        actualHealthDamage
                                * stats.damageReflectionPercent()
                                / 100.0
                );

                if (reflectedDamage > 0.0F) {
                    int previousDepth = REFLECTION_DEPTH.get();
                    REFLECTION_DEPTH.set(previousDepth + 1);

                    try {
                        attacker.hurt(
                                victim.damageSources().thorns(victim),
                                reflectedDamage
                        );
                    } finally {
                        if (previousDepth == 0) {
                            REFLECTION_DEPTH.remove();
                        } else {
                            REFLECTION_DEPTH.set(previousDepth);
                        }
                    }
                }
            }
        }

        DamageEvaluation incoming = LAST_DAMAGE.get(victim);

        if (incoming == null) {
            incoming = new DamageEvaluation(
                    classify(source),
                    -1.0F,
                    -1.0F,
                    0.0,
                    -1.0F,
                    stats.damageReflectionPercent(),
                    0.0F
            );
        }

        LAST_DAMAGE.put(
                victim,
                new DamageEvaluation(
                        incoming.category(),
                        incoming.incomingDamage(),
                        incoming.resolvedIncomingDamage(),
                        incoming.resistancePercent(),
                        Math.max(0.0F, actualHealthDamage),
                        stats.damageReflectionPercent(),
                        reflectedDamage
                )
        );
    }

    public static DamageStatState evaluateStats(ServerPlayer player) {
        PlayerEssenceData playerData =
                EssenceSavedData
                        .get(player.server)
                        .getPlayerData(player.getUUID());

        EquipmentStatState worn =
                EquipmentStatResolver.evaluateWornArmor(player);

        return new DamageStatState(
                percent(playerData, worn, EssenceStats.MELEE_RESISTANCE),
                percent(playerData, worn, EssenceStats.RANGED_RESISTANCE),
                percent(playerData, worn, EssenceStats.MAGIC_RESISTANCE),
                percent(playerData, worn, EssenceStats.FALL_RESISTANCE),
                percent(playerData, worn, EssenceStats.FIRE_RESISTANCE),
                percent(playerData, worn, EssenceStats.EXPLOSION_RESISTANCE),
                percent(playerData, worn, EssenceStats.DAMAGE_REFLECTION)
        );
    }

    public static Optional<DamageEvaluation> lastDamage(ServerPlayer player) {
        return Optional.ofNullable(LAST_DAMAGE.get(player));
    }

    public static void forget(ServerPlayer player) {
        LAST_DAMAGE.remove(player);
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
