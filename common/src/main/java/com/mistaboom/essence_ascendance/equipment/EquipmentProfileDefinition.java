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
 * First-party equipment archetype/profile.
 *
 * Two independent concepts live here:
 *
 * 1. stat applicability, stored per activation context;
 * 2. archetype multipliers applied to tier-derived physical baselines.
 *
 * Keeping applicability context-specific means one future hybrid item can
 * legitimately expose different player stats while HELD versus WORN without
 * reintroducing broad conduit categories.
 */
public final class EquipmentProfileDefinition {

    private final ResourceLocation id;
    private final String displayName;
    private final Map<EquipmentActivationType, Map<ResourceLocation, Double>> statApplicability;
    private final Map<EquipmentBaselineProperty, Double> baselineMultipliers;

    private EquipmentProfileDefinition(
            ResourceLocation id,
            String displayName,
            Map<EquipmentActivationType, Map<ResourceLocation, Double>> statApplicability,
            Map<EquipmentBaselineProperty, Double> baselineMultipliers
    ) {
        this.id = Objects.requireNonNull(id, "Equipment profile ID cannot be null");

        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Equipment profile display name cannot be blank");
        }
        this.displayName = displayName;

        Objects.requireNonNull(statApplicability, "Stat applicability cannot be null");
        Map<EquipmentActivationType, Map<ResourceLocation, Double>> contexts =
                new EnumMap<>(EquipmentActivationType.class);

        for (Map.Entry<EquipmentActivationType, Map<ResourceLocation, Double>> contextEntry :
                statApplicability.entrySet()) {

            EquipmentActivationType activation = Objects.requireNonNull(
                    contextEntry.getKey(),
                    "Equipment activation type cannot be null"
            );

            Map<ResourceLocation, Double> stats = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Double> statEntry :
                    Objects.requireNonNull(
                            contextEntry.getValue(),
                            "Equipment stat applicability map cannot be null"
                    ).entrySet()) {

                ResourceLocation statId = Objects.requireNonNull(
                        statEntry.getKey(),
                        "Stat ID cannot be null"
                );
                double strength = validateNonNegativeFinite(
                        statEntry.getValue(),
                        "Stat applicability for " + statId
                );

                if (strength > 0.0) {
                    stats.put(statId, strength);
                }
            }

            if (!stats.isEmpty()) {
                contexts.put(
                        activation,
                        Collections.unmodifiableMap(stats)
                );
            }
        }
        this.statApplicability = Collections.unmodifiableMap(contexts);

        Objects.requireNonNull(baselineMultipliers, "Baseline multipliers cannot be null");
        Map<EquipmentBaselineProperty, Double> baselineCopy =
                new EnumMap<>(EquipmentBaselineProperty.class);

        for (Map.Entry<EquipmentBaselineProperty, Double> entry : baselineMultipliers.entrySet()) {
            EquipmentBaselineProperty property = Objects.requireNonNull(
                    entry.getKey(),
                    "Baseline property cannot be null"
            );
            double multiplier = validateNonNegativeFinite(
                    entry.getValue(),
                    "Baseline multiplier for " + property
            );

            if (multiplier > 0.0) {
                baselineCopy.put(property, multiplier);
            }
        }
        this.baselineMultipliers = Collections.unmodifiableMap(baselineCopy);
    }

    public ResourceLocation id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Set<EquipmentActivationType> activationTypes() {
        return statApplicability.keySet();
    }

    public Map<EquipmentActivationType, Map<ResourceLocation, Double>> statApplicability() {
        return statApplicability;
    }

    public Map<ResourceLocation, Double> statApplicability(
            EquipmentActivationType activation
    ) {
        return statApplicability.getOrDefault(activation, Map.of());
    }

    public Map<EquipmentBaselineProperty, Double> baselineMultipliers() {
        return baselineMultipliers;
    }

    public double statStrength(
            EquipmentActivationType activation,
            StatDefinition stat
    ) {
        return statStrength(activation, stat.id());
    }

    public double statStrength(
            EquipmentActivationType activation,
            ResourceLocation statId
    ) {
        return statApplicability(activation).getOrDefault(statId, 0.0);
    }

    public double strongestStatStrength(StatDefinition stat) {
        double strongest = 0.0;
        for (EquipmentActivationType activation : activationTypes()) {
            strongest = Math.max(strongest, statStrength(activation, stat));
        }
        return strongest;
    }

    public double baselineMultiplier(EquipmentBaselineProperty property) {
        return baselineMultipliers.getOrDefault(property, 0.0);
    }

    public static Builder builder(ResourceLocation id, String displayName) {
        return new Builder(id, displayName);
    }

    private static double validateNonNegativeFinite(Double value, String name) {
        Objects.requireNonNull(value, name + " cannot be null");
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }

    public static final class Builder {

        private final ResourceLocation id;
        private final String displayName;
        private final Map<EquipmentActivationType, Map<ResourceLocation, Double>> statApplicability =
                new EnumMap<>(EquipmentActivationType.class);
        private final Map<EquipmentBaselineProperty, Double> baselineMultipliers =
                new EnumMap<>(EquipmentBaselineProperty.class);

        private Builder(ResourceLocation id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public Builder stat(
                EquipmentActivationType activation,
                StatDefinition stat
        ) {
            return stat(activation, stat, 1.0);
        }

        public Builder stat(
                EquipmentActivationType activation,
                StatDefinition stat,
                double strength
        ) {
            Objects.requireNonNull(activation, "Equipment activation type cannot be null");
            Objects.requireNonNull(stat, "Stat cannot be null");

            statApplicability
                    .computeIfAbsent(
                            activation,
                            ignored -> new LinkedHashMap<>()
                    )
                    .put(stat.id(), strength);

            return this;
        }

        public Builder baseline(EquipmentBaselineProperty property) {
            return baseline(property, 1.0);
        }

        public Builder baseline(
                EquipmentBaselineProperty property,
                double multiplier
        ) {
            baselineMultipliers.put(property, multiplier);
            return this;
        }

        public EquipmentProfileDefinition build() {
            if (statApplicability.isEmpty()
                    && baselineMultipliers.isEmpty()) {
                throw new IllegalStateException(
                        "Equipment profile requires stat applicability or a baseline property"
                );
            }

            return new EquipmentProfileDefinition(
                    id,
                    displayName,
                    statApplicability,
                    baselineMultipliers
            );
        }
    }
}
