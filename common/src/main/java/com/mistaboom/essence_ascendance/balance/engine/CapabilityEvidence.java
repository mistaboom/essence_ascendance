package com.mistaboom.essence_ascendance.balance.engine;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Equipment-independent or transformative capability supplied by registered providers and factual declarations. */
public record CapabilityEvidence(String subjectId, ProgressionBand stage, Map<CapabilityAxis, Double> axes,
                                 boolean reachable, double confidence, String reason) {
    public CapabilityEvidence { axes = Collections.unmodifiableMap(new TreeMap<>(axes)); }

    /** Rich generation evidence extends the same axes/source model without changing runtime calibration. */
    public record Functional(CapabilityEvidence source, String configuration, java.util.List<Measurement> measurements,
                             java.util.List<AcquisitionSource> acquisition, boolean attainable) {
        public Functional {
            java.util.Objects.requireNonNull(source);
            if (!Double.isFinite(source.confidence()) || source.confidence() < 0 || source.confidence() > 1)
                throw new IllegalArgumentException("Invalid capability confidence");
            if (configuration == null || configuration.isBlank()) throw new IllegalArgumentException("Configuration identity required");
            measurements = java.util.List.copyOf(measurements); acquisition = java.util.List.copyOf(acquisition);
        }
    }
    public enum Origin { NATIVE, GENERIC_SYSTEM, TYPED_ADAPTER, NOMENCLATURE }
    public enum Activity { PLAYER_ACTIVE, PASSIVE, UNKNOWN }
    public enum Renewal { FINITE, RENEWABLE, UNKNOWN }
    public record Scope(String targets, String geometry, Double radiusBlocks, Double targetCount) {
        public Scope { nonnegative(radiusBlocks); nonnegative(targetCount); }
        public static Scope self() { return new Scope("self", "single", null, 1.0); }
        public static Scope unknown() { return new Scope("unknown", "unknown", null, null); }
    }
    /** Null numeric values mean unmeasured, never zero/free/infinite. Rates use seconds at nominal 20 TPS. */
    public record Operation(Automation automation, Activity activity, Renewal renewal,
                            Double unitsPerSecond, Double durationSeconds, Double uptimeFraction, Double cooldownSeconds,
                            java.util.List<String> recurringCosts, java.util.List<String> setup) {
        public Operation {
            java.util.Objects.requireNonNull(automation); java.util.Objects.requireNonNull(activity); java.util.Objects.requireNonNull(renewal);
            nonnegative(unitsPerSecond); nonnegative(durationSeconds); nonnegative(cooldownSeconds); nonnegative(uptimeFraction);
            if (uptimeFraction != null && uptimeFraction > 1) throw new IllegalArgumentException("Invalid uptime");
            recurringCosts = java.util.List.copyOf(recurringCosts); setup = java.util.List.copyOf(setup);
        }
        public static Operation manual() { return new Operation(Automation.NONE, Activity.PLAYER_ACTIVE, Renewal.UNKNOWN,
                null, null, null, null, java.util.List.of("operating cost unmeasured"), java.util.List.of("equip/use source")); }
        /** Best-case duty under the captured cooldown contract, not a measured player activity rate. */
        public Double uptimeBound() {
            Double duty = durationSeconds != null && durationSeconds > 0 && cooldownSeconds != null && cooldownSeconds > 0
                    ? Math.min(1, durationSeconds / cooldownSeconds) : null;
            if (uptimeFraction == null) return duty;
            if (duty == null) return uptimeFraction;
            return Math.min(uptimeFraction, duty);
        }
    }
    public record Measurement(CapabilityFamily family, CapabilityAxis axis, Double magnitude, String unit,
                              String applicability, Scope scope, Operation operation, String provider, Origin origin,
                              java.util.List<String> unsupported) {
        public Measurement {
            java.util.Objects.requireNonNull(family); java.util.Objects.requireNonNull(axis);
            java.util.Objects.requireNonNull(scope); java.util.Objects.requireNonNull(operation); java.util.Objects.requireNonNull(origin);
            nonnegative(magnitude);
            if (unit == null || unit.isBlank() || applicability == null || applicability.isBlank() || provider == null || provider.isBlank())
                throw new IllegalArgumentException("Capability needs units, applicability and provenance");
            if (origin == Origin.NOMENCLATURE && magnitude != null) throw new IllegalArgumentException("Names cannot measure power");
            unsupported = java.util.List.copyOf(unsupported);
        }
        /** Different transformations/units/target domains must never share a scalar frontier. */
        public String comparisonKey() { return family + "/" + axis + "/" + unit + "/" + applicability + "/"
                + scope.targets + "/" + scope.geometry + "/" + operation.automation + "/" + operation.activity + "/" + operation.renewal; }
    }
    private static void nonnegative(Double value) {
        if (value != null && (!Double.isFinite(value) || value < 0)) throw new IllegalArgumentException("Invalid capability measurement");
    }
}
