package com.mistaboom.essence_ascendance.balance.engine;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Equipment-independent or transformative capability supplied by registered providers and factual declarations. */
public record CapabilityEvidence(String subjectId, ProgressionBand stage, Map<CapabilityAxis, Double> axes,
                                 boolean reachable, double confidence, String reason) {
    public CapabilityEvidence { axes = Collections.unmodifiableMap(new TreeMap<>(axes)); }
}
