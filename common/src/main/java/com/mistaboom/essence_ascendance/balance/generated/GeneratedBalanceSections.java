package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.stream.JsonWriter;
import com.mistaboom.essence_ascendance.balance.economy.EconomyProfile;
import com.mistaboom.essence_ascendance.balance.engine.PackEvidence;

import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

/** Canonical generation output without a JSON tree for an entire evidence or economy section. */
final class GeneratedBalanceSections {
    private GeneratedBalanceSections() { }

    static void writeEvidence(PackEvidence evidence, JsonWriter output) throws IOException {
        Objects.requireNonNull(evidence, "Missing generated evidence");
        output.beginObject();
        output.name("capabilities"); writeArray(evidence.capabilities(), output);
        output.name("enemies"); writeArray(evidence.enemies(), output);
        output.name("equipment"); writeArray(evidence.equipment(), output);
        output.name("facts"); writeArray(evidence.facts(), output);
        output.name("frontiers"); writeMap(evidence.frontiers(), output);
        output.name("graphSummary"); writeMap(evidence.graphSummary(), output);
        output.name("resources"); writeMap(evidence.resources(), output);
        output.name("warnings"); writeArray(evidence.warnings(), output);
        output.endObject();
    }

    static void writeEconomy(EconomyProfile economy, JsonWriter output) throws IOException {
        Objects.requireNonNull(economy, "Missing generated economy");
        output.beginObject();
        output.name("invariants"); writeArray(economy.invariants(), output);
        output.name("processes"); writeArray(economy.processes(), output);
        // The processing policy contains only two scalar efficiency values.
        output.name("processingPolicy"); writeLeaf(economy.processingPolicy(), output);
        output.name("resources"); writeMap(economy.resources(), output);
        output.name("solverPasses"); writeLeaf(economy.solverPasses(), output);
        output.name("warnings"); writeArray(economy.warnings(), output);
        output.endObject();
    }

    private static void writeArray(Iterable<?> values, JsonWriter output) throws IOException {
        output.beginArray();
        for (Object value : values) writeLeaf(value, output);
        output.endArray();
    }

    private static void writeMap(Map<?, ?> values, JsonWriter output) throws IOException {
        output.beginObject();
        // Gson uses String.valueOf for these String/enum map keys. Enum declaration
        // order is not canonical lexical order. Sorting retains references to entries,
        // not their JSON projections; only the current value becomes a JSON tree.
        var entries = values.entrySet().stream()
                .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey()))).toList();
        for (var entry : entries) {
            // GSON omits object members whose values are null, before canonical writing.
            if (entry.getValue() == null) continue;
            output.name(String.valueOf(entry.getKey()));
            writeLeaf(entry.getValue(), output);
        }
        output.endObject();
    }

    private static void writeLeaf(Object value, JsonWriter output) throws IOException {
        // Keep the existing adapters and exact number/null/escape behavior. A leaf is
        // one collection record or map value, never a complete generated section.
        BalanceDocument.writeCanonical(BalanceDocument.GSON.toJsonTree(value), output);
    }
}
