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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.text.Normalizer;
import com.mistaboom.essence_ascendance.client.presentation.PresentationContext;

/** One index of real semantic articles and completed item yields; query edits never rebuild documents. */
public final class ArchiveSearch {
    private ArchiveSearch() { }

    public record Result(String target, Component title, Component summary, String searchableText) { }

    public static Component status(int count, boolean yieldsReady) {
        var text = count == 0 ? EssenceText.guide("archive.search.no_matches") : EssenceText.guide("archive.search.count", count);
        return yieldsReady ? text : text.append(" · ").append(EssenceText.guide("archive.search.yields_unavailable"));
    }

    public static final class Index {
        private final List<Result> entries;
        private final java.util.Map<String, String> titles;
        public Index(List<Result> candidates) {
            var unique = new LinkedHashMap<String, Result>();
            for (Result result : candidates) unique.putIfAbsent(result.target(), new Result(result.target(),
                    result.title(), result.summary(), normalize(result.searchableText())));
            entries = List.copyOf(unique.values());
            titles = entries.stream().collect(java.util.stream.Collectors.toMap(Result::target,
                    result -> normalize(result.title().getString())));
        }
        public List<Result> entries() { return entries; }

        public List<Result> results(String query) {
            List<String> terms = terms(query);
            String phrase = normalize(query);
            // Explanatory pages remain ahead of the unlimited item group; neither group hides matches.
            return entries.stream().filter(entry -> terms.stream().allMatch(entry.searchableText()::contains))
                    .sorted(Comparator.comparing((Result result) -> result.target().startsWith("yield:"))
                            .thenComparing(Comparator.comparingInt((Result result) -> score(titles.get(result.target()), phrase, terms)).reversed())
                            .thenComparing(result -> titles.get(result.target()))
                            .thenComparing(Result::target)).toList();
        }
    }

    /** A query never participates in the document-cache key. */
    public static final class Cache {
        private ArchiveCatalog catalog;
        private ItemEssenceTooltipClientState.YieldSnapshot yields;
        private PresentationContext.Revision revision;
        private Index index;
        public Index get(ArchiveCatalog catalog, ItemEssenceTooltipClientState.YieldSnapshot yields,
                         PresentationContext.Revision revision) {
            if (index == null || this.catalog != catalog || this.yields != yields || !Objects.equals(this.revision, revision)) {
                index = index(catalog, yields);
                this.catalog = catalog; this.yields = yields; this.revision = revision;
            }
            return index;
        }
    }

    private static int score(String title, String phrase, List<String> terms) {
        if (phrase.isEmpty()) return 0;
        if (title.equals(phrase)) return 1000;
        if (title.startsWith(phrase)) return 800;
        if (title.contains(phrase)) return 600;
        if (terms.stream().allMatch(title::contains)) return 400;
        return (int) terms.stream().filter(title::contains).count() * 20;
    }

    public static Index index(ArchiveCatalog catalog, ItemEssenceTooltipClientState.YieldSnapshot yields) {
        List<Result> entries = new ArrayList<>();
        for (ArchiveEntry entry : catalog.entries()) {
            Component path = entry.mode().label().copy().append(" › ").append(catalog.section(entry.section()).label());
            entries.add(new Result(entry.id().toString(), entry.title(), path,
                    normalize(catalog.searchableText(entry).stream().map(Component::getString)
                            .collect(java.util.stream.Collectors.joining(" ")))));
        }
        for (ItemYieldBrowser.SearchMetadata item : ItemYieldBrowser.searchMetadata(yields)) {
            entries.add(new Result(item.target(), item.localizedName(),
                    com.mistaboom.essence_ascendance.archive.ArchiveMode.REFERENCE.label().copy().append(" › ")
                            .append(EssenceText.guide("archive.entry.reference.essences.item_yields.title")),
                    normalize(item.terms().stream().map(Component::getString)
                            .collect(java.util.stream.Collectors.joining(" ")))));
        }
        return new Index(entries);
    }

    private static List<String> terms(String query) {
        return Arrays.stream(normalize(query).split(" ")).filter(term -> !term.isBlank()).distinct().toList();
    }

    public static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT).replaceAll("[\\p{javaWhitespace}\\p{Z}]+", " ").strip();
    }
}
