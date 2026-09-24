package com.mistaboom.essence_ascendance.client.archive;

import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.archive.ArchiveCatalog;
import com.mistaboom.essence_ascendance.archive.ArchiveEntry;
import com.mistaboom.essence_ascendance.archive.ArchiveLocation;
import com.mistaboom.essence_ascendance.archive.ArchiveMode;
import com.mistaboom.essence_ascendance.archive.ArchiveSection;
import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.client.ItemEssenceTooltipClientState;
import com.mistaboom.essence_ascendance.client.ui.content.ContentViewport;
import com.mistaboom.essence_ascendance.client.ui.content.EntryListView;
import com.mistaboom.essence_ascendance.client.ui.content.ItemPresentation;
import com.mistaboom.essence_ascendance.client.ui.content.SemanticDocument;
import com.mistaboom.essence_ascendance.client.ui.data.ReadOnlyDataTable;
import com.mistaboom.essence_ascendance.client.ui.data.ReadOnlyDataTableView;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenComposition;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenLayout;
import com.mistaboom.essence_ascendance.client.presentation.PresentationContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.core.component.DataComponentPatch;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/** Catalog, semantic composition, typed table, navigation/history and item-asset invariants. */
public final class ArchiveFoundationTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        catalogAndContent();
        localization();
        navigationAndHistory();
        yieldBrowserData();
        yieldBrowserLayout();
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
        for (ArchiveSection section : List.of(ArchiveSection.REFERENCE_SKILLS, ArchiveSection.REFERENCE_BONUSES)) {
            List<String> labels = catalog.entries(ArchiveMode.REFERENCE, section.id()).stream()
                    .map(entry -> entry.title().getString()).toList();
            List<String> alphabetical = labels.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
            check(labels.equals(alphabetical), "Reference side-panel entries are not alphabetical: " + section.id());
        }
        ArchiveEntry guideMachines = catalog.entry(id("guide/machines/first_network"));
        ArchiveEntry referenceMachines = catalog.entry(id("reference/machines/crucible"));
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
        navigator.selectEntry(catalog.entries(ArchiveMode.REFERENCE,
                ArchiveSection.REFERENCE_SKILLS.id()).getFirst().id());
        navigator.setArticleScroll(41);
        navigator.selectMode(ArchiveMode.GUIDE);
        check(navigator.articleScroll() == 27 && navigator.listScroll() == 3,
                "Guide location was not independent of Reference location");
        navigator.selectMode(ArchiveMode.SEARCH);
        navigator.setQuery("machine");
        navigator.selectSearchResult(id("reference/machines/crucible"));
        navigator.setListScroll(4);
        ArchiveNavigationState.Snapshot saved = navigator.snapshot();
        ArchiveNavigator restored = new ArchiveNavigator(catalog, saved);
        check(restored.mode() == ArchiveMode.SEARCH && restored.query().equals("machine")
                        && restored.selectedResult().equals(id("reference/machines/crucible"))
                        && restored.listScroll() == 4,
                "Search query/result/scroll did not restore independently");
        restored.selectMode(ArchiveMode.REFERENCE);
        check(restored.section().equals(ArchiveSection.REFERENCE_SKILLS.id()) && restored.articleScroll() == 41,
                "Reference section/entry/scroll did not restore");

        ArchiveNavigationState.Snapshot invalid = new ArchiveNavigationState.Snapshot(ArchiveMode.GUIDE,
                Map.of(ArchiveMode.GUIDE, new ArchiveNavigationState.Page(id("guide/removed"), id("guide/removed/page"),
                        99, 4, 8)), ArchiveNavigationState.Search.initial(),
                List.of(new ArchiveNavigationState.Visit(new ArchiveLocation.YieldBrowser(id("subject/essence"), null),
                        0, 0, 0)), List.of());
        ArchiveNavigator reconciled = new ArchiveNavigator(catalog, invalid);
        check(reconciled.section().equals(ArchiveSection.GUIDE_BEGINNING.id())
                        && reconciled.entryId().equals(id("guide/beginning/welcome")),
                "Removed canonical targets did not fall back safely");
        for (int index = 0; index < 80; index++) reconciled.selectMode(index % 2 == 0 ? ArchiveMode.REFERENCE : ArchiveMode.GUIDE);
        check(reconciled.snapshot().history().size() <= ArchiveNavigator.HISTORY_LIMIT,
                "Canonical navigation history is not bounded");
        ArchiveNavigator missing = new ArchiveNavigator(catalog, new ArchiveNavigationState.Snapshot(ArchiveMode.GUIDE,
                Map.of(ArchiveMode.GUIDE, new ArchiveNavigationState.Page(ArchiveSection.GUIDE_BEGINNING.id(), null, 0, 0, 0)),
                ArchiveNavigationState.Search.initial(), List.of(), List.of()));
        check(missing.entryId().equals(id("guide/beginning/welcome")) && catalog.entry(null) == null,
                "An absent remembered entry falls back safely without an immutable-map null lookup");
        missing.selectMode(ArchiveMode.SEARCH);
        missing.selectSearchResult(id("guide/beginning/welcome"));
        missing.setListScroll(5);
        missing.openSelectedSearchResult();
        missing.back();
        check(missing.mode() == ArchiveMode.SEARCH && missing.listScroll() == 5,
                "Opening a search result and returning preserves the captured results viewport");

        ArchiveNavigator browser = new ArchiveNavigator(catalog, ArchiveNavigationState.Snapshot.initial());
        check(!browser.canGoBack() && !browser.canGoForward(), "A fresh Archive has no history");
        browser.setArticleScroll(120);
        browser.setListScroll(3);
        browser.openTarget("essence_ascendance:reference/machines/crucible");
        browser.setArticleScroll(240);
        browser.setListScroll(4);
        browser.selectMode(ArchiveMode.SEARCH);
        browser.setQuery("Focus");
        browser.selectSearchResult(id("reference/machines/focus"));
        browser.setListScroll(7);
        browser.openSelectedSearchResult();
        browser.setArticleScroll(360);
        browser.back();
        check(browser.mode() == ArchiveMode.SEARCH && browser.query().equals("Focus")
                        && browser.selectedResult().equals(id("reference/machines/focus")) && browser.listScroll() == 7,
                "Back must restore the exact search visit");
        browser.back();
        check(browser.entryId().equals(id("reference/machines/crucible"))
                        && browser.articleScroll() == 240 && browser.listScroll() == 4,
                "Back must restore article and navigation-list scroll positions");
        browser.back();
        check(browser.mode() == ArchiveMode.GUIDE && browser.articleScroll() == 120 && browser.listScroll() == 3
                        && !browser.canGoBack() && browser.canGoForward(),
                "Back must reach the first visit without losing its viewport");
        browser.back();
        browser = new ArchiveNavigator(catalog, browser.snapshot());
        browser.forward();
        check(browser.entryId().equals(id("reference/machines/crucible")) && browser.articleScroll() == 240,
                "Forward history must survive closing/reopening the Archive");
        browser.forward();
        check(browser.mode() == ArchiveMode.SEARCH && browser.listScroll() == 7 && browser.query().equals("Focus"),
                "Forward must restore the search visit rather than page section tabs");
        browser.forward();
        check(browser.entryId().equals(id("reference/machines/focus")) && browser.articleScroll() == 360
                        && !browser.canGoForward(), "Forward must return to the latest article and viewport");
        browser.forward();
        browser.back();
        browser.selectMode(ArchiveMode.SEARCH);
        browser.openTarget("invalid:missing");
        check(browser.canGoForward(), "No-op or invalid navigation must not clear forward history");
        browser.openTarget("essence_ascendance:reference/machines/infuser");
        check(!browser.canGoForward(), "Following a new destination after Back must discard the old forward branch");
        browser.back();
        browser.back();
        browser.openTarget(browser.entryId().toString());
        check(browser.canGoForward(), "Opening the current article must not add a visit or clear Forward");
        for (int index = 0; index < 80; index++) reconciled.back();
        check(reconciled.snapshot().future().size() <= ArchiveNavigator.HISTORY_LIMIT,
                "Forward history is not bounded");

        ResourceLocation diamond = ResourceLocation.withDefaultNamespace("diamond");
        ResourceLocation coal = ResourceLocation.withDefaultNamespace("coal");
        browser.openYieldItem(diamond);
        ArchiveNavigationState.YieldBrowser configured = new ArchiveNavigationState.YieldBrowser("dia",
                Set.of(id("offense"), id("defense")), ArchiveNavigationState.YieldMatch.ALL,
                id("offense").toString(), ArchiveNavigationState.YieldSortDirection.DESCENDING, diamond, 9);
        browser.setYieldBrowser(configured);
        browser.selectSection(ArchiveSection.REFERENCE_ESSENCES.id());
        check(browser.entryId().equals(catalog.first(ArchiveMode.REFERENCE, ArchiveSection.REFERENCE_ESSENCES.id()).id())
                        && !browser.entryId().equals(ArchiveNavigator.ITEM_YIELDS),
                "Clicking Essences from Item Yields must return to the parent article list");
        browser.back();
        check(browser.currentLocation() instanceof ArchiveLocation.YieldBrowser && browser.yieldBrowser().equals(configured),
                "Returning from the parent list must restore the exact yield browser state");
        browser.openTarget("essence_ascendance:reference/machines/crucible");
        browser.back();
        check(browser.currentLocation() instanceof ArchiveLocation.YieldBrowser
                        && browser.yieldBrowser().equals(configured),
                "Back did not restore yield filters, sort, selection and results scroll");
        check(browser.canGoForward(), "Returning to the yield browser lost Forward");
        browser.openYieldItem(diamond);
        check(browser.canGoForward(), "Opening the current yield destination cleared Forward");
        browser.openTarget(ArchiveNavigator.yieldTarget(coal));
        check(!browser.canGoForward() && browser.yieldBrowser().selectedRow().equals(coal)
                        && browser.yieldBrowser().query().isEmpty()
                        && browser.yieldBrowser().selectedEssences().isEmpty(),
                "A new deep-linked row did not branch history and reveal itself predictably");
    }

    private static void yieldBrowserData() {
        ResourceLocation offense = id("offense");
        ResourceLocation defense = id("defense");
        List<ItemYieldBrowser.BrowserRow> rows = List.of(
                browserRow("minecraft:apple", "Apple", Map.of(offense, 125_000L)),
                browserRow("minecraft:coal", "Coal", Map.of(defense, 1_000_000L)),
                browserRow("minecraft:diamond", "Diamond", Map.of(offense, 2_000_000L, defense, 500_000L)));
        check(ItemYieldBrowser.filterRows(rows, "", Set.of(), ArchiveNavigationState.YieldMatch.ALL).equals(rows),
                "No selected category imposed an Essence restriction");
        check(ItemYieldBrowser.filterRows(rows, "", Set.of(offense, defense), ArchiveNavigationState.YieldMatch.ANY)
                        .equals(rows), "Any-selected filtering rejected a positive category");
        check(ItemYieldBrowser.filterRows(rows, "", Set.of(offense, defense), ArchiveNavigationState.YieldMatch.ALL)
                        .stream().map(ItemYieldBrowser.BrowserRow::itemId).toList()
                        .equals(List.of(ResourceLocation.withDefaultNamespace("diamond"))),
                "All-selected filtering did not require every exact positive yield");
        check(ItemYieldBrowser.filterRows(rows, "DIAM", Set.of(), ArchiveNavigationState.YieldMatch.ANY)
                        .equals(List.of(rows.getLast())), "Localized item-name filtering is not case-insensitive");
        check(ItemYieldBrowser.sortRows(rows, offense.toString(), ArchiveNavigationState.YieldSortDirection.ASCENDING)
                        .stream().map(ItemYieldBrowser.BrowserRow::itemId).toList()
                        .equals(List.of(ResourceLocation.withDefaultNamespace("coal"),
                                ResourceLocation.withDefaultNamespace("apple"), ResourceLocation.withDefaultNamespace("diamond"))),
                "Ascending Essence sort did not use exact raw fractional values");
        check(ItemYieldBrowser.sortRows(rows, offense.toString(), ArchiveNavigationState.YieldSortDirection.DESCENDING)
                        .stream().map(ItemYieldBrowser.BrowserRow::itemId).toList()
                        .equals(List.of(ResourceLocation.withDefaultNamespace("diamond"),
                                ResourceLocation.withDefaultNamespace("apple"), ResourceLocation.withDefaultNamespace("coal"))),
                "Descending Essence sort or deterministic tie-breaking failed");
        check(rows.getFirst().item().resource().equals(rows.getFirst().itemId()),
                "A yield row's representative image does not belong to that item");
        ReadOnlyDataTable<ItemYieldBrowser.BrowserRow> schema = ItemYieldBrowser.tableData(rows);
        for (int width = 0; width <= 1000; width++) {
            List<ReadOnlyDataTable.ColumnWidth> measured = measureYieldSchema(schema, width);
            check(measured.stream().mapToInt(ReadOnlyDataTable.ColumnWidth::width).sum()
                            + Math.min(1, width / (measured.size() - 1)) * (measured.size() - 1) == width,
                    "Item Yields columns did not consume the actual available width at " + width);
            int essenceWidth = measured.get(2).width();
            check(measured.subList(2, measured.size()).stream().allMatch(column -> column.width() == essenceWidth),
                    "Essence columns must be exactly equal even at compressed or odd viewport widths: " + width);
        }
        List<ReadOnlyDataTable.ColumnWidth> wide = measureYieldSchema(schema, 800);
        for (int index = 0; index < schema.columns().size(); index++)
            check(wide.get(index).width() >= schema.columns().get(index).minimumWidth(),
                    "A meaningful Item Yields header was squeezed at normal width: "
                            + schema.columns().get(index).header().getString());
        var networkRow = new ItemEssenceTooltipClientState.YieldRow(
                ResourceLocation.withDefaultNamespace("diamond"),
                List.of(new ItemEssenceTooltipClientState.Yield(offense, 125_000L)));
        var snapshot = new ItemEssenceTooltipClientState.YieldSnapshot(4, true,
                Map.of(networkRow.itemId(), networkRow.outputs()), List.of(networkRow));
        var projected = ItemYieldBrowser.project(snapshot).getFirst();
        check(projected.itemId().equals(projected.item().resource())
                        && projected.item().stack().is(net.minecraft.world.item.Items.DIAMOND)
                        && projected.yield(offense) == 125_000L,
                "Server-fed row did not retain its exact yield and registered representative image");
        check(ItemYieldBrowser.searchMetadata(snapshot).getFirst().target()
                        .equals(ArchiveNavigator.yieldTarget(networkRow.itemId())),
                "Yield rows do not expose the canonical reveal target for global Search");
    }

    private static void yieldBrowserLayout() {
        Font metricFont = new Font(ignored -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
            @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
                return List.of(FormattedCharSequence.forward(text.getString(), Style.EMPTY));
            }
        };
        var browser = new ItemYieldBrowser(ArchiveNavigationState.YieldBrowser.initial(), ignored -> { });
        var snapshot = new ItemEssenceTooltipClientState.YieldSnapshot(0, false, Map.of(), List.of());
        for (int width : List.of(300, 480, 800, 1600)) {
            var frame = FullscreenLayout.frame(FullscreenLayout.Spec.standard(), width, 600, true);
            var builder = new FullscreenComposition.Builder("yield-layout", frame);
            browser.compose(builder, frame.content(), metricFont, snapshot, new PresentationContext.Revision(0, 0, false, 0, 0));
            var scene = builder.build();
            UiBounds query = scene.regions().stream().filter(region -> region.id().equals("archive/yields/query"))
                    .findFirst().orElseThrow().bounds();
            check(query.equals(FullscreenLayout.bands(frame.content(), 24, 0, 6).header()),
                    "Yield filtering must use exactly the Archive Search field geometry");
            UiBounds table = scene.regions().stream().filter(region -> region.id().equals("archive/yields/table"))
                    .findFirst().orElseThrow().bounds();
            check(table.bottom() == frame.content().bottom() && table.width() == frame.content().width(),
                    "Yield results must fill the existing content boundaries with no footer reservation");
            check(scene.regions().stream().noneMatch(region -> region.id().contains("heading") || region.id().contains("see_also"))
                            && scene.controls().stream().noneMatch(control -> control.id().equals("archive/yields/essentium")),
                    "Removed explanatory heading and See Also footer must not return");
            check(scene.controls().stream().filter(control -> control.id().startsWith("archive/yields/category/")).count() == 6,
                    "Compact browser must retain all six category toggles");
            check(scene.controls().stream().allMatch(control -> control.bounds().y() >= query.bottom()
                            && control.bounds().bottom() <= table.y()
                            && control.bounds().x() >= frame.content().x() && control.bounds().right() <= frame.content().right()),
                    "Browser controls must stay between the full-width search field and table");
        }
    }

    private static ItemYieldBrowser.BrowserRow browserRow(String itemId, String name,
                                                           Map<ResourceLocation, Long> yields) {
        ResourceLocation id = ResourceLocation.parse(itemId);
        Component label = Component.literal(name);
        return new ItemYieldBrowser.BrowserRow(id, label,
                new ItemPresentation(id, DataComponentPatch.EMPTY, label), yields);
    }

    private static List<ReadOnlyDataTable.ColumnWidth> measureYieldSchema(
            ReadOnlyDataTable<ItemYieldBrowser.BrowserRow> schema, int width) {
        int[] localizedHeaderWidths = {24, 24, 42, 42, 48, 48, 54, 42};
        AtomicInteger index = new AtomicInteger();
        return schema.measure(width, 1, ignored -> localizedHeaderWidths[index.getAndIncrement()]);
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
                     "guide.essence_ascendance.archive.control.back", "guide.essence_ascendance.archive.control.forward",
                     "guide.essence_ascendance.archive.search.placeholder",
                     "guide.essence_ascendance.archive.reference.item_yields.caption",
                     "guide.essence_ascendance.archive.reference.item_yields.unavailable",
                     "guide.essence_ascendance.archive.reference.item_yields.unavailable_short",
                     "guide.essence_ascendance.archive.reference.item_yields.table.item",
                     "guide.essence_ascendance.archive.reference.item_yields.filter.placeholder",
                     "guide.essence_ascendance.archive.reference.item_yields.match.any",
                     "guide.essence_ascendance.archive.reference.item_yields.match.all",
                     "guide.essence_ascendance.archive.reference.item_yields.result_count",
                     "guide.essence_ascendance.archive.reference.item_yields.essentium_note",
                     "guide.essence_ascendance.archive.reference.item_yields.essentium_link",
                     "guide.essence_ascendance.archive.control.clear",
                     "guide.essence_ascendance.archive.control.reset",
                     "guide.essence_ascendance.archive.table.item",
                     "guide.essence_ascendance.archive.table.name"))
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
        ReadOnlyDataTable<String> shortContent = new ReadOnlyDataTable<>(List.of(
                ReadOnlyDataTable.Column.text("value", Component.literal("Value"), 40, 1,
                        Component::literal, null)),
                List.of(new ReadOnlyDataTable.Row<>("short", "Short")));
        ReadOnlyDataTable<String> longContent = new ReadOnlyDataTable<>(List.of(
                ReadOnlyDataTable.Column.text("value", Component.literal("Value"), 40, 1,
                        Component::literal, null)),
                List.of(new ReadOnlyDataTable.Row<>("long", "A much longer value that must wrap without changing the schema")));
        check(shortContent.measure(120, 1, component -> component.getString().length() * 6)
                        .equals(longContent.measure(120, 1, component -> component.getString().length() * 6)),
                "Row content changed schema-driven table column widths");
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
        Font wrappingFont = new Font(ignored -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
            @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
                String value = text.getString();
                int characters = Math.max(1, width / 6);
                int lines = Math.max(1, (value.length() + characters - 1) / characters);
                return IntStream.range(0, lines).mapToObj(line -> {
                    int start = Math.min(value.length(), line * characters);
                    int end = Math.min(value.length(), start + characters);
                    return FormattedCharSequence.forward(value.substring(start, end), Style.EMPTY);
                }).toList();
            }
        };
        ReadOnlyDataTable<String> wrappedData = new ReadOnlyDataTable<>(List.of(
                ReadOnlyDataTable.Column.text("effect", Component.literal("Native effects"), 50, 1,
                        Component::literal, null)), List.of(
                new ReadOnlyDataTable.Row<>("short", "Short"),
                new ReadOnlyDataTable.Row<>("long", "A long native effect that occupies several wrapped lines")));
        ReadOnlyDataTableView<String> wrappedView = new ReadOnlyDataTableView<>(wrappedData);
        int uniformHeight = wrappedView.uniformRowHeight(wrappingFont, 80);
        wrappedView.prepare(wrappingFont, new UiBounds(0, 0, 80,
                ReadOnlyDataTableView.HEADER_HEIGHT + uniformHeight * 2 + 2));
        check(uniformHeight > ReadOnlyDataTableView.ROW_HEIGHT && wrappedView.rowHeight() == uniformHeight,
                "Wrapped table rows did not share the height required by their longest value");
        var measuredLinks = new java.util.ArrayList<Component>();
        Font linkFont = new Font(ignored -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
            @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
                if (text instanceof Component component
                        && component.getContents() instanceof TranslatableContents translated
                        && translated.getKey().equals("gui.essence_ascendance.link.related")) measuredLinks.add(component);
                return List.of(FormattedCharSequence.forward(text.getString(), Style.EMPTY));
            }
        };
        ContentViewport linkArticle = new ContentViewport((graphics, illustration, bounds, tick) -> { }, ignored -> { });
        linkArticle.prepare(linkFont, new UiBounds(0, 0, 300, 200), new SemanticDocument(Component.literal("Links"),
                List.of(new SemanticDocument.Links(List.of(
                        new SemanticDocument.Link("target/plain", Component.literal("Plain").withStyle(ChatFormatting.GOLD),
                                SemanticDocument.LinkRelation.RELATED),
                        new SemanticDocument.Link("target/emphasis", Component.literal("Emphasis").withStyle(ChatFormatting.UNDERLINE),
                                SemanticDocument.LinkRelation.RELATED))))));
        check(measuredLinks.size() == 2 && measuredLinks.stream().noneMatch(value -> value.getStyle().isUnderlined()),
                "Arrow links must not acquire automatic hyperlink underlining");
        var plainLabel = (Component) ((TranslatableContents) measuredLinks.getFirst().getContents()).getArgs()[0];
        var emphasisLabel = (Component) ((TranslatableContents) measuredLinks.getLast().getContents()).getArgs()[0];
        check(plainLabel.getStyle().getColor().getValue() == ChatFormatting.GOLD.getColor()
                        && !plainLabel.getStyle().isUnderlined() && emphasisLabel.getStyle().isUnderlined(),
                "Arrow links must retain canonical colors and explicitly authored emphasis");

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

        SemanticDocument.Table<Integer> expanded = new SemanticDocument.Table<>(data, true);
        check(expanded.layoutPolicy() == SemanticDocument.TableLayoutPolicy.ARTICLE_FLOW
                        && new SemanticDocument.Table<>(data).layoutPolicy()
                        == SemanticDocument.TableLayoutPolicy.BOUNDED_RESULTS,
                "Article-flow and bounded-results table scrolling policies are not explicit");
        check(ContentViewport.visibleTableRows(expanded) == data.rows().size(), "Expanded table clips rows into a nested viewport");
        article.restore(0);
        article.prepare(metricFont, new UiBounds(0, 0, 200, 100),
                new SemanticDocument(Component.literal("Expanded"), List.of(expanded)));
        article.click(20, 55, 0);
        article.scroll(20, 55, 0, -1);
        check(article.scrollOffset() > 0, "Wheel over an expanded table must scroll the article immediately");
        article.restore(0);
        article.prepare(metricFont, new UiBounds(0, 0, 200, 100),
                new SemanticDocument(Component.literal("Expanded"), List.of(expanded)));
        article.click(20, 55, 0);
        article.key(269, 0, 0);
        check(article.scrollOffset() > 0, "End over an expanded table must scroll the article");
        for (int width : List.of(180, 320, 640))
            check(ContentViewport.requirementTextWidth(width) == width - 29,
                    "Requirement explanations still have a narrow secondary column");

        var linkTarget = new java.util.concurrent.atomic.AtomicReference<String>();
        ReadOnlyDataTable<Integer> linkedData = new ReadOnlyDataTable<>(List.of(
                ReadOnlyDataTable.Column.number("value", Component.literal("Value"), 40, 1,
                        value -> value, value -> Component.literal(value.toString()))),
                List.of(new ReadOnlyDataTable.Row<>("first", 2, "target/first"),
                        new ReadOnlyDataTable.Row<>("second", 1, "target/second")));
        var linkedView = new ReadOnlyDataTableView<>(linkedData, linkTarget::set);
        linkedView.prepare(new UiBounds(0, 0, 100, 80), value -> value.getString().length() * 6);
        linkedView.click(10, 23, 1);
        check(linkTarget.get() == null, "Secondary click activated a table link");
        linkedView.click(10, 23, 0);
        check("target/first".equals(linkTarget.get()), "Primary click did not activate the row's target");
        linkedView.click(10, 5, 0);
        linkedView.click(10, 23, 0);
        check("target/second".equals(linkTarget.get()), "Sorting detached the target from its row");
        linkedView.key(264);
        linkedView.key(257);
        check("target/first".equals(linkTarget.get()), "Keyboard selection and Enter did not activate the stable row target");

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
