package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Bounded report over the exact static signals used by item estimates. */
public final class RoutingGenerationDiagnostics {
    private RoutingGenerationDiagnostics() { }
    public static JsonObject collect(List<ProceduralValuationResult> values) {
        JsonObject result = new JsonObject(), counts = new JsonObject(); JsonArray samples = new JsonArray();
        Map<String, Long> totals = new TreeMap<>();
        for (var value : values) {
            value.routingDiagnostics().evidence().forEach(signal -> totals.merge(signal, 1L, Long::sum));
            if (samples.size() >= 24) continue;
            JsonObject row = new JsonObject(); row.addProperty("item", value.itemId().toString());
            row.addProperty("valueEvidence", value.valueEvidence().name());
            row.addProperty("routingConfidence", value.routingDiagnostics().confidence().name());
            JsonArray evidence = new JsonArray(); value.routingDiagnostics().evidence().forEach(evidence::add); row.add("evidence", evidence);
            JsonObject payout = new JsonObject(); value.routedEssence().entrySet().stream()
                    .sorted(java.util.Comparator.comparing(e -> e.getKey().id().toString()))
                    .forEach(e -> payout.addProperty(e.getKey().id().toString(), e.getValue()));
            row.add("payoutCategories", payout); samples.add(row);
        }
        totals.forEach(counts::addProperty); result.addProperty("items", values.size());
        result.addProperty("scope", "Common tags, static components and item types supply policy estimates; no item-name or namespace heuristics, acquisition proof or measured farm rate.");
        result.add("signalCounts", counts); result.add("representatives", samples); return result;
    }
}
