package com.mistaboom.essence_ascendance.client.archive;

import com.mistaboom.essence_ascendance.archive.ArchiveCatalog;
import com.mistaboom.essence_ascendance.archive.ArchiveEntry;
import com.mistaboom.essence_ascendance.archive.ArchiveLocation;
import com.mistaboom.essence_ascendance.archive.ArchiveMode;
import com.mistaboom.essence_ascendance.archive.ArchiveSection;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenNavigation;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Archive-owned typed navigation, independent mode locations and bounded canonical history. */
public final class ArchiveNavigator {
    public static final int HISTORY_LIMIT = 32;

    private final ArchiveCatalog catalog;
    private final FullscreenNavigation<ArchiveMode, ResourceLocation> navigation =
            new FullscreenNavigation<>(ArchiveMode.GUIDE);
    private final Map<ArchiveMode, ArchiveNavigationState.Page> pages = new EnumMap<>(ArchiveMode.class);
    private final List<ArchiveNavigationState.Visit> history = new ArrayList<>();
    private final List<ArchiveNavigationState.Visit> future = new ArrayList<>();
    private ArchiveNavigationState.Search search = ArchiveNavigationState.Search.initial();

    public ArchiveNavigator(ArchiveCatalog catalog, ArchiveNavigationState.Snapshot restored) {
        this.catalog = catalog;
        pages.putAll(restored.pages());
        search = restored.search();
        history.addAll(restored.history().stream().skip(Math.max(0, restored.history().size() - HISTORY_LIMIT)).toList());
        future.addAll(restored.future().stream().skip(Math.max(0, restored.future().size() - HISTORY_LIMIT)).toList());
        for (ArchiveMode mode : List.of(ArchiveMode.GUIDE, ArchiveMode.REFERENCE)) {
            ArchiveNavigationState.Page page = validPage(mode, pages.get(mode));
            pages.put(mode, page);
            navigation.restoreMode(mode, page.section(), page.sectionWindow());
        }
        navigation.selectMode(restored.mode());
        if (restored.mode() != ArchiveMode.SEARCH) syncCurrentPage();
        reconcileSearch();
    }

    public ArchiveMode mode() { return navigation.mode(); }
    public ResourceLocation section() { return mode() == ArchiveMode.SEARCH ? null : page(mode()).section(); }
    public ResourceLocation entryId() { return mode() == ArchiveMode.SEARCH ? search.selectedResult() : page(mode()).entry(); }
    public ArchiveEntry entry() { return catalog.entry(entryId()); }
    public int sectionWindow() { return mode() == ArchiveMode.SEARCH ? 0 : navigation.sectionWindow(); }
    public int listScroll() { return mode() == ArchiveMode.SEARCH ? search.scroll() : page(mode()).listScroll(); }
    public int articleScroll() { return mode() == ArchiveMode.SEARCH ? 0 : page(mode()).articleScroll(); }
    public String query() { return search.query(); }
    public ResourceLocation selectedResult() { return search.selectedResult(); }
    public boolean canGoBack() { return !history.isEmpty(); }
    public boolean canGoForward() { return !future.isEmpty(); }
    public FullscreenNavigation<ArchiveMode, ResourceLocation> fullscreenNavigation() { return navigation; }

    /** Capture tab paging performed by the shared section controls. */
    public void captureSectionWindow() {
        if (mode() == ArchiveMode.SEARCH) return;
        ArchiveNavigationState.Page page = page(mode());
        pages.put(mode(), new ArchiveNavigationState.Page(page.section(), page.entry(), navigation.sectionWindow(),
                page.listScroll(), page.articleScroll()));
    }

    public void selectMode(ArchiveMode mode) {
        if (mode == mode()) return;
        pushCurrent();
        navigation.selectMode(mode);
        if (mode != ArchiveMode.SEARCH) syncCurrentPage();
    }

    public void selectSection(ResourceLocation section) {
        ArchiveSection candidate = catalog.section(section);
        if (candidate == null || candidate.mode() != mode() || section.equals(this.section())) return;
        pushCurrent();
        navigation.selectSection(section);
        ArchiveEntry first = catalog.first(mode(), section);
        pages.put(mode(), new ArchiveNavigationState.Page(section, first == null ? null : first.id(),
                navigation.sectionWindow(), 0, 0));
    }

    public void sectionWindow(int start) {
        navigation.sectionWindow(start);
        ArchiveNavigationState.Page page = page(mode());
        pages.put(mode(), new ArchiveNavigationState.Page(page.section(), page.entry(), navigation.sectionWindow(),
                page.listScroll(), page.articleScroll()));
    }

    public void selectEntry(ResourceLocation entry) {
        ArchiveEntry candidate = catalog.entry(entry);
        if (candidate == null || candidate.mode() != mode() || candidate.id().equals(entryId())) return;
        pushCurrent();
        int listOffset = candidate.section().equals(section()) ? listScroll() : 0;
        if (!candidate.section().equals(section())) navigation.selectSection(candidate.section());
        pages.put(mode(), new ArchiveNavigationState.Page(candidate.section(), candidate.id(), navigation.sectionWindow(), listOffset, 0));
    }

    public void openTarget(String target) {
        ResourceLocation id = ResourceLocation.tryParse(target);
        ArchiveEntry candidate = id == null ? null : catalog.entry(id);
        if (candidate == null || candidate.mode() == mode() && candidate.id().equals(entryId())) return;
        pushCurrent();
        navigation.selectMode(candidate.mode());
        navigation.selectSection(candidate.section());
        pages.put(candidate.mode(), new ArchiveNavigationState.Page(candidate.section(), candidate.id(),
                navigation.sectionWindow(), 0, 0));
    }

