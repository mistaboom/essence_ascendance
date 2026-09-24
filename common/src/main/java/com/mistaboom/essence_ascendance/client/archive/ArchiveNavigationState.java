package com.mistaboom.essence_ascendance.client.archive;

import com.mistaboom.essence_ascendance.archive.ArchiveLocation;
import com.mistaboom.essence_ascendance.archive.ArchiveMode;
import com.mistaboom.essence_ascendance.client.ui.UiNavigationMemory;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

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

    public record Search(String query, ResourceLocation selectedResult, int scroll) {
        public Search { query = query == null ? "" : query; scroll = Math.max(0, scroll); }
        public static Search initial() { return new Search("", null, 0); }
    }

    public record Snapshot(ArchiveMode mode, Map<ArchiveMode, Page> pages, Search search,
                           List<ArchiveLocation> history) {
        public Snapshot {
            mode = mode == null ? ArchiveMode.GUIDE : mode;
            pages = Map.copyOf(pages);
            search = search == null ? Search.initial() : search;
            history = List.copyOf(history);
        }
        public static Snapshot initial() { return new Snapshot(ArchiveMode.GUIDE, Map.of(), Search.initial(), List.of()); }
    }

    public static UiNavigationMemory.Session session() { return MEMORY.session(); }
    public static Snapshot recall(UiNavigationMemory.Session session) { return MEMORY.recall(session, KEY, Snapshot.initial()); }
    public static void remember(UiNavigationMemory.Session session, Snapshot snapshot) { MEMORY.remember(session, KEY, snapshot); }
    public static void clearSession() { MEMORY.clearSession(); }
}
