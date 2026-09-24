package com.mistaboom.essence_ascendance.client.archive;

import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.archive.ArchiveCatalog;
import com.mistaboom.essence_ascendance.archive.ArchiveEntry;
import com.mistaboom.essence_ascendance.archive.ArchiveLocation;
import com.mistaboom.essence_ascendance.archive.ArchiveMode;
import com.mistaboom.essence_ascendance.archive.ArchiveSection;
import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.client.ui.content.ContentViewport;
import com.mistaboom.essence_ascendance.client.ui.content.EntryListView;
import com.mistaboom.essence_ascendance.client.ui.content.SemanticDocument;
import com.mistaboom.essence_ascendance.client.ui.data.ReadOnlyDataTable;
import com.mistaboom.essence_ascendance.client.ui.data.ReadOnlyDataTableView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/** Catalog, semantic composition, typed table, navigation/history and item-asset invariants. */
public final class ArchiveFoundationTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        catalogAndContent();
        localization();
        navigationAndHistory();
        tables();
        componentInput();
        itemAsset();
        System.out.println("ArchiveFoundationTest: " + checks + " Archive foundation assertions PASS");
    }

    private static void catalogAndContent() {
        ArchiveCatalog catalog = ArchiveCatalog.DEFAULT;
        check(catalog.sections(ArchiveMode.GUIDE).size() == 6, "Guide curriculum does not expose six sections");
        check(catalog.sections(ArchiveMode.REFERENCE).size() == 6, "Reference curriculum does not expose six sections");
        check(catalog.entries().size() >= 12, "Every initial section needs functioning content");
        for (ArchiveEntry entry : catalog.entries()) {
            check(catalog.entry(entry.id()) == entry, "Catalog lookup lost canonical entry identity");
            check(!catalog.searchableText(entry).isEmpty(), "Semantic entry cannot feed global search");
            check(entry.content().get().blocks().size() > 0, "Entry provider returned an empty shell");
        }
        ArchiveEntry guideMachines = catalog.entry(id("guide/machines/first_network"));
        ArchiveEntry referenceMachines = catalog.entry(id("reference/machines/overview"));
        check(guideMachines != null && referenceMachines != null && !guideMachines.id().equals(referenceMachines.id())
                        && guideMachines.subject().equals(referenceMachines.subject()),
                "Guide and Reference documents about one subject must remain distinct entries");

        boolean illustration = false, steps = false, callout = false, stats = false, requirements = false, links = false;
        for (ArchiveEntry entry : catalog.entries()) for (SemanticDocument.Block block : entry.content().get().blocks()) {
            illustration |= block instanceof SemanticDocument.Illustration;
            steps |= block instanceof SemanticDocument.OrderedSteps;
            callout |= block instanceof SemanticDocument.Callout;
            stats |= block instanceof SemanticDocument.StatRows;
            requirements |= block instanceof SemanticDocument.Requirements;
            links |= block instanceof SemanticDocument.Links;
        }
        check(illustration && steps && callout && stats && requirements && links,
                "Initial curriculum does not exercise the reusable semantic components");
        var relations = catalog.entries().stream().flatMap(entry -> entry.content().get().blocks().stream())
                .filter(SemanticDocument.Links.class::isInstance).map(SemanticDocument.Links.class::cast)
                .flatMap(block -> block.links().stream()).map(SemanticDocument.Link::relation)
                .collect(java.util.stream.Collectors.toSet());
        check(relations.containsAll(List.of(SemanticDocument.LinkRelation.RELATED,
                        SemanticDocument.LinkRelation.PREVIOUS, SemanticDocument.LinkRelation.NEXT)),
                "Related and previous/next link roles are not composed by live entries");
        Component styled = Component.literal("semantic").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        SemanticDocument document = new SemanticDocument(styled, List.of(new SemanticDocument.Paragraph(styled)));
        check(document.searchableText().getFirst().getStyle().isBold()
                        && document.searchableText().getFirst().getStyle().getColor() != null,
                "Searchable semantic content flattened its style");
    }

    private static void navigationAndHistory() {
        ArchiveCatalog catalog = ArchiveCatalog.DEFAULT;
        ArchiveNavigator navigator = new ArchiveNavigator(catalog, ArchiveNavigationState.Snapshot.initial());
        check(navigator.mode() == ArchiveMode.GUIDE
                        && navigator.section().equals(ArchiveSection.GUIDE_BEGINNING.id())
                        && navigator.entryId().equals(id("guide/beginning/welcome")),
                "First use did not open Guide → Beginning");
        navigator.setArticleScroll(27);
        navigator.setListScroll(3);
        navigator.selectMode(ArchiveMode.REFERENCE);
        navigator.selectSection(ArchiveSection.REFERENCE_SKILLS.id());
        navigator.selectEntry(id("reference/skills/ranks"));
        navigator.setArticleScroll(41);
        navigator.selectMode(ArchiveMode.GUIDE);
        check(navigator.articleScroll() == 27 && navigator.listScroll() == 3,
                "Guide location was not independent of Reference location");
        navigator.selectMode(ArchiveMode.SEARCH);
        navigator.setQuery("machine");
        navigator.selectSearchResult(id("reference/machines/overview"));
        navigator.setListScroll(4);
        ArchiveNavigationState.Snapshot saved = navigator.snapshot();
        ArchiveNavigator restored = new ArchiveNavigator(catalog, saved);
        check(restored.mode() == ArchiveMode.SEARCH && restored.query().equals("machine")
                        && restored.selectedResult().equals(id("reference/machines/overview"))
                        && restored.listScroll() == 4,
                "Search query/result/scroll did not restore independently");
        restored.selectMode(ArchiveMode.REFERENCE);
        check(restored.section().equals(ArchiveSection.REFERENCE_SKILLS.id()) && restored.articleScroll() == 41,
                "Reference section/entry/scroll did not restore");

        ArchiveNavigationState.Snapshot invalid = new ArchiveNavigationState.Snapshot(ArchiveMode.GUIDE,
                Map.of(ArchiveMode.GUIDE, new ArchiveNavigationState.Page(id("guide/removed"), id("guide/removed/page"),
                        99, 4, 8)), ArchiveNavigationState.Search.initial(),
                List.of(new ArchiveLocation.YieldBrowser(id("subject/essence"), null)));
        ArchiveNavigator reconciled = new ArchiveNavigator(catalog, invalid);
        check(reconciled.section().equals(ArchiveSection.GUIDE_BEGINNING.id())
                        && reconciled.entryId().equals(id("guide/beginning/welcome")),
                "Removed canonical targets did not fall back safely");
        for (int index = 0; index < 80; index++) reconciled.selectMode(index % 2 == 0 ? ArchiveMode.REFERENCE : ArchiveMode.GUIDE);
        check(reconciled.snapshot().history().size() <= ArchiveNavigator.HISTORY_LIMIT,
                "Canonical navigation history is not bounded");
        ArchiveNavigator missing = new ArchiveNavigator(catalog, new ArchiveNavigationState.Snapshot(ArchiveMode.GUIDE,
                Map.of(ArchiveMode.GUIDE, new ArchiveNavigationState.Page(ArchiveSection.GUIDE_BEGINNING.id(), null, 0, 0, 0)),
                ArchiveNavigationState.Search.initial(), List.of()));
        check(missing.entryId().equals(id("guide/beginning/welcome")) && catalog.entry(null) == null,
                "An absent remembered entry falls back safely without an immutable-map null lookup");
        missing.selectMode(ArchiveMode.SEARCH);
        missing.selectSearchResult(id("guide/beginning/welcome"));
        missing.setListScroll(5);
        missing.openSelectedSearchResult();
        missing.back();
        check(missing.mode() == ArchiveMode.SEARCH && missing.listScroll() == 5,
                "Opening a search result and returning preserves the captured results viewport");
    }

    private static void localization() throws Exception {
        try (var stream = ArchiveFoundationTest.class.getResourceAsStream(
                "/assets/essence_ascendance/lang/en_us.json")) {
            check(stream != null, "English localization resource is absent");
            var language = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            ArchiveCatalog catalog = ArchiveCatalog.DEFAULT;
            for (ArchiveMode mode : ArchiveMode.values()) requireTranslation(language, mode.label());
            for (ArchiveMode mode : List.of(ArchiveMode.GUIDE, ArchiveMode.REFERENCE))
                for (ArchiveSection section : catalog.sections(mode)) requireTranslation(language, section.label());
            for (ArchiveEntry entry : catalog.entries())
                for (Component component : catalog.searchableText(entry)) requireTranslation(language, component);
            for (String key : List.of("item.essence_ascendance.ascendance_archive",
                    "gui.essence_ascendance.table.sort.ascending", "gui.essence_ascendance.table.sort.descending",
                    "gui.essence_ascendance.content.step", "gui.essence_ascendance.requirement.met",
                    "gui.essence_ascendance.requirement.unmet", "gui.essence_ascendance.requirement.information",
                    "gui.essence_ascendance.link.related", "gui.essence_ascendance.link.previous",
                    "gui.essence_ascendance.link.next", "guide.essence_ascendance.archive.title",
                    "guide.essence_ascendance.archive.control.back", "guide.essence_ascendance.archive.search.placeholder"))
                check(language.has(key), "Missing Archive localization " + key);
        }
    }

    private static void requireTranslation(com.google.gson.JsonObject language, Component component) {
        if (component.getContents() instanceof TranslatableContents translated)
            check(language.has(translated.getKey()), "Missing semantic localization " + translated.getKey());
    }

    private static void tables() {
        record Rank(String name, Integer rank) { }
        ReadOnlyDataTable<Rank> table = new ReadOnlyDataTable<>(List.of(
                ReadOnlyDataTable.Column.text("name", Component.literal("Name"), 40, 1,
                        row -> Component.literal(row.name()), Comparator.comparing(Component::getString)),
                ReadOnlyDataTable.Column.number("rank", Component.literal("Rank"), 30, 0,
                        Rank::rank, value -> Component.literal(Integer.toString(value))),
                new ReadOnlyDataTable.Column<>("natural", Component.literal("Natural"), 35, 0,
                        ReadOnlyDataTable.Alignment.LEFT, Rank::name, Component::literal, null)
        ), List.of(
                new ReadOnlyDataTable.Row<>("charlie", new Rank("Charlie", 3)),
                new ReadOnlyDataTable.Row<>("alpha", new Rank("Alpha", 1)),
                new ReadOnlyDataTable.Row<>("bravo", new Rank("Bravo", 2))));
        check(table.ordered("rank", ReadOnlyDataTable.SortDirection.ASCENDING).stream()
                        .map(ReadOnlyDataTable.Row::key).toList().equals(List.of("alpha", "bravo", "charlie")),
                "Numeric typed comparator did not sort ranks");
        check(table.ordered("name", ReadOnlyDataTable.SortDirection.DESCENDING).stream()
                        .map(ReadOnlyDataTable.Row::key).toList().equals(List.of("charlie", "bravo", "alpha")),
                "Text typed comparator did not sort labels");
        check(table.ordered("natural", ReadOnlyDataTable.SortDirection.DESCENDING).equals(table.rows()),
                "Sorting-disabled columns did not retain fixed natural order");
        List<ReadOnlyDataTable.ColumnWidth> widths = table.measure(220, 1, component -> component.getString().length() * 6);
        check(widths.size() == 3 && widths.getLast().x() + widths.getLast().width() == 220,
                "Measured columns did not consume the available width exactly");
        check(table.rows().stream().map(ReadOnlyDataTable.Row::key).distinct().count() == table.rows().size(),
                "Stable table row keys were lost");
        check(table.visibleRows("rank", ReadOnlyDataTable.SortDirection.ASCENDING, 1, 1)
                        .stream().map(ReadOnlyDataTable.Row::key).toList().equals(List.of("bravo")),
                "Visible-row window did not follow the sorted data view");
        ReadOnlyDataTableView<Rank> view = new ReadOnlyDataTableView<>(table);
        view.restore("bravo", 12, "rank", ReadOnlyDataTable.SortDirection.DESCENDING);
        check(view.selectedKey().equals("bravo") && view.sortColumn().equals("rank")
                        && view.sortDirection() == ReadOnlyDataTable.SortDirection.DESCENDING,
                "Table view did not restore stable selection and sort state");
    }

    private static void itemAsset() throws Exception {
        check(com.mistaboom.essence_ascendance.item.AscendanceItems.ASCENDANCE_ARCHIVE != null,
                "Archive item registration is absent");
        try (var stream = ArchiveFoundationTest.class.getResourceAsStream(
                "/assets/essence_ascendance/models/item/ascendance_archive.json")) {
            check(stream != null, "Archive item model is absent");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            check(json.get("parent").getAsString().equals("minecraft:item/generated")
                            && json.getAsJsonObject("textures").get("layer0").getAsString().equals("minecraft:item/book"),
                    "Archive placeholder is not a normal generated item using minecraft:item/book");
        }
    }

    private static void componentInput() {
        var listEntries = IntStream.range(0, 30).mapToObj(index -> new EntryListView.Entry<>("row/" + index,
                Component.literal("Row " + index), Component.empty(), index)).toList();
        AtomicInteger chosen = new AtomicInteger(-1);
        EntryListView<Integer> list = new EntryListView<>(chosen::set);
        list.prepare(new UiBounds(0, 0, 100, 92), listEntries);
        for (int i = 0; i < 10; i++) list.key(264, 0, 0);
        check(chosen.get() == 10 && list.selectedKey().equals("row/10") && list.scrollOffset() == 8,
                "Keyboard list selection remains visible while moving beyond the initial viewport");
        list.click(5, 5, 1);
        check(chosen.get() == 10 && list.selectedKey().equals("row/10"),
                "Non-primary list clicks cannot desynchronize visual selection from the chosen document");
        list.key(265, 0, 0); list.key(265, 0, 0); list.key(265, 0, 0);
        check(list.selectedKey().equals("row/7") && list.scrollOffset() == 7,
                "Moving list selection above the viewport reveals it");

        AtomicInteger searchSelection = new AtomicInteger(-1);
        AtomicInteger searchActivation = new AtomicInteger(-1);
        EntryListView<Integer> search = new EntryListView<>(searchSelection::set, searchActivation::set);
        search.prepare(new UiBounds(0, 0, 100, 92), listEntries);
        search.key(264, 0, 0);
        check(searchSelection.get() == 1 && searchActivation.get() == -1,
                "Search arrows move selection without opening a result");
        search.key(257, 0, 0);
        check(searchActivation.get() == 1, "Enter activates the selected search result");
        search.click(5, 65, 0);
        check(searchSelection.get() == 2 && searchActivation.get() == 2,
                "A primary click selects and immediately activates a search result");

        AtomicInteger comparisons = new AtomicInteger();
        ReadOnlyDataTable<Integer> data = new ReadOnlyDataTable<>(List.of(new ReadOnlyDataTable.Column<>(
                "value", Component.literal("Long localized header"), 20, 1, ReadOnlyDataTable.Alignment.LEFT,
                value -> value, value -> Component.literal("Full styled row value " + value).withStyle(ChatFormatting.GOLD),
                (left, right) -> { comparisons.incrementAndGet(); return Integer.compare(left, right); })),
                IntStream.range(0, 30).mapToObj(value -> new ReadOnlyDataTable.Row<>("row/" + value, value)).toList());
        ReadOnlyDataTableView<Integer> table = new ReadOnlyDataTableView<>(data);
        UiBounds tableBounds = new UiBounds(10, 20, 70, 71);
        table.prepare(tableBounds, component -> component.getString().length() * 6);
        table.key(264);
        check(table.selectedKey().equals("row/0"), "The first table arrow selects the first row instead of skipping it");
        for (int i = 0; i < 10; i++) table.key(264);
        check(table.selectedKey().equals("row/10") && table.scrollOffset() == 8,
                "Table keyboard selection scrolls fully into view");
        var overflow = table.overflowTextAt(15, 45);
        check(overflow.isPresent() && overflow.get().getString().endsWith("8")
                        && overflow.get().getStyle().getColor() != null,
                "Compressed table cells expose the full styled value for the visible row");
        check(table.overflowTextAt(15, 24).isPresent() && table.overflowTextAt(15, 200).isEmpty(),
                "Compressed headers expose overflow while off-table coordinates do not");
        table.click(15, 24, 1);
        check(table.sortColumn() == null, "Non-primary header clicks do not reorder a table");
        table.click(15, 24, 0);
        table.orderedRows();
        int compared = comparisons.get();
        table.orderedRows(); table.overflowTextAt(15, 45);
        check(compared > 0 && comparisons.get() == compared,
                "Rendering and hit testing reuse one sorted row order instead of sorting the entire dataset again");
        table.key(269);
        check(!table.scroll(-1) && table.scroll(1),
                "A table at its lower edge yields downward wheel input and still scrolls back upward");

        Font metricFont = new Font(ignored -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
            @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
                return List.of(FormattedCharSequence.forward(text.getString(), Style.EMPTY));
            }
        };
        ContentViewport article = new ContentViewport((graphics, illustration, bounds, tick) -> { }, ignored -> { });
        SemanticDocument document = new SemanticDocument(Component.literal("Table"),
                List.of(new SemanticDocument.Table<>(data), new SemanticDocument.Paragraph(Component.literal("After table"))));
        article.prepare(metricFont, new UiBounds(0, 0, 200, 100), document);
        // Title plus padding places the table at y=28; its body starts at y=47.
        article.click(20, 55, 0);
        article.key(269, 0, 0);
        check(article.scrollOffset() == 0, "Focused table End scrolls its rows without moving the article");
        article.scroll(20, 55, 0, -1);
        check(article.scrollOffset() > 0, "Wheel input at the embedded table end continues scrolling the article");
        article.restore(0);
        article.prepare(metricFont, new UiBounds(0, 0, 200, 100),
                new SemanticDocument(Component.literal("New table"), document.blocks()));
        article.key(269, 0, 0);
        check(article.scrollOffset() > 0, "A replaced document does not inherit the previous embedded table's focus");

        ReadOnlyDataTable<Integer> equal = new ReadOnlyDataTable<>(List.of(new ReadOnlyDataTable.Column<Integer, Integer>(
                "equal", Component.literal("Equal"), 1, 0, ReadOnlyDataTable.Alignment.LEFT,
                value -> 0, value -> Component.literal("0"), Comparator.naturalOrder())), data.rows());
        check(equal.ordered("equal", ReadOnlyDataTable.SortDirection.DESCENDING).equals(data.rows()),
                "Equal sort values preserve natural row ordering in both directions");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("essence_ascendance", path);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
