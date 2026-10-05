package com.mistaboom.essence_ascendance.balance.engine;

import java.util.List;

/** Unknown production rate is explicit; expectedOutput means per source event, never per second. */
public record AcquisitionSource(String id, Kind kind, ProgressionBand stage, double expectedOutput,
                                boolean renewable, boolean rateKnown, double unitsPerSecond,
                                double confidence, List<String> dependencies, String reason, SourceAvailability availability) {
    /** Existing source families need not fabricate optional availability diagnostics. */
    public AcquisitionSource(String id, Kind kind, ProgressionBand stage, double expectedOutput, boolean renewable,
                             boolean rateKnown, double unitsPerSecond, double confidence, List<String> dependencies, String reason) {
        this(id, kind, stage, expectedOutput, renewable, rateKnown, unitsPerSecond, confidence, dependencies, reason, null);
    }
    public AcquisitionSource {
        dependencies = dependencies.stream().sorted().distinct().toList();
        if (availability != null && (!id.equals(availability.underlyingSource()) || expectedOutput != availability.expectedPerEvent()))
            throw new IllegalArgumentException("Source availability identity/count differs from acquisition opportunity");
    }
    public enum Kind { WORLD_GENERATION, MOB_DROP, FARMING, FISHING, TRADE, LOOT, RECIPE,
        PASSIVE_GENERATION, MACHINE, BYPRODUCT, PLAYER_ACTION, INFINITE_BULK, ADMINISTRATIVE, QUEST_REWARD }
}
