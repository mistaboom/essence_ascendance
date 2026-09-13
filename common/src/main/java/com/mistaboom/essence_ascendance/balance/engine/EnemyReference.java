package com.mistaboom.essence_ascendance.balance.engine;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record EnemyReference(String entityId, Encounter encounter, ProgressionBand stage,
                            Map<CapabilityAxis, Double> axes, boolean included, double confidence,
                            List<String> unknownMechanics, String reason) {
    public EnemyReference { axes = Collections.unmodifiableMap(new TreeMap<>(axes)); unknownMechanics = List.copyOf(unknownMechanics); }
    public enum Encounter { ROUTINE, ELITE, BOSS, APEX, UNKNOWN }
}
