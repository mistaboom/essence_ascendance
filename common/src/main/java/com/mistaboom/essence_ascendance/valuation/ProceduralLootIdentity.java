package com.mistaboom.essence_ascendance.valuation;

import java.util.function.Predicate;

/** Path ancestry is a provenance hint, never permission to invent an entity. */
final class ProceduralLootIdentity {
    private ProceduralLootIdentity() { }

    static String registeredEntityPath(String tablePath, Predicate<String> registered) {
        if (tablePath == null || !tablePath.startsWith("entities/")) return null;
        String candidate = tablePath.substring("entities/".length());
        while (!candidate.isEmpty()) {
            if (registered.test(candidate)) return candidate;
            int separator = candidate.lastIndexOf('/');
            if (separator < 0) break;
            candidate = candidate.substring(0, separator);
        }
        return null;
    }
}
