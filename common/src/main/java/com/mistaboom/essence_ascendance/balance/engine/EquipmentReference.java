package com.mistaboom.essence_ascendance.balance.engine;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record EquipmentReference(String itemId, String slot, ProgressionBand stage,
                                 Map<CapabilityAxis, Double> axes, List<String> capabilities,
                                 boolean reachable, boolean included, double confidence, String reason,
                                 Map<String, NativeAttributeContribution> attributeModifiers) {
    public static final String ESTIMATED_CATALOG_REFERENCE = "estimated_catalog_reference";

    public boolean usableReference() {
        return included && (reachable || capabilities.contains(ESTIMATED_CATALOG_REFERENCE));
    }

    public EquipmentReference {
        axes = Collections.unmodifiableMap(new TreeMap<>(axes));
        capabilities = capabilities.stream().sorted().distinct().toList();
        attributeModifiers = Collections.unmodifiableMap(new TreeMap<>(java.util.Objects.requireNonNull(attributeModifiers)));
    }

    public EquipmentReference(String itemId, String slot, ProgressionBand stage,
            Map<CapabilityAxis, Double> axes, List<String> capabilities,
            boolean reachable, boolean included, double confidence, String reason) {
        this(itemId, slot, stage, axes, capabilities, reachable, included, confidence, reason, Map.of());
    }
}