    public void setArticleScroll(int offset) {
        if (mode() == ArchiveMode.SEARCH) return;
        ArchiveNavigationState.Page page = page(mode());
        pages.put(mode(), new ArchiveNavigationState.Page(page.section(), page.entry(), page.sectionWindow(),
                page.listScroll(), offset));
    }

    public void setListScroll(int offset) {
        if (mode() == ArchiveMode.SEARCH) search = new ArchiveNavigationState.Search(search.query(), search.selectedResult(), offset);
        else {
            ArchiveNavigationState.Page page = page(mode());
            pages.put(mode(), new ArchiveNavigationState.Page(page.section(), page.entry(), page.sectionWindow(),
                    offset, page.articleScroll()));
        }
    }

    public void setQuery(String query) { search = new ArchiveNavigationState.Search(query, search.selectedResult(), 0); reconcileSearch(); }
    public void selectSearchResult(ResourceLocation entry) {
        if (entry != null && catalog.entry(entry) == null) entry = null;
        search = new ArchiveNavigationState.Search(search.query(), entry, search.scroll());
    }

    public void openSelectedSearchResult() {
        ArchiveEntry selected = catalog.entry(search.selectedResult());
        if (selected != null) openTarget(selected.id().toString());
    }

    public void back() {
        if (history.isEmpty()) return;
        appendVisit(future, currentVisit());
        apply(history.removeLast());
    }

    public void forward() {
        if (future.isEmpty()) return;
        appendVisit(history, currentVisit());
        apply(future.removeLast());
    }

    public ArchiveLocation currentLocation() {
        return mode() == ArchiveMode.SEARCH
                ? new ArchiveLocation.Search(search.query(), search.selectedResult())
                : new ArchiveLocation.Article(mode(), section(), entryId());
    }

    public ArchiveNavigationState.Snapshot snapshot() {
        if (mode() != ArchiveMode.SEARCH) syncCurrentPage();
        return new ArchiveNavigationState.Snapshot(mode(), pages, search, history, future);
    }

    private void pushCurrent() {
        appendVisit(history, currentVisit());
        future.clear();
    }

    private ArchiveNavigationState.Visit currentVisit() {
        return new ArchiveNavigationState.Visit(currentLocation(), sectionWindow(), listScroll(), articleScroll());
    }

    private static void appendVisit(List<ArchiveNavigationState.Visit> stack, ArchiveNavigationState.Visit visit) {
        if (!stack.isEmpty() && stack.getLast().location().equals(visit.location())) stack.removeLast();
        stack.add(visit);
        while (stack.size() > HISTORY_LIMIT) stack.removeFirst();
    }

    private void apply(ArchiveNavigationState.Visit visit) {
        ArchiveLocation location = visit.location();
        if (location instanceof ArchiveLocation.Article article) {
            ArchiveEntry entry = catalog.entry(article.entry());
            if (entry == null) {
                ArchiveNavigationState.Page fallback = validPage(article.mode(), null);
                navigation.selectMode(article.mode()); navigation.selectSection(fallback.section());
                pages.put(article.mode(), fallback);
            } else {
                navigation.selectMode(entry.mode());
                navigation.restoreMode(entry.mode(), entry.section(), visit.sectionWindow());
                pages.put(entry.mode(), new ArchiveNavigationState.Page(entry.section(), entry.id(),
                        visit.sectionWindow(), visit.listScroll(), visit.articleScroll()));
            }
        } else if (location instanceof ArchiveLocation.Search searched) {
            navigation.selectMode(ArchiveMode.SEARCH);
            search = new ArchiveNavigationState.Search(searched.query(), searched.selectedResult(), visit.listScroll());
            reconcileSearch();
        } else if (location instanceof ArchiveLocation.YieldBrowser) {
            navigation.selectMode(ArchiveMode.REFERENCE);
            pages.put(ArchiveMode.REFERENCE, validPage(ArchiveMode.REFERENCE, pages.get(ArchiveMode.REFERENCE)));
            syncCurrentPage();
        }
    }

    private void syncCurrentPage() {
        ArchiveNavigationState.Page page = validPage(mode(), pages.get(mode()));
        navigation.restoreMode(mode(), page.section(), page.sectionWindow());
        pages.put(mode(), page);
    }

    private ArchiveNavigationState.Page validPage(ArchiveMode mode, ArchiveNavigationState.Page requested) {
        List<ArchiveSection> sections = catalog.sections(mode);
        if (sections.isEmpty()) return new ArchiveNavigationState.Page(null, null, 0, 0, 0);
        ResourceLocation section = requested == null ? sections.getFirst().id() : requested.section();
        if (catalog.section(section) == null || catalog.section(section).mode() != mode) section = sections.getFirst().id();
        ArchiveEntry selected = requested == null ? null : catalog.entry(requested.entry());
        if (selected == null || selected.mode() != mode || !selected.section().equals(section)) selected = catalog.first(mode, section);
        return new ArchiveNavigationState.Page(section, selected == null ? null : selected.id(),
                requested == null ? 0 : requested.sectionWindow(), requested == null ? 0 : requested.listScroll(),
                requested == null ? 0 : requested.articleScroll());
    }

    private void reconcileSearch() {
        if (search.selectedResult() != null && catalog.entry(search.selectedResult()) == null)
            search = new ArchiveNavigationState.Search(search.query(), null, search.scroll());
    }

    private ArchiveNavigationState.Page page(ArchiveMode mode) { return pages.get(mode); }
}
