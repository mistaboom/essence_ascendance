package com.mistaboom.essence_ascendance.balance.economy;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Providers may describe machines without exposing optional-mod classes. */
public record ProductionGraph(List<Process> processes, List<String> warnings) {
    public ProductionGraph {
        processes = processes.stream().sorted(Comparator.comparing(Process::id)).toList();
        if (processes.stream().map(Process::id).distinct().count() != processes.size())
            throw new IllegalArgumentException("Duplicate production process IDs");
        warnings = warnings.stream().sorted().distinct().toList();
    }

    public record Input(List<String> alternatives, double count, boolean consumed) {
        public Input {
            alternatives = alternatives.stream().sorted().distinct().toList();
            if (alternatives.isEmpty() || !Double.isFinite(count) || count <= 0)
                throw new IllegalArgumentException("Invalid production input");
        }
    }

    public record Output(String itemId, double count, double probability, boolean byproduct) {
        public Output {
            if (itemId == null || itemId.isBlank() || !Double.isFinite(count) || count <= 0
                    || !Double.isFinite(probability) || probability <= 0 || probability > 1)
                throw new IllegalArgumentException("Invalid production output");
        }
        public double expectedCount() { return count * probability; }
    }

    public record Process(String id, String family, List<Input> inputs, List<Output> outputs,
                          double processingTicks, double externalCost, String provider,
                          double confidence, Map<String, String> metadata) {
        public Process {
            if (id == null || id.isBlank() || outputs.isEmpty() || !Double.isFinite(processingTicks)
                    || processingTicks < 0 || !Double.isFinite(externalCost) || externalCost < 0
                    || !Double.isFinite(confidence) || confidence < 0 || confidence > 1)
                throw new IllegalArgumentException("Invalid production process");
            inputs = List.copyOf(inputs);
            outputs = outputs.stream().sorted(Comparator.comparing(Output::itemId)
                    .thenComparingDouble(Output::probability)).toList();
            metadata = java.util.Collections.unmodifiableMap(new TreeMap<>(metadata));
        }
    }
}
