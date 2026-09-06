package com.mistaboom.essence_ascendance.config;

import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Provisional native shield balance. Player investments still use the shared stat configuration. */
public record ShieldBalanceSettings(
        double innateReflectionPercent,
        int minimumDisableTicks,
        Map<EquipmentTier, Integer> durability,
        Map<EquipmentTier, Double> blockAmplification
) {
    public ShieldBalanceSettings {
        if (!Double.isFinite(innateReflectionPercent) || innateReflectionPercent <= 0
                || innateReflectionPercent > 1_000_000) {
            throw new IllegalArgumentException("ascendance_shield.innate_reflection_percent must be positive and at most 1000000");
        }
        if (minimumDisableTicks < 1 || minimumDisableTicks > 100) {
            throw new IllegalArgumentException("ascendance_shield.minimum_disable_ticks must be between 1 and 100");
        }
        EnumMap<EquipmentTier, Integer> durabilityCopy = new EnumMap<>(EquipmentTier.class);
        EnumMap<EquipmentTier, Double> amplificationCopy = new EnumMap<>(EquipmentTier.class);
        for (EquipmentTier tier : EquipmentTier.values()) {
            Integer points = durability.get(tier);
            Double multiplier = tier == EquipmentTier.LATENT ? 1.0 : blockAmplification.get(tier);
            if (points == null || points < 1) {
                throw new IllegalArgumentException("Missing/invalid shield durability for " + tier.serializedName());
            }
            if (multiplier == null || !Double.isFinite(multiplier) || multiplier < 1 || multiplier > 1_000_000) {
                throw new IllegalArgumentException("Shield amplification must be between 1 and 1000000 for " + tier.serializedName());
            }
            durabilityCopy.put(tier, points);
            amplificationCopy.put(tier, multiplier);
        }
        durability = Collections.unmodifiableMap(durabilityCopy);
        blockAmplification = Collections.unmodifiableMap(amplificationCopy);
    }

    public static ShieldBalanceSettings defaults() {
        return new ShieldBalanceSettings(20.0, 20,
                Map.of(EquipmentTier.LATENT, 336, EquipmentTier.DORMANT, 672,
                        EquipmentTier.AWAKENED, 1008, EquipmentTier.RESONANT, 1680,
                        EquipmentTier.ASCENDANT, 2688, EquipmentTier.TRANSCENDENT, 4032),
                Map.of(EquipmentTier.LATENT, 1.0, EquipmentTier.DORMANT, 2.0,
                        EquipmentTier.AWAKENED, 3.0, EquipmentTier.RESONANT, 4.0,
                        EquipmentTier.ASCENDANT, 6.0, EquipmentTier.TRANSCENDENT, 8.0));
    }
}
