package com.mistaboom.essence_ascendance.client.ui.fullscreen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Navigation by stable mode/section identities, independent of content and host
 * lifecycle. Modes may have no sections. Selection methods report changes so
 * the host can reset only its domain-specific dependent content, when needed.
 */
public final class FullscreenNavigation<M, S> {
    public record Location<S>(S section, int sectionWindow) {
        public Location { sectionWindow = Math.max(0, sectionWindow); }
    }

    private final Map<M, Location<S>> locations = new LinkedHashMap<>();
    private M mode;

    public FullscreenNavigation(M initialMode) {
        mode = Objects.requireNonNull(initialMode, "Initial mode");
        locations.put(mode, new Location<>(null, 0));
    }

    public M mode() { return mode; }
    public S section() { return location().section(); }
    public int sectionWindow() { return location().sectionWindow(); }

    public boolean selectMode(M selected) {
        Objects.requireNonNull(selected, "Selected mode");
        if (Objects.equals(mode, selected)) return false;
        mode = selected;
        locations.putIfAbsent(mode, new Location<>(null, 0));
        return true;
    }

    /** A section change retains the user's tab window; the host owns dependent content changes. */
    public boolean selectSection(S selected) {
        if (Objects.equals(section(), selected)) return false;
        locations.put(mode, new Location<>(selected, sectionWindow()));
        return true;
    }

    public void sectionWindow(int start) {
        locations.put(mode, new Location<>(section(), start));
    }

    /** Restores one mode without activating it or mutating another mode's location. */
    public void restoreMode(M restoredMode, S section, int sectionWindow) {
        locations.put(Objects.requireNonNull(restoredMode, "Restored mode"), new Location<>(section, sectionWindow));
    }

    public Map<M, Location<S>> locations() { return Map.copyOf(locations); }

    /**
     * Temporary unavailable content is not evidence of removal. An authoritative
     * list resolves stable IDs after reorder, falls back after removal, and clamps
     * the tab window without forcing a manually paged window back to selection.
     */
    public void reconcileSections(List<S> available, int visibleCount, boolean authoritative) {
        Objects.requireNonNull(available, "Available sections");
        if (!authoritative) return;
        if (available.isEmpty()) {
            locations.put(mode, new Location<>(null, 0));
            return;
        }
        S resolved = available.get(sectionIndex(section(), available));
        int maximumWindow = Math.max(0, available.size() - Math.max(1, visibleCount));
        locations.put(mode, new Location<>(resolved, Math.min(sectionWindow(), maximumWindow)));
    }

    public static <M> M availableMode(M requested, M fallback, boolean authoritative, Predicate<M> available) {
        Objects.requireNonNull(available, "Mode availability");
        return authoritative && !available.test(requested) ? fallback : requested;
    }

    public static <S> int sectionIndex(S requested, List<S> available) {
        return Math.max(0, requested == null ? 0 : available.indexOf(requested));
    }

    private Location<S> location() { return locations.get(mode); }
}
