package com.mistaboom.essence_ascendance.client.archive;

import com.mistaboom.essence_ascendance.archive.ArchiveCatalog;
import com.mistaboom.essence_ascendance.archive.ArchiveEntry;
import com.mistaboom.essence_ascendance.client.ItemEssenceTooltipClientState;
import com.mistaboom.essence_ascendance.text.EssenceText;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** One index of real semantic articles and completed item yields; query edits never rebuild documents. */
public final class ArchiveSearch {
    private ArchiveSearch() { }

    public record Result(String target, Component title, Component summary, String searchableText) { }

    public record Index(List<Result> entries) {
        public Index { entries = List.copyOf(entries); }

        public List<Result> results(String query) {
            List<String> terms = terms(query);
            return entries.stream().filter(entry -> terms.stream().allMatch(entry.searchableText()::contains)).toList();
        }
    }

    public static Index index(ArchiveCatalog catalog, ItemEssenceTooltipClientState.YieldSnapshot yields) {
        List<Result> entries = new ArrayList<>();
        for (ArchiveEntry entry : catalog.entries()) {
            entries.add(new Result(entry.id().toString(), entry.title(), entry.navigationSummary(),
                    normalize(catalog.searchableText(entry).stream().map(Component::getString)
                            .collect(java.util.stream.Collectors.joining(" ")))));
        }
        for (ItemYieldBrowser.SearchMetadata item : ItemYieldBrowser.searchMetadata(yields)) {
            entries.add(new Result(item.target(), item.localizedName(),
                    EssenceText.guide("archive.entry.reference.essences.item_yields.title"),
                    normalize(item.terms().stream().map(Component::getString)
                            .collect(java.util.stream.Collectors.joining(" ")))));
        }
        return new Index(entries);
    }

    private static List<String> terms(String query) {
        return Arrays.stream(normalize(query).split("\\s+")).filter(term -> !term.isBlank()).toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
