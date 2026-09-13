package com.mistaboom.essence_ascendance.balance.engine;

import java.util.List;

/** Unknown production rate is explicit; expectedOutput means per source event, never per second. */
public record AcquisitionSource(String id, Kind kind, ProgressionBand stage, double expectedOutput,
                                boolean renewable, boolean rateKnown, double unitsPerSecond,
                                double confidence, List<String> dependencies, String reason) {
    public AcquisitionSource { dependencies = dependencies.stream().sorted().distinct().toList(); }
    public enum Kind { WORLD_GENERATION, MOB_DROP, FARMING, FISHING, TRADE, LOOT, RECIPE,
        PASSIVE_GENERATION, MACHINE, BYPRODUCT, PLAYER_ACTION, INFINITE_BULK, ADMINISTRATIVE }
}
