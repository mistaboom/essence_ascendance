package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.archive.ArchiveCatalog;
import com.mistaboom.essence_ascendance.archive.ArchiveEntry;
import com.mistaboom.essence_ascendance.archive.ArchiveMode;
import com.mistaboom.essence_ascendance.archive.ArchiveSection;
import com.mistaboom.essence_ascendance.client.archive.ArchiveNavigationState;
import com.mistaboom.essence_ascendance.client.archive.ArchiveNavigator;
import com.mistaboom.essence_ascendance.client.archive.ArchiveSearch;
import com.mistaboom.essence_ascendance.client.archive.ArchiveSearchField;
import com.mistaboom.essence_ascendance.client.ui.StyledTextLayout;
import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.client.ui.UiNavigationMemory;
import com.mistaboom.essence_ascendance.client.ui.content.ContentViewport;
import com.mistaboom.essence_ascendance.client.ui.content.EntryListView;
import com.mistaboom.essence_ascendance.client.ui.content.ItemIllustrationRenderer;
import com.mistaboom.essence_ascendance.client.ui.content.SemanticDocument;
import com.mistaboom.essence_ascendance.client.presentation.PresentationContext;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenComposition;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenControls;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenLayout;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenScreen;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.visual.AscendanceUiPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Shipping fullscreen Ascendance Archive consumer of the shared composition system. */
public final class AscendanceArchiveScreen extends FullscreenScreen {
    private final ArchiveCatalog catalog = ArchiveCatalog.DEFAULT;
    private final UiNavigationMemory.Session navigationSession;
    private final ArchiveNavigator navigation;
    private final EntryListView<ArchiveEntry> entries;
    private final EntryListView<ArchiveEntry> searchResults;
    private final ContentViewport article;
    private final ArchiveSearchField searchField;
    private Object listStateKey;
    private ResourceLocation articleEntry;
    private SemanticDocument articleDocument;
    private PresentationContext.Revision articleRevision;
    private PresentationContext.Revision searchRevision;
    private String cachedSearchQuery;
    private List<ArchiveEntry> cachedSearchResults = List.of();

    public AscendanceArchiveScreen() {
        super(EssenceText.guide("archive.title"));
        ClientPacketDispatch.preparePresentationConnection();
        navigationSession = ArchiveNavigationState.session();
        navigation = new ArchiveNavigator(catalog, ArchiveNavigationState.recall(navigationSession));
        entries = new EntryListView<>(this::chooseEntry);
        searchResults = new EntryListView<>(this::selectSearchResult, this::openSearchResult);
        article = new ContentViewport(ItemIllustrationRenderer.INSTANCE, this::openTarget);
        searchField = new ArchiveSearchField(navigation.query(), EssenceText.guide("archive.search.placeholder"),
                navigation::setQuery);
    }

    public static void open() { Minecraft.getInstance().setScreen(new AscendanceArchiveScreen()); }

    @Override
    protected FullscreenComposition.Scene composeFullscreen() {
        navigation.captureSectionWindow();
        ArchiveMode mode = navigation.mode();
        boolean secondary = mode != ArchiveMode.SEARCH;
        FullscreenLayout.Frame frame = FullscreenLayout.frame(FullscreenLayout.Spec.standard(), width, height, secondary);
        // Selecting another entry keeps the list's keyboard focus in this section.
        Object pageKey = mode == ArchiveMode.SEARCH ? mode : java.util.Arrays.asList(mode, navigation.section());
        FullscreenComposition.Builder builder = new FullscreenComposition.Builder(pageKey, frame)
                .modes(List.of(
                                new FullscreenComposition.Mode<>(ArchiveMode.GUIDE, ArchiveMode.GUIDE.label(), true, AscendanceUiPalette.INFORMATION),
                                new FullscreenComposition.Mode<>(ArchiveMode.REFERENCE, ArchiveMode.REFERENCE.label(), true, AscendanceUiPalette.SPECIAL),
                                new FullscreenComposition.Mode<>(ArchiveMode.SEARCH, ArchiveMode.SEARCH.label(), true, AscendanceUiPalette.INTERACTIVE)),
                        mode, this::chooseMode)
                .region(new FullscreenComposition.Region("archive/title",
                        new UiBounds(14, 7, Math.max(0, (width - frame.modes().width()) / 2 - 20), 18),
                        this::renderTitle, FullscreenComposition.Input.NONE, false));

        if (secondary) {
            List<ArchiveSection> sections = catalog.sections(mode);
            FullscreenLayout.Tabs tabs = FullscreenLayout.tabs(frame.secondary(),
                    sections.stream().map(section -> font.width(section.label())).toList(), 70, 112, 18, 3);
            navigation.fullscreenNavigation().reconcileSections(sections.stream().map(ArchiveSection::id).toList(),
                    tabs.visibleCount(), true);
            builder.sections(navigation.fullscreenNavigation(), sections.stream()
                            .map(section -> new FullscreenComposition.Section<>(section.id(), section.label(),
                                    mode == ArchiveMode.GUIDE ? AscendanceUiPalette.INFORMATION : AscendanceUiPalette.SPECIAL))
                            .toList(), tabs, this::chooseSection);
            composeArticle(builder, frame.content());
        } else {
            composeSearch(builder, frame.content());
        }

        if (navigation.canGoBack()) {
            builder.control(new FullscreenComposition.Control("archive/back",
                    new UiBounds(14, Math.max(0, height - 16), Math.min(84, Math.max(0, width - 28)), 14),
                    EssenceText.guide("archive.control.back"), true, false, FullscreenControls.Style.LINK,
                    AscendanceUiPalette.INTERACTIVE, this::back));
        }
        return builder.build();
    }

