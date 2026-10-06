package com.mistaboom.essence_ascendance.balance.engine;

import java.util.*;
import static com.mistaboom.essence_ascendance.balance.engine.CapabilityEvidence.*;

/** Operation-owned factual output. Configurations must carry real witnesses, never synthetic per-axis maxima. */
public final class CapabilitySink {
    public enum Reason { UNCLASSIFIED, ACCESS_UNPROVEN, READ_FAILED, UNSUPPORTED_API, UNKNOWN_BEHAVIOR,
        DATA_NOT_READY, CONFIGURATION_DISABLED, NO_SUPPORTED_OPERATION }
    public record Candidate(String subject, String provider, String detail, Set<CapabilityAxis> axes, Reason reason) {
        public Candidate {
            axes = axes == null ? Set.of() : Collections.unmodifiableSet(new TreeSet<>(axes));
            reason = reason == null ? Reason.UNCLASSIFIED : reason;
        }
        /** Old callers and saved candidate diagnostics retain explicit unknown scope. */
        public Candidate(String subject, String provider, String detail) {
            this(subject, provider, detail, Set.of(), Reason.UNCLASSIFIED);
        }
    }
    private final List<Functional> evidence = new ArrayList<>();
    private final Map<String, List<Candidate>> candidatesByProvider = new TreeMap<>();
    private final Map<String, Long> unknownCounts = new TreeMap<>();
    private final Map<CapabilityAxis, Map<Reason, Long>> coverage = new EnumMap<>(CapabilityAxis.class);
    private final Map<Reason, Long> unscoped = new EnumMap<>(Reason.class);
    private final Map<String, com.google.gson.JsonElement> definitions = new TreeMap<>();
    private long analyzed, configurations, pruned, dominanceComparisons;
    /** Merge detached, completed output without recounting sampled unknowns or rerunning pruning. */
    public void merge(CapabilitySink completed) {
        evidence.addAll(completed.evidence);
        completed.definitions.forEach((key, value) -> definitions.put(key, value.deepCopy()));
        completed.unknownCounts.forEach((key, count) -> unknownCounts.merge(key, count, Long::sum));
        completed.coverage.forEach((axis, reasons) -> reasons.forEach((reason, count) ->
                coverage.computeIfAbsent(axis, ignored -> new EnumMap<>(Reason.class)).merge(reason, count, Long::sum)));
        completed.unscoped.forEach((reason, count) -> unscoped.merge(reason, count, Long::sum));
        completed.candidatesByProvider.forEach((provider, samples) -> {
            var target = candidatesByProvider.computeIfAbsent(provider, ignored -> new ArrayList<>());
            samples.stream().limit(Math.max(0, 64 - target.size())).forEach(target::add);
        });
        analyzed += completed.analyzed; configurations += completed.configurations;
        pruned += completed.pruned; dominanceComparisons += completed.dominanceComparisons;
    }
    public void analyzed() { analyzed++; }
    public void definition(String provider, String id, com.google.gson.JsonElement definition) {
        definitions.put(provider + "/" + id, definition.deepCopy());
    }
    public Map<String, com.google.gson.JsonElement> definitions() { return Collections.unmodifiableMap(definitions); }
    public void add(Functional fact) { evidence.add(Objects.requireNonNull(fact)); }
    public void candidate(String subject, String provider, String detail) {
        candidate(subject, provider, detail, Set.of(), Reason.UNCLASSIFIED);
    }
    public void candidate(String subject, String provider, String detail, Set<CapabilityAxis> axes, Reason reason) {
        Candidate candidate = new Candidate(subject, provider, detail, axes, reason);
        unknownCounts.merge(provider + ": " + detail, 1L, Long::sum);
        if (candidate.axes().isEmpty()) unscoped.merge(candidate.reason(), 1L, Long::sum);
        else candidate.axes().forEach(axis -> coverage.computeIfAbsent(axis, ignored -> new EnumMap<>(Reason.class))
                .merge(candidate.reason(), 1L, Long::sum));
        // Late typed providers must not disappear behind earlier recipe/name diagnostics.
        var representatives = candidatesByProvider.computeIfAbsent(provider, ignored -> new ArrayList<>());
        if (representatives.size() < 64) representatives.add(candidate);
    }
    public Map<CapabilityAxis, Map<Reason, Long>> candidateCoverage() {
        Map<CapabilityAxis, Map<Reason, Long>> out = new EnumMap<>(CapabilityAxis.class);
        coverage.forEach((axis, reasons) -> out.put(axis, Collections.unmodifiableMap(new EnumMap<>(reasons))));
        return Collections.unmodifiableMap(out);
    }
    public Map<Reason, Long> unscopedCandidateReasons() { return Collections.unmodifiableMap(unscoped); }
    /** Provider submits at most 4096 candidates per batch, already bounded upstream. No Cartesian enumeration here.
     * Dominance requires identical semantics, no later access, and no tradeoff in scope, costs or operating facts. */
    public void configurations(List<Functional> batch) {
        if (batch.size() > 4096) throw new IllegalArgumentException("Provider must bound configuration candidates before submission");
        configurations += batch.size();
        Map<String, List<Prepared>> groups = new TreeMap<>();
        for (Functional candidate : batch.stream().sorted(Comparator.comparing(f -> f.source().subjectId() + "/" + f.configuration())).toList()) {
            if (!candidate.attainable() || !candidate.source().reachable()) { pruned++; continue; }
            Prepared prepared = prepare(candidate);
            // Only identical sources/semantic signatures can dominate. Avoid cross-source quadratic work.
            String signature = candidate.source().subjectId() + "/" + prepared.axes().entrySet().stream()
                    .map(e -> e.getKey() + "/" + e.getValue().scope() + "/" + e.getValue().operation()
                            + "/" + e.getValue().unsupported()).toList();
            List<Prepared> retained = groups.computeIfAbsent(signature, ignored -> new ArrayList<>());
            if (retained.stream().anyMatch(other -> dominates(other, prepared))) { pruned++; continue; }
            for (var it = retained.iterator(); it.hasNext();) if (dominates(prepared, it.next())) { it.remove(); pruned++; }
            retained.add(prepared);
        }
        groups.values().forEach(group -> group.forEach(p -> evidence.add(p.fact())));
    }
    private record Prepared(Functional fact, Map<String, Measurement> axes) { }
    private static Prepared prepare(Functional fact) {
        Map<String, Measurement> axes = new TreeMap<>();
        for (Measurement m : fact.measurements()) if (axes.putIfAbsent(m.comparisonKey(), m) != null)
            throw new IllegalArgumentException("Configuration must normalize duplicate comparable measurements before pruning");
        return new Prepared(fact, axes);
    }
    private boolean dominates(Prepared leftPrepared, Prepared rightPrepared) {
        dominanceComparisons++;
        Functional a = leftPrepared.fact(), b = rightPrepared.fact();
        if (!a.source().subjectId().equals(b.source().subjectId()) || a.source().stage().ordinal() > b.source().stage().ordinal()
                || a.source().confidence() < b.source().confidence() || a.measurements().size() != b.measurements().size()) return false;
        for (Measurement right : b.measurements()) {
            Measurement left = leftPrepared.axes().get(right.comparisonKey());
            if (left == null || left.magnitude() == null || right.magnitude() == null || left.magnitude() < right.magnitude()
                    || !left.scope().equals(right.scope()) || !left.operation().equals(right.operation())
                    || !left.unsupported().equals(right.unsupported())) return false;
        }
        return true;
    }
    public List<Functional> evidence() { return List.copyOf(evidence); }
    public List<Candidate> candidates() {
        List<Candidate> result = new ArrayList<>();
        // Round-robin deterministic admission: every provider gets a representative before a second sample.
        for (int index = 0; index < 64 && result.size() < 512; index++) {
            for (var group : candidatesByProvider.values()) {
                if (index < group.size()) result.add(group.get(index));
                if (result.size() == 512) break;
            }
        }
        return List.copyOf(result);
    }
    public int evidenceCount() { return evidence.size(); }
    public Map<String, Long> counts() {
        Map<String, Long> result = new TreeMap<>(); result.put("sourcesAnalyzed", analyzed);
        result.put("evidenceSources", (long)evidence.size()); result.put("configurationCandidates", configurations);
        result.put("prunedConfigurations", pruned); result.put("candidateCount", unknownCounts.values().stream().mapToLong(Long::longValue).sum());
        result.put("configurationDominanceComparisons", dominanceComparisons);
        result.put("candidateRepresentatives", (long)candidates().size()); return result;
    }
    public Map<String, Long> unsupportedCounts() { return Collections.unmodifiableMap(unknownCounts); }
}
