package com.mistaboom.essence_ascendance.balance.engine;

import java.util.*;
import static com.mistaboom.essence_ascendance.balance.engine.CapabilityEvidence.*;

/** Operation-owned factual output. Configurations must carry real witnesses, never synthetic per-axis maxima. */
public final class CapabilitySink {
    public record Candidate(String subject, String provider, String detail) { }
    private final List<Functional> evidence = new ArrayList<>();
    private final List<Candidate> candidates = new ArrayList<>();
    private final Map<String, Long> unknownCounts = new TreeMap<>();
    private final Map<String, Integer> representativesByProvider = new TreeMap<>();
    private final Map<String, com.google.gson.JsonElement> definitions = new TreeMap<>();
    private long analyzed, configurations, pruned;
    public void analyzed() { analyzed++; }
    public void definition(String provider, String id, com.google.gson.JsonElement definition) {
        definitions.put(provider + "/" + id, definition.deepCopy());
    }
    public Map<String, com.google.gson.JsonElement> definitions() { return Collections.unmodifiableMap(definitions); }
    public void add(Functional fact) { evidence.add(Objects.requireNonNull(fact)); }
    public void candidate(String subject, String provider, String detail) {
        unknownCounts.merge(provider + ": " + detail, 1L, Long::sum);
        if (candidates.size() < 512 && representativesByProvider.getOrDefault(provider, 0) < 64) {
            candidates.add(new Candidate(subject, provider, detail)); representativesByProvider.merge(provider, 1, Integer::sum);
        }
    }
    /** Provider submits at most 4096 candidates per batch, already bounded upstream. No Cartesian enumeration here.
     * Dominance requires identical semantics, no later access, and no tradeoff in scope, costs or operating facts. */
    public void configurations(List<Functional> batch) {
        if (batch.size() > 4096) throw new IllegalArgumentException("Provider must bound configuration candidates before submission");
        configurations += batch.size();
        List<Prepared> retained = new ArrayList<>();
        for (Functional candidate : batch.stream().sorted(Comparator.comparing(f -> f.source().subjectId() + "/" + f.configuration())).toList()) {
            if (!candidate.attainable() || !candidate.source().reachable()) { pruned++; continue; }
            Prepared prepared = prepare(candidate);
            if (retained.stream().anyMatch(other -> dominates(other, prepared))) { pruned++; continue; }
            for (var it = retained.iterator(); it.hasNext();) if (dominates(prepared, it.next())) { it.remove(); pruned++; }
            retained.add(prepared);
        }
        retained.forEach(p -> evidence.add(p.fact()));
    }
    private record Prepared(Functional fact, Map<String, Measurement> axes) { }
    private static Prepared prepare(Functional fact) {
        Map<String, Measurement> axes = new TreeMap<>();
        for (Measurement m : fact.measurements()) if (axes.putIfAbsent(m.comparisonKey(), m) != null)
            throw new IllegalArgumentException("Configuration must normalize duplicate comparable measurements before pruning");
        return new Prepared(fact, axes);
    }
    private static boolean dominates(Prepared leftPrepared, Prepared rightPrepared) {
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
    public List<Candidate> candidates() { return List.copyOf(candidates); }
    public Map<String, Long> counts() {
        Map<String, Long> result = new TreeMap<>(); result.put("sourcesAnalyzed", analyzed);
        result.put("evidenceSources", (long)evidence.size()); result.put("configurationCandidates", configurations);
        result.put("prunedConfigurations", pruned); result.put("candidateCount", unknownCounts.values().stream().mapToLong(Long::longValue).sum());
        result.put("candidateRepresentatives", (long)candidates.size()); return result;
    }
    public Map<String, Long> unsupportedCounts() { return Collections.unmodifiableMap(unknownCounts); }
}
