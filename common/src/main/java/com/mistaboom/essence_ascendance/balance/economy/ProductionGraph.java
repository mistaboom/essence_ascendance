package com.mistaboom.essence_ascendance.balance.economy;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Providers may describe machines without exposing optional-mod classes. */
public record ProductionGraph(List<Process> processes, List<String> warnings) {
    private static final com.google.gson.Gson RESOURCE_JSON = new com.google.gson.Gson();
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

    /** Additional native resource flows. Predicate JSON retains exact tag/component restrictions. */
    public record ResourceFlow(String direction, String role, String form, String unit,
                               List<String> alternatives, double count, double probability, String predicate) {
        public ResourceFlow {
            if (!List.of("input", "output").contains(direction)
                    || !List.of("consumed", "catalyst", "durability", "product", "byproduct").contains(role)
                    || form == null || form.isBlank() || unit == null || unit.isBlank()
                    || !Double.isFinite(count) || count <= 0 || !Double.isFinite(probability) || probability < 0 || probability > 1)
                throw new IllegalArgumentException("Invalid native resource flow");
            alternatives = alternatives.stream().sorted().distinct().toList();
            if (alternatives.isEmpty()) throw new IllegalArgumentException("Resource flow needs an identity or predicate");
            com.google.gson.JsonParser.parseString(predicate);
        }
    }

    public record Process(String id, String family, List<Input> inputs, List<Output> outputs,
                          double processingTicks, double externalCost, String provider,
                          double confidence, Map<String, String> metadata) {
        public Process {
            if (id == null || id.isBlank() || !Double.isFinite(processingTicks)
                    || processingTicks < 0 || !Double.isFinite(externalCost) || externalCost < 0
                    || !Double.isFinite(confidence) || confidence < 0 || confidence > 1)
                throw new IllegalArgumentException("Invalid production process");
            inputs = List.copyOf(inputs);
            outputs = outputs.stream().sorted(Comparator.comparing(Output::itemId)
                    .thenComparingDouble(Output::probability)).toList();
            metadata = java.util.Collections.unmodifiableMap(new TreeMap<>(metadata));
            List<ResourceFlow> resources = decodeResources(metadata);
            if (outputs.isEmpty() && resources.stream().noneMatch(flow -> flow.direction().equals("output")))
                throw new IllegalArgumentException("Production needs an item or native resource output");
            for (String key : List.of("acquisition_complete", "conservation_complete", "duration_known"))
                if (metadata.containsKey(key) && !List.of("true", "false").contains(metadata.get(key)))
                    throw new IllegalArgumentException("Invalid production completeness: " + key);
        }

        /** Missing facts remain unknown. Existing material-only processes keep their declared contract. */
        public boolean conservationComplete() { return !"false".equals(metadata.get("conservation_complete")); }
        public boolean acquisitionComplete() { return !"false".equals(metadata.get("acquisition_complete")); }
        public boolean sourceProducer() { return inputs.stream().noneMatch(Input::consumed); }
        public Double duration() { return "true".equals(metadata.get("duration_known")) ? processingTicks : null; }
        public List<ResourceFlow> resources() { return decodeResources(metadata); }
        public com.google.gson.JsonObject effectiveDefinition() {
            String value = metadata.get("effective_definition");
            return value == null ? null : com.google.gson.JsonParser.parseString(value).getAsJsonObject();
        }
    }
    private static List<ResourceFlow> decodeResources(Map<String, String> metadata) {
        String json = metadata.get("native_resources");
        if (json == null) return List.of();
        ResourceFlow[] flows = RESOURCE_JSON.fromJson(json, ResourceFlow[].class);
        return List.of(flows);
    }
}
