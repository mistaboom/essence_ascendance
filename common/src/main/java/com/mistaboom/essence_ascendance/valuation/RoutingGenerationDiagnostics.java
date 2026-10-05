package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Generation-only projection of resolved routing; no registry, tag or recipe scans. */
public final class RoutingGenerationDiagnostics {
    private RoutingGenerationDiagnostics() { }
    public static JsonObject collect(List<ProceduralValuationResult> values) {
        JsonObject result = new JsonObject(), counts = new JsonObject(), samples = new JsonObject();
        Map<String, Long> totals = new TreeMap<>(), rules = new TreeMap<>();
        for (var value : values) {
            var diagnostic = value.routingDiagnostics();
            var evidence = diagnostic.evidence();
            String winner = evidence.contains("structured_function") ? "structured_function"
                    : evidence.contains("name_hint") ? "name_hint"
                    : evidence.contains("downstream_recipes") ? "downstream_recipes"
                    : evidence.contains("recipe_composition") ? "recipe_composition" : "utility_fallback";
            List<String> groups = new java.util.ArrayList<>();
            groups.add(winner);
            if (evidence.contains("utility_fallback")) groups.add("fallback_only_function");
            if (evidence.contains("ambiguous_name_hint")) groups.add("ambiguous");
            if (evidence.contains("name_structured_conflict")) groups.add("conflicts");
            if (diagnostic.nameHints().stream().anyMatch(rule -> rule.equals("form:component")
                    || rule.equals("form:depiction") || rule.equals("form:wing_component")))
                groups.add("suppressed_false_positive_candidates");
            diagnostic.nameHints().forEach(rule -> rules.merge(rule, 1L, Long::sum));
            groups.forEach(group -> totals.merge(group, 1L, Long::sum));
            if (groups.stream().noneMatch(group -> !samples.has(group) || samples.getAsJsonArray(group).size() < 24)) continue;
            JsonObject row = new JsonObject();
            row.addProperty("item", value.itemId().toString());
            row.addProperty("precedenceWinner", winner);
            row.addProperty("routingConfidence", diagnostic.confidence().name());
            row.addProperty("nameSource", diagnostic.nameHintSource());
            row.add("evidence", strings(evidence));
            row.add("structuredSignals", strings(diagnostic.structuredSignals()));
            row.add("matchedRules", strings(diagnostic.nameHints()));
            // Categories describe integer payout only; zero payout is not unclassified function.
            JsonObject payout = new JsonObject();
            value.routedEssence().entrySet().stream().sorted(java.util.Comparator.comparing(e -> e.getKey().id().toString()))
                    .forEach(e -> payout.addProperty(e.getKey().id().toString(), e.getValue()));
            row.add("payoutCategories", payout);
            groups.forEach(group -> record(group, row, samples));
        }
        totals.forEach(counts::addProperty);
        JsonObject ruleCounts = new JsonObject(); rules.forEach(ruleCounts::addProperty);
        result.addProperty("items", values.size());
        result.addProperty("sampleLimitPerGroup", 24);
        result.addProperty("scope", "Routing hypotheses only. Precedence winner identifies the strongest evidence class, not exclusive contribution. Conflicts are disjoint name/structured vectors; ambiguity is more than two name axes without structured evidence. Suppressed candidates are contextual safeguards, not verified false positives. Classification does not establish reachability, economic correctness or measured capability correctness.");
        result.add("counts", counts); result.add("ruleCounts", ruleCounts); result.add("representatives", samples);
        return result;
    }
    private static JsonArray strings(List<String> values) {
        JsonArray result = new JsonArray(); values.forEach(result::add); return result;
    }
    private static void record(String group, JsonObject row, JsonObject samples) {
        if (!samples.has(group)) samples.add(group, new JsonArray());
        JsonArray sample = samples.getAsJsonArray(group);
        if (sample.size() < 24) sample.add(row);
    }
}
