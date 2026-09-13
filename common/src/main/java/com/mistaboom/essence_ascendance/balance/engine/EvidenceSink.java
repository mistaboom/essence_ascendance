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

    public EvidenceSink() { this(Map.of()); }
    public EvidenceSink(Map<String, Integer> providerPriorities) { this.providerPriorities = Map.copyOf(providerPriorities); }
    public void add(EvidenceFact fact) {
        Integer replacement = providerPriorities.get(fact.provider());
        if (replacement != null && fact.origin() != EvidenceFact.Origin.OVERRIDE) {
            fact = new EvidenceFact(fact.subject(), fact.subjectId(), fact.property(), fact.value(), fact.provider(),
                    fact.origin(), fact.confidence(), replacement, fact.stage(), fact.dependencies(), fact.reason());
        }
        facts.add(fact);
        resolved.merge(fact.key(), fact, (left, right) -> PREFERENCE.compare(left, right) >= 0 ? left : right);
    }
    public EvidenceFact get(EvidenceFact.Subject subject, String id, String property) {
        return resolved.get(subject + ":" + id + ":" + property);
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
        return facts.stream().sorted(Comparator.comparing(EvidenceFact::key).thenComparing(PREFERENCE)).toList();
    }
    public void warn(String warning) { warnings.add(warning); }
    public List<String> warnings() { return warnings.stream().sorted().distinct().toList(); }
}
