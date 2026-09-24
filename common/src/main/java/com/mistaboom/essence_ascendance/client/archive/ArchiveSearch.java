package com.mistaboom.essence_ascendance.client.archive;

import com.mistaboom.essence_ascendance.archive.ArchiveCatalog;
import com.mistaboom.essence_ascendance.archive.ArchiveEntry;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Small foundation search over the canonical semantic catalog; ranking and advanced filters remain future work. */
public final class ArchiveSearch {
    private ArchiveSearch() { }

    public static List<ArchiveEntry> results(ArchiveCatalog catalog, String query) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) return catalog.entries();
        List<String> terms = Arrays.stream(normalized.split("\\s+")).filter(term -> !term.isBlank()).toList();
        return catalog.entries().stream().filter(entry -> {
            String haystack = normalize(catalog.searchableText(entry).stream()
                    .map(component -> component.getString()).reduce("", (left, right) -> left + " " + right));
            return terms.stream().allMatch(haystack::contains);
        }).toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
