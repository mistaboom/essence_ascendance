package com.mistaboom.essence_ascendance.balance.engine;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record EquipmentReference(String itemId, String slot, ProgressionBand stage,
                                 Map<CapabilityAxis, Double> axes, List<String> capabilities,
                                 boolean reachable, boolean included, double confidence, String reason) {
    public EquipmentReference {
        axes = Collections.unmodifiableMap(new TreeMap<>(axes));
        capabilities = capabilities.stream().sorted().distinct().toList();
    }
}