    @Override public void removed() {
        captureCurrentOffsets();
        ArchiveNavigationState.remember(navigationSession, navigation.snapshot());
        super.removed();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override protected void renderFullscreenTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (navigation.mode() != ArchiveMode.SEARCH) article.renderTooltips(graphics, mouseX, mouseY);
    }

    private void composeArticle(FullscreenComposition.Builder builder, UiBounds content) {
        FullscreenLayout.Split split = FullscreenLayout.listDetail(content, Math.min(190, Math.max(112, content.width() / 3)), 8);
        List<ArchiveEntry> available = catalog.entries(navigation.mode(), navigation.section());
        Object nextListKey = java.util.Arrays.asList(navigation.mode(), navigation.section(), navigation.entryId());
        if (!Objects.equals(listStateKey, nextListKey)) {
            entries.restore(navigation.entryId() == null ? null : navigation.entryId().toString(), navigation.listScroll());
            listStateKey = nextListKey;
        }
        entries.prepare(split.list(), available.stream().map(this::listEntry).toList());
        builder.region(new FullscreenComposition.Region("archive/entries", split.list(),
                (graphics, x, y, tick) -> entries.render(graphics, font, x, y), entries, true));

        ArchiveEntry selected = navigation.entry();
        if (selected == null) return;
        PresentationContext.Revision revision = PresentationContext.capture().revision();
        if (!selected.id().equals(articleEntry) || !Objects.equals(articleRevision, revision)) {
            articleEntry = selected.id();
            articleDocument = selected.content().get();
            articleRevision = revision;
            article.restore(navigation.articleScroll());
        }
        article.prepare(font, split.detail(), articleDocument);
        builder.region(new FullscreenComposition.Region("archive/article", split.detail(), article::render, article, true))
                .primaryInput("archive/article");
    }

    private void composeSearch(FullscreenComposition.Builder builder, UiBounds content) {
        FullscreenLayout.Bands bands = FullscreenLayout.bands(content, 24, 0, 6);
        if (!searchField.query().equals(navigation.query())) searchField.query(navigation.query());
        UiBounds fieldBounds = bands.header();
        searchField.bounds(fieldBounds);
        builder.region(new FullscreenComposition.Region("archive/search/field", fieldBounds,
                (graphics, x, y, tick) -> searchField.render(graphics, font), searchField, true));

        PresentationContext.Revision revision = PresentationContext.capture().revision();
        if (!Objects.equals(searchRevision, revision) || !Objects.equals(cachedSearchQuery, navigation.query())) {
            cachedSearchResults = ArchiveSearch.results(catalog, navigation.query());
            cachedSearchQuery = navigation.query();
            searchRevision = revision;
        }
        List<ArchiveEntry> results = cachedSearchResults;
        if (results.isEmpty()) navigation.selectSearchResult(null);
        else if (navigation.selectedResult() == null
                || results.stream().noneMatch(entry -> entry.id().equals(navigation.selectedResult()))) {
            navigation.selectSearchResult(results.getFirst().id());
        }
        Object nextListKey = List.of(ArchiveMode.SEARCH, navigation.query());
        if (!Objects.equals(listStateKey, nextListKey)) {
            searchResults.restore(navigation.selectedResult() == null ? null : navigation.selectedResult().toString(),
                    navigation.listScroll());
            listStateKey = nextListKey;
        }
        UiBounds resultBounds = bands.body();
        searchResults.prepare(resultBounds, results.stream().map(this::listEntry).toList());
        builder.region(new FullscreenComposition.Region("archive/search/results", resultBounds,
                (graphics, x, y, tick) -> searchResults.render(graphics, font, x, y), searchResults, true));
    }

    private void renderTitle(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiBounds bounds = fullscreen.scene().regions().stream().filter(region -> region.id().equals("archive/title"))
                .findFirst().orElseThrow().bounds();
        graphics.drawString(font, StyledTextLayout.fit(font, title, bounds.width()), bounds.x(), bounds.y() + 4,
                AscendanceUiPalette.argb(AscendanceUiPalette.PRIMARY_TEXT), false);
    }

    private EntryListView.Entry<ArchiveEntry> listEntry(ArchiveEntry entry) {
        return new EntryListView.Entry<>(entry.id().toString(), entry.title(), entry.summary(), entry);
    }

    private void chooseMode(ArchiveMode mode) { captureCurrentOffsets(); navigation.selectMode(mode); invalidateLocation(); }
    private void chooseSection(ResourceLocation section) { captureCurrentOffsets(); navigation.selectSection(section); invalidateLocation(); }
    private void chooseEntry(ArchiveEntry entry) { captureCurrentOffsets(); navigation.selectEntry(entry.id()); }
    private void selectSearchResult(ArchiveEntry entry) { navigation.selectSearchResult(entry.id()); }
    private void openTarget(String target) { captureCurrentOffsets(); navigation.openTarget(target); invalidateLocation(); }
    private void openSearchResult(ArchiveEntry entry) {
        captureCurrentOffsets();
        navigation.selectSearchResult(entry.id());
        navigation.openSelectedSearchResult();
        invalidateLocation();
    }
    private void back() { captureCurrentOffsets(); navigation.back(); invalidateLocation(); }

    private void invalidateLocation() {
        listStateKey = null;
        articleEntry = null;
        articleRevision = null;
    }

    private void captureCurrentOffsets() {
        navigation.captureSectionWindow();
        if (navigation.mode() == ArchiveMode.SEARCH) navigation.setListScroll(searchResults.scrollOffset());
        else {
            navigation.setListScroll(entries.scrollOffset());
            navigation.setArticleScroll(article.scrollOffset());
        }
    }
}
