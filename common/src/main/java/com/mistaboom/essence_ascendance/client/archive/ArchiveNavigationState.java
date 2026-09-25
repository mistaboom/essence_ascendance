package com.mistaboom.essence_ascendance.client.archive;

import com.mistaboom.essence_ascendance.archive.ArchiveLocation;
import com.mistaboom.essence_ascendance.archive.ArchiveMode;
import com.mistaboom.essence_ascendance.client.ui.UiNavigationMemory;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Session-only Archive snapshot adapter backed by the shared bounded memory primitive. */
public final class ArchiveNavigationState {
    private static final UiNavigationMemory<String, Snapshot> MEMORY = new UiNavigationMemory<>(2);
    private static final String KEY = "ascendance_archive";
    private ArchiveNavigationState() { }

    public record Page(ResourceLocation section, ResourceLocation entry, int sectionWindow,
                       int listScroll, int articleScroll) {
        public Page {
            sectionWindow = Math.max(0, sectionWindow);
            listScroll = Math.max(0, listScroll);
            articleScroll = Math.max(0, articleScroll);
        }
    }

    public record Search(String query, String selectedResult, int scroll) {
        public Search { query = query == null ? "" : query; scroll = Math.max(0, scroll); }
        public static Search initial() { return new Search("", null, 0); }
    }

    public enum YieldMatch { ANY, ALL }
    public enum YieldSortDirection { ASCENDING, DESCENDING }

    /** Mutable browser controls represented as one immutable current-location value. */
    public record YieldBrowser(String query, Set<ResourceLocation> selectedEssences, YieldMatch match,
                               String sortColumn, YieldSortDirection sortDirection,
                               ResourceLocation selectedRow, int scroll, boolean revealSelection) {
        public YieldBrowser(String query, Set<ResourceLocation> selectedEssences, YieldMatch match,
                            String sortColumn, YieldSortDirection sortDirection, ResourceLocation selectedRow, int scroll) {
            this(query, selectedEssences, match, sortColumn, sortDirection, selectedRow, scroll, false);
        }
        public YieldBrowser {
            query = query == null ? "" : query;
            selectedEssences = Set.copyOf(selectedEssences == null ? Set.of() : selectedEssences);
            match = match == null ? YieldMatch.ANY : match;
            sortColumn = sortColumn == null ? "name" : sortColumn;
            sortDirection = sortDirection == null ? YieldSortDirection.ASCENDING : sortDirection;
            scroll = Math.max(0, scroll);
        }
        public static YieldBrowser initial() {
            return new YieldBrowser("", Set.of(), YieldMatch.ANY, "name",
                    YieldSortDirection.ASCENDING, null, 0);
        }
    }

    /** One history visit, including the viewport at the moment the player left it. */
    public record Visit(ArchiveLocation location, int sectionWindow, int listScroll, int articleScroll,
                        YieldBrowser yieldBrowser) {
        public Visit(ArchiveLocation location, int sectionWindow, int listScroll, int articleScroll) {
            this(location, sectionWindow, listScroll, articleScroll, YieldBrowser.initial());
        }
        public Visit {
            sectionWindow = Math.max(0, sectionWindow);
            listScroll = Math.max(0, listScroll);
            articleScroll = Math.max(0, articleScroll);
            yieldBrowser = yieldBrowser == null ? YieldBrowser.initial() : yieldBrowser;
        }
    }

    public record Snapshot(ArchiveMode mode, Map<ArchiveMode, Page> pages, Search search,
                           YieldBrowser yieldBrowser, List<Visit> history, List<Visit> future) {
        public Snapshot(ArchiveMode mode, Map<ArchiveMode, Page> pages, Search search,
                        List<Visit> history, List<Visit> future) {
            this(mode, pages, search, YieldBrowser.initial(), history, future);
        }
        public Snapshot {
            mode = mode == null ? ArchiveMode.GUIDE : mode;
            pages = Map.copyOf(pages);
            search = search == null ? Search.initial() : search;
            yieldBrowser = yieldBrowser == null ? YieldBrowser.initial() : yieldBrowser;
            history = List.copyOf(history);
            future = List.copyOf(future);
        }
        public static Snapshot initial() { return new Snapshot(ArchiveMode.GUIDE, Map.of(), Search.initial(),
                YieldBrowser.initial(), List.of(), List.of()); }
    }

    public static UiNavigationMemory.Session session() { return MEMORY.session(); }
    public static Snapshot recall(UiNavigationMemory.Session session) { return MEMORY.recall(session, KEY, Snapshot.initial()); }
    public static void remember(UiNavigationMemory.Session session, Snapshot snapshot) { MEMORY.remember(session, KEY, snapshot); }
    public static void clearSession() { MEMORY.clearSession(); }
}
