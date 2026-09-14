package com.mistaboom.essence_ascendance.client.nexus;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Client-session navigation only. Never retains player objects, draft allocations or transactions. */
public final class NexusNavigationState {
    private static final int REMEMBERED_PLAYERS = 8;
    private static final Map<UUID, Snapshot> RECENT = new LinkedHashMap<>();
    private NexusNavigationState() { }

    public record Page(ResourceLocation category, int tabWindow, int trackWindow) {
        public Page { tabWindow = Math.max(0, tabWindow); trackWindow = Math.max(0, trackWindow); }
        public static Page initial() { return new Page(null, 0, 0); }
    }
    public record Attunement(String category, int scroll, int focusedIndex) {
        public Attunement { scroll = Math.max(0, scroll); focusedIndex = Math.max(-1, focusedIndex); }
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
    public static Snapshot recall(UUID player) { return RECENT.getOrDefault(player, Snapshot.initial()); }
    public static void remember(UUID player, Snapshot snapshot) {
        RECENT.remove(player); RECENT.put(player, snapshot);
        while (RECENT.size() > REMEMBERED_PLAYERS) RECENT.remove(RECENT.keySet().iterator().next());
    }
    /** An initial synchronization wait is not evidence that a remembered powered screen is locked. */
    public static NexusMode availableMode(NexusMode requested, boolean ready, Predicate<NexusMode> available) {
        return ready && !available.test(requested) ? NexusMode.ASCENDANCE : requested;
    }
    /** Registry identity survives category reorder; removed categories fall back to the first available tab. */
    public static int categoryIndex(ResourceLocation requested, List<ResourceLocation> available) {
        int found = requested == null ? 0 : available.indexOf(requested);
        return Math.max(0, found);
    }
}
