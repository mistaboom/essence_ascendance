package com.mistaboom.essence_ascendance.client.nexus;

import com.mistaboom.essence_ascendance.client.ui.UiNavigationMemory;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenNavigation;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Client-session navigation only. Never retains player objects, draft allocations or transactions. */
public final class NexusNavigationState {
    private static final int REMEMBERED_PLAYERS = 8;
    private static final UiNavigationMemory<UUID, Snapshot> RECENT = new UiNavigationMemory<>(REMEMBERED_PLAYERS);
    private NexusNavigationState() { }

    public record Page(ResourceLocation category, int tabWindow, int trackWindow) {
        public Page { tabWindow = Math.max(0, tabWindow); trackWindow = Math.max(0, trackWindow); }
        public static Page initial() { return new Page(null, 0, 0); }
    }
    public record Attunement(String category, int scroll, int focusedIndex, String focusedId) {
        public Attunement { scroll = Math.max(0, scroll); focusedIndex = Math.max(-1, focusedIndex); }
        public Attunement(String category, int scroll, int focusedIndex) { this(category, scroll, focusedIndex, null); }
        public static Attunement initial() { return new Attunement(null, 0, -1); }
    }
    public record Snapshot(NexusMode mode, Map<NexusMode, Page> pages, Attunement attunement,
                           Map<ResourceLocation, Double> skillScrollX, Map<ResourceLocation, Double> skillScrollY) {
        public Snapshot {
            mode = mode == null ? NexusMode.ASCENDANCE : mode;
            pages = Map.copyOf(pages);
            attunement = attunement == null ? Attunement.initial() : attunement;
            skillScrollX = Map.copyOf(skillScrollX); skillScrollY = Map.copyOf(skillScrollY);
        }
        public Page page(NexusMode mode) { return pages.getOrDefault(mode, Page.initial()); }
        public static Snapshot initial() { return new Snapshot(NexusMode.ASCENDANCE, Map.of(), Attunement.initial(), Map.of(), Map.of()); }
    }
    public static UiNavigationMemory.Session session() { return RECENT.session(); }
    public static Snapshot recall(UUID player) { return recall(session(), player); }
    public static Snapshot recall(UiNavigationMemory.Session session, UUID player) {
        return RECENT.recall(session, player, Snapshot.initial());
    }
    public static void remember(UUID player, Snapshot snapshot) {
        remember(session(), player, snapshot);
    }
    public static void remember(UiNavigationMemory.Session session, UUID player, Snapshot snapshot) {
        RECENT.remember(session, player, snapshot);
    }
    public static void clearSession() { RECENT.clearSession(); }
    /** An initial synchronization wait is not evidence that a remembered powered screen is locked. */
    public static NexusMode availableMode(NexusMode requested, boolean ready, Predicate<NexusMode> available) {
        return FullscreenNavigation.availableMode(requested, NexusMode.ASCENDANCE, ready, available);
    }
    /** Registry identity survives category reorder; removed categories fall back to the first available tab. */
    public static int categoryIndex(ResourceLocation requested, List<ResourceLocation> available) {
        return FullscreenNavigation.sectionIndex(requested, available);
    }
}
