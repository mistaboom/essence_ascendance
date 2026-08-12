package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.stat.StatDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/*
 * Provider-level ItemStack capability description.
 *
 * Strengths are stored per activation context so an armor item may expose
 * WORN stats without those stats becoming active merely because the item is
 * held in a hand.
 */
public final class EquipmentStatProfile {

    private final Map<EquipmentActivationType, Map<ResourceLocation, Double>> strengths;

    public EquipmentStatProfile(
            Map<EquipmentActivationType, Map<ResourceLocation, Double>> strengths
    ) {
        Objects.requireNonNull(strengths, "Equipment stat strengths cannot be null");

        Map<EquipmentActivationType, Map<ResourceLocation, Double>> copy =
                new EnumMap<>(EquipmentActivationType.class);

        for (Map.Entry<EquipmentActivationType, Map<ResourceLocation, Double>> contextEntry :
                strengths.entrySet()) {

            EquipmentActivationType activation = Objects.requireNonNull(
                    contextEntry.getKey(),
                    "Equipment activation type cannot be null"
            );

            Map<ResourceLocation, Double> statCopy = new LinkedHashMap<>();

            for (Map.Entry<ResourceLocation, Double> statEntry :
                    Objects.requireNonNull(
                            contextEntry.getValue(),
                            "Equipment stat map cannot be null"
                    ).entrySet()) {

                ResourceLocation statId = Objects.requireNonNull(
                        statEntry.getKey(),
                        "Equipment stat ID cannot be null"
                );

                Double strengthObject = Objects.requireNonNull(
                        statEntry.getValue(),
                        "Equipment stat strength cannot be null"
                );

                double strength = strengthObject;

                if (!Double.isFinite(strength) || strength < 0.0) {
                    throw new IllegalArgumentException(
                            "Equipment stat strength for " + statId
                                    + " must be finite and non-negative"
                    );
                }

                if (strength > 0.0) {
                    statCopy.put(statId, strength);
                }
            }

            if (!statCopy.isEmpty()) {
                copy.put(
                        activation,
                        Collections.unmodifiableMap(statCopy)
                );
            }
        }

        this.strengths = Collections.unmodifiableMap(copy);
    }

    public static EquipmentStatProfile none() {
        return new EquipmentStatProfile(Map.of());
    }

    public static EquipmentStatProfile fromDefinition(
            EquipmentProfileDefinition definition
    ) {
        Objects.requireNonNull(definition, "Equipment profile definition cannot be null");
        return new EquipmentStatProfile(
                definition.statApplicability()
        );
    }

    public Set<EquipmentActivationType> activations() {
        return strengths.keySet();
    }

    public Map<ResourceLocation, Double> strengths(
            EquipmentActivationType activation
    ) {
        return strengths.getOrDefault(activation, Map.of());
    }

    public double strength(
            EquipmentActivationType activation,
            StatDefinition stat
    ) {
        return strength(activation, stat.id());
    }

    public double strength(
            EquipmentActivationType activation,
            ResourceLocation statId
    ) {
        return strengths(activation).getOrDefault(statId, 0.0);
    }

    public EquipmentStatProfile mergeMax(EquipmentStatProfile other) {
        Objects.requireNonNull(other, "Other equipment stat profile cannot be null");

        Map<EquipmentActivationType, Map<ResourceLocation, Double>> merged =
                new EnumMap<>(EquipmentActivationType.class);

        for (EquipmentActivationType activation : EquipmentActivationType.values()) {
            Map<ResourceLocation, Double> context = new LinkedHashMap<>(
                    strengths(activation)
            );

            for (Map.Entry<ResourceLocation, Double> entry :
                    other.strengths(activation).entrySet()) {
                context.merge(
                        entry.getKey(),
                        entry.getValue(),
                        Math::max
                );
            }

            if (!context.isEmpty()) {
                merged.put(activation, context);
            }
        }

        return new EquipmentStatProfile(merged);
    }
}
