package com.mistaboom.essence_ascendance.balance.engine;

import java.util.List;

public record ResourceEvidence(String itemId, ProgressionBand stage, Availability availability,
                               Automation automation, boolean reachable, boolean external,
                               double economicValue, double confidence, List<AcquisitionSource> sources,
                               List<String> warnings) {
    public ResourceEvidence { sources = List.copyOf(sources); warnings = List.copyOf(warnings); }
}
