package com.mistaboom.essence_ascendance.balance.engine;

import java.util.List;

/** One typed, attributable claim. Retaining losing claims makes override explanations auditable. */
public record EvidenceFact(Subject subject, String subjectId, String property, Value value,
                           String provider, Origin origin, double confidence, int priority,
                           ProgressionBand stage, List<String> dependencies, String reason) {
    public EvidenceFact {
        if (subject == null || subjectId == null || property == null || value == null || provider == null
                || origin == null || !Double.isFinite(confidence) || confidence < 0 || confidence > 1)
            throw new IllegalArgumentException("Invalid evidence claim for " + subjectId + "/" + property);
        dependencies = dependencies == null ? List.of() : dependencies.stream().sorted().distinct().toList();
        reason = reason == null ? "" : reason;
    }
    public String key() { return subject + ":" + subjectId + ":" + property; }
    public enum Subject { ITEM, BLOCK, RECIPE, TAG, LOOT, TRADE, ENEMY, EQUIPMENT, CAPABILITY, SOURCE, DIMENSION, STRUCTURE, PROVIDER }
    public enum Origin { OBSERVED, INFERRED, OVERRIDE, POLICY }
    public enum ValueType { NUMBER, TEXT, FLAG }
    public record Value(ValueType type, double number, String text, boolean flag) {
        public Value {
            if (type == null || !Double.isFinite(number)) throw new IllegalArgumentException("Non-finite evidence value");
            text = text == null ? "" : text;
        }
        public static Value number(double value) { return new Value(ValueType.NUMBER, value, "", false); }
        public static Value text(String value) { return new Value(ValueType.TEXT, 0, value, false); }
        public static Value flag(boolean value) { return new Value(ValueType.FLAG, 0, "", value); }
    }
}
