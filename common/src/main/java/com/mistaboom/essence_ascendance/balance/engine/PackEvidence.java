package com.mistaboom.essence_ascendance.balance.engine;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Generation-only result. Contains no live Minecraft objects and can be serialized deterministically. */
public record PackEvidence(Map<String, ResourceEvidence> resources, List<EquipmentReference> equipment,
                           List<EnemyReference> enemies, Map<ProgressionBand, Map<CapabilityAxis, Double>> frontiers,
                           List<EvidenceFact> facts, List<String> warnings, Map<String, Long> graphSummary,
                           List<CapabilityEvidence> capabilities) {
    public PackEvidence {
        resources = Collections.unmodifiableMap(new TreeMap<>(resources));
        equipment = List.copyOf(equipment); enemies = List.copyOf(enemies); facts = List.copyOf(facts);
        capabilities = List.copyOf(capabilities);
        warnings = warnings.stream().sorted().distinct().toList(); graphSummary = Collections.unmodifiableMap(new TreeMap<>(graphSummary));
        Map<ProgressionBand, Map<CapabilityAxis, Double>> copied = new TreeMap<>();
        frontiers.forEach((band, axes) -> copied.put(band, Collections.unmodifiableMap(new TreeMap<>(axes))));
        frontiers = Collections.unmodifiableMap(copied);
    }
    public PackEvidence(Map<String, ResourceEvidence> resources, List<EquipmentReference> equipment,
                        List<EnemyReference> enemies, Map<ProgressionBand, Map<CapabilityAxis, Double>> frontiers,
                        List<EvidenceFact> facts, List<String> warnings, Map<String, Long> graphSummary) {
        this(resources, equipment, enemies, frontiers, facts, warnings, graphSummary, List.of());
    }
    public double reference(ProgressionBand band, CapabilityAxis axis, double fallback) {
        return frontiers.getOrDefault(band, Map.of()).getOrDefault(axis, fallback);
    }
}
