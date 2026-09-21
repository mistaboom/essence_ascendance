package com.mistaboom.essence_ascendance.config;

import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Native shield-tier balance. Player investments still use the shared stat configuration. */
public record ShieldBalanceSettings(
        double baseReflectionPercent,
        int minimumDisableTicks,
        Map<EquipmentTier, Integer> durability,
        Map<EquipmentTier, Double> innateReflectionBonus,
        Map<EquipmentTier, Double> blockAmplification
) {
    public ShieldBalanceSettings {
        if (!Double.isFinite(baseReflectionPercent) || baseReflectionPercent < 0
                || baseReflectionPercent > 1_000_000) {
            throw new IllegalArgumentException("ascendance_shield.base_reflection_percent must be between 0 and 1000000");
        }
        if (minimumDisableTicks < 1 || minimumDisableTicks > 100) {
            throw new IllegalArgumentException("ascendance_shield.minimum_disable_ticks must be between 1 and 100");
        }
        Objects.requireNonNull(durability, "Shield durability map cannot be null");
        Objects.requireNonNull(innateReflectionBonus, "Shield innate reflection bonus map cannot be null");
        Objects.requireNonNull(blockAmplification, "Shield amplification map cannot be null");
        EnumMap<EquipmentTier, Integer> durabilityCopy = new EnumMap<>(EquipmentTier.class);
        EnumMap<EquipmentTier, Double> reflectionBonusCopy = new EnumMap<>(EquipmentTier.class);
        EnumMap<EquipmentTier, Double> amplificationCopy = new EnumMap<>(EquipmentTier.class);
        for (EquipmentTier tier : EquipmentTier.values()) {
            Integer points = durability.get(tier);
            Double reflectionBonus = innateReflectionBonus.get(tier);
            Double multiplier = tier == EquipmentTier.LATENT ? 1.0 : blockAmplification.get(tier);
            if (points == null || points < 1) {
                throw new IllegalArgumentException("Missing/invalid shield durability for " + tier.serializedName());
            }
            if (reflectionBonus == null || !Double.isFinite(reflectionBonus)
                    || reflectionBonus < 0 || reflectionBonus > 1_000_000) {
                throw new IllegalArgumentException("Shield innate reflection bonus must be between 0 and 1000000 for " + tier.serializedName());
            }
            if (multiplier == null || !Double.isFinite(multiplier) || multiplier < 1 || multiplier > 1_000_000) {
                throw new IllegalArgumentException("Shield amplification must be between 1 and 1000000 for " + tier.serializedName());
            }
            durabilityCopy.put(tier, points);
            reflectionBonusCopy.put(tier, reflectionBonus);
            amplificationCopy.put(tier, multiplier);
        }
        if (baseReflectionPercent == 0 && reflectionBonusCopy.values().stream().noneMatch(value -> value > 0))
            throw new IllegalArgumentException("Reflection skills require a positive native shield reflection route before bonus investment");
        durability = Collections.unmodifiableMap(durabilityCopy);
        innateReflectionBonus = Collections.unmodifiableMap(reflectionBonusCopy);
        blockAmplification = Collections.unmodifiableMap(amplificationCopy);
    }

    public double nativeReflectionPercent(EquipmentTier tier) {
        return baseReflectionPercent + innateReflectionBonus.get(tier);
    }

    public static ShieldBalanceSettings defaults() {
        return new ShieldBalanceSettings(10.0, 20,
                Map.of(EquipmentTier.LATENT, 336, EquipmentTier.DORMANT, 672,
                        EquipmentTier.AWAKENED, 1008, EquipmentTier.RESONANT, 1680,
                        EquipmentTier.ASCENDANT, 2688, EquipmentTier.TRANSCENDENT, 4032),
                Map.of(EquipmentTier.LATENT, 0.0, EquipmentTier.DORMANT, 1.0,
                        EquipmentTier.AWAKENED, 2.0, EquipmentTier.RESONANT, 3.0,
                        EquipmentTier.ASCENDANT, 4.0, EquipmentTier.TRANSCENDENT, 5.0),
                Map.of(EquipmentTier.LATENT, 1.0, EquipmentTier.DORMANT, 1.5,
                        EquipmentTier.AWAKENED, 2.0, EquipmentTier.RESONANT, 3.0,
                        EquipmentTier.ASCENDANT, 4.0, EquipmentTier.TRANSCENDENT, 5.0));
    }
}
