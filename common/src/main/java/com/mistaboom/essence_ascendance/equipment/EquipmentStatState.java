package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.stat.StatDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/*
 * Context-resolved stat applicability.
 *
 * Provider merging uses MAX before this state is produced. Worn armor pieces
 * are then combined by weighted addition, while unrelated active contexts
 * (for example worn armor plus held item) are merged with MAX to avoid
 * double-dipping the same player bonus.
 */
public final class EquipmentStatState {

    private final Map<ResourceLocation, Double> strengths;

    public EquipmentStatState(Map<ResourceLocation, Double> strengths) {
        Objects.requireNonNull(strengths, "Resolved equipment stat strengths cannot be null");

        Map<ResourceLocation, Double> copy = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Double> entry : strengths.entrySet()) {
            ResourceLocation statId = Objects.requireNonNull(entry.getKey());
            double strength = Objects.requireNonNull(entry.getValue());
            if (!Double.isFinite(strength) || strength < 0.0) {
                throw new IllegalArgumentException(
                        "Resolved stat strength for " + statId
                                + " must be finite and non-negative"
                );
            }
            if (strength > 0.0) {
                copy.put(statId, strength);
            }
        }
        this.strengths = Collections.unmodifiableMap(copy);
    }

    public static EquipmentStatState none() {
        return new EquipmentStatState(Map.of());
    }

    public Map<ResourceLocation, Double> values() {
        return strengths;
    }

    public double strength(StatDefinition stat) {
        return strength(stat.id());
    }

    public double strength(ResourceLocation statId) {
        return strengths.getOrDefault(statId, 0.0);
    }

    public EquipmentStatState mergeMax(EquipmentStatState other) {
        Map<ResourceLocation, Double> merged = new LinkedHashMap<>(strengths);
        for (Map.Entry<ResourceLocation, Double> entry : other.strengths.entrySet()) {
            merged.merge(entry.getKey(), entry.getValue(), Math::max);
        }
        return new EquipmentStatState(merged);
    }

    public EquipmentStatState addScaled(
            Map<ResourceLocation, Double> additions,
            double scale
    ) {
        if (!Double.isFinite(scale) || scale < 0.0) {
            throw new IllegalArgumentException("Scale must be finite and non-negative");
        }

        Map<ResourceLocation, Double> merged = new LinkedHashMap<>(strengths);
        for (Map.Entry<ResourceLocation, Double> entry : additions.entrySet()) {
            double contribution = entry.getValue() * scale;
            if (contribution > 0.0) {
                merged.merge(entry.getKey(), contribution, Double::sum);
            }
        }
        return new EquipmentStatState(merged);
    }
}
