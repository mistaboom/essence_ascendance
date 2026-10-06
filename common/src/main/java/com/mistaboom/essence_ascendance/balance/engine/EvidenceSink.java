package com.mistaboom.essence_ascendance.balance.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Priority, then confidence, then origin, then lexical provider ID resolve conflicts reproducibly. */
public final class EvidenceSink {
    private static final Comparator<EvidenceFact> PREFERENCE = Comparator.comparingInt(EvidenceFact::priority)
            .thenComparingDouble(EvidenceFact::confidence).thenComparingInt(f -> f.origin().ordinal())
            .thenComparing(EvidenceFact::provider).thenComparing(f -> f.value().toString());
    private final List<EvidenceFact> facts = new ArrayList<>();
    private final Map<String, EvidenceFact> resolved = new TreeMap<>();
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, Integer> providerPriorities;
    private final EvidenceSink baseline;
    private long conflicts;

    public EvidenceSink() { this(Map.of()); }
    public EvidenceSink(Map<String, Integer> providerPriorities) { this(providerPriorities, null); }
    private EvidenceSink(Map<String, Integer> providerPriorities, EvidenceSink baseline) {
        this.providerPriorities = Map.copyOf(providerPriorities); this.baseline = baseline;
    }
    /** A provider writes to this detached sink; failed hooks cannot leak partial facts. */
    public EvidenceSink staged() { return new EvidenceSink(providerPriorities, this); }
    public void merge(EvidenceSink completed) {
        completed.facts.forEach(this::add);
        warnings.addAll(completed.warnings);
    }
    public void add(EvidenceFact fact) {
        Integer replacement = providerPriorities.get(fact.provider());
        if (replacement != null && fact.origin() != EvidenceFact.Origin.OVERRIDE) {
            fact = new EvidenceFact(fact.subject(), fact.subjectId(), fact.property(), fact.value(), fact.provider(),
                    fact.origin(), fact.confidence(), replacement, fact.stage(), fact.dependencies(), fact.reason());
        }
        facts.add(fact);
        resolved.merge(fact.key(), fact, (left, right) -> {
            if (!left.value().equals(right.value())) conflicts++;
            return PREFERENCE.compare(left, right) >= 0 ? left : right;
        });
    }
    public EvidenceFact get(EvidenceFact.Subject subject, String id, String property) {
        EvidenceFact local = resolved.get(subject + ":" + id + ":" + property);
        EvidenceFact inherited = baseline == null ? null : baseline.get(subject, id, property);
        return local == null ? inherited : inherited == null || PREFERENCE.compare(local, inherited) >= 0 ? local : inherited;
    }
    public double number(EvidenceFact.Subject subject, String id, String property, double fallback) {
        EvidenceFact f = get(subject, id, property);
        return f != null && f.value().type() == EvidenceFact.ValueType.NUMBER ? f.value().number() : fallback;
    }
    public boolean flag(EvidenceFact.Subject subject, String id, String property, boolean fallback) {
        EvidenceFact f = get(subject, id, property);
        return f != null && f.value().type() == EvidenceFact.ValueType.FLAG ? f.value().flag() : fallback;
    }
    public String text(EvidenceFact.Subject subject, String id, String property, String fallback) {
        EvidenceFact f = get(subject, id, property);
        return f != null && f.value().type() == EvidenceFact.ValueType.TEXT ? f.value().text() : fallback;
    }
    public List<EvidenceFact> facts() {
        return java.util.stream.Stream.concat(baseline == null ? java.util.stream.Stream.empty() : baseline.facts().stream(), facts.stream())
                .sorted(Comparator.comparing(EvidenceFact::key).thenComparing(PREFERENCE)).toList();
    }
    public void warn(String warning) { warnings.add(warning); }
    public List<String> warnings() { return warnings.stream().sorted().distinct().toList(); }
    public int size() { return facts.size(); }
    public long conflicts() { return conflicts; }
    public record Emitted(long facts, long sources, double minimumConfidence) { }
    public Emitted emittedSince(int start) {
        long sources = 0; double confidence = 1;
        for (int i = start; i < facts.size(); i++) {
            EvidenceFact fact = facts.get(i); confidence = Math.min(confidence, fact.confidence());
            if (fact.subject() == EvidenceFact.Subject.SOURCE) sources++;
        }
        return new Emitted(facts.size() - start, sources, confidence);
    }
}
