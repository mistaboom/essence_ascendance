package com.mistaboom.essence_ascendance.client.ui.fullscreen;

import com.mistaboom.essence_ascendance.client.ui.UiNavigationMemory;

import java.util.List;
import java.util.Map;

/** Navigation/session invariants require no Minecraft bootstrap or live host. */
public final class FullscreenNavigationTest {
    private static int checks;

    public static void main(String[] args) {
        independentLocations();
        synchronizationAndRemoval();
        sessionMemory();
        System.out.println("FullscreenNavigationTest: " + checks + " checks PASS");
    }

    private static void independentLocations() {
        var navigation = new FullscreenNavigation<String, String>("guide");
        navigation.restoreMode("guide", "getting_started", 2);
        navigation.restoreMode("reference", "skills", 4);
        check(navigation.mode().equals("guide") && navigation.section().equals("getting_started"),
                "Restoring other modes does not activate them");
        check(!navigation.selectMode("guide") && !navigation.selectSection("getting_started"),
                "Repeated selection does not emit a content-change intent");
        check(navigation.selectMode("reference") && navigation.section().equals("skills")
                        && navigation.sectionWindow() == 4,
                "Each mode owns its section and tab window");
        navigation.selectSection("bonuses");
        check(navigation.sectionWindow() == 4, "Section selection preserves deliberate tab paging");
        navigation.selectMode("search");
        navigation.reconcileSections(List.of(), 0, true);
        check(navigation.section() == null && navigation.sectionWindow() == 0,
                "A mode without secondary navigation has a valid empty location");
        navigation.selectMode("guide");
        check(navigation.section().equals("getting_started") && navigation.sectionWindow() == 2,
                "Visiting a mode without sections preserves the other modes' locations");
        Map<String, FullscreenNavigation.Location<String>> captured = navigation.locations();
        navigation.sectionWindow(-10);
        check(navigation.sectionWindow() == 0 && captured.get("guide").sectionWindow() == 2,
                "Snapshots are detached and negative tab offsets clamp");
        boolean immutable = false;
        try { captured.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "Navigation location snapshots are immutable");
    }

    private static void synchronizationAndRemoval() {
        var navigation = new FullscreenNavigation<String, String>("reference");
        navigation.restoreMode("reference", "skills", 3);
        navigation.reconcileSections(List.of(), 1, false);
        check(navigation.section().equals("skills") && navigation.sectionWindow() == 3,
                "A temporary empty synchronization result preserves remembered location");
        navigation.reconcileSections(List.of("bonuses"), 1, false);
        check(navigation.section().equals("skills") && navigation.sectionWindow() == 3,
                "A temporary partial synchronization result preserves remembered location");
        navigation.reconcileSections(List.of("skills", "bonuses", "tiers"), 2, true);
        check(navigation.section().equals("skills") && navigation.sectionWindow() == 1,
                "Stable section identity survives reorder while the viewport reflows");
        navigation.reconcileSections(List.of("tiers", "bonuses"), 1, true);
        check(navigation.section().equals("tiers") && navigation.sectionWindow() == 1,
                "Authoritative section removal falls back to the first available identity");
        navigation.reconcileSections(List.of(), 2, true);
        check(navigation.section() == null && navigation.sectionWindow() == 0,
                "An authoritative empty section set clears its stale location");
        check(FullscreenNavigation.availableMode("reference", "guide", false,
                        mode -> { throw new AssertionError("Temporary mode availability must not be consulted"); }).equals("reference"),
                "Unavailable synchronization never discards a remembered mode");
        check(FullscreenNavigation.availableMode("reference", "guide", true, mode -> false).equals("guide"),
                "Authoritative mode unavailability chooses the host-provided fallback");
        check(FullscreenNavigation.sectionIndex("skills", List.of("bonuses", "skills")) == 1
                        && FullscreenNavigation.sectionIndex("removed", List.of("bonuses")) == 0,
                "Index resolution follows identity and has a safe removed-content fallback");
    }

    private static void sessionMemory() {
        var memory = new UiNavigationMemory<String, RememberedPage>(2);
        var session = memory.session();
        var initial = new RememberedPage("guide", 0);
        var guide = new RememberedPage("guide", 10);
        var reference = new RememberedPage("reference", 20);
        check(memory.recall(session, "first", initial).equals(initial), "Missing keys use the typed initial snapshot");
        memory.remember(session, "first", guide);
        memory.remember(session, "second", reference);
        check(memory.recall(session, "first", initial).equals(guide)
                        && memory.recall(session, "second", initial).equals(reference),
                "Independent stable keys keep their typed snapshots");
        memory.remember(session, "first", reference);
        memory.remember(session, "third", guide);
        check(memory.recall(session, "second", initial).equals(initial)
                        && memory.recall(session, "first", initial).equals(reference),
                "Bounded memory evicts the oldest saved key and replacement refreshes save recency");
        memory.clearSession();
        var nextSession = memory.session();
        check(nextSession != session && memory.recall(nextSession, "first", initial).equals(initial),
                "Connection reset starts a fresh memory scope");
        check(!memory.remember(session, "first", reference)
                        && memory.recall(nextSession, "first", initial).equals(initial),
                "An old screen's late save cannot repopulate a new connection");
        memory.remember(nextSession, "first", guide);
        check(memory.recall(session, "first", initial).equals(initial)
                        && memory.recall(nextSession, "first", initial).equals(guide),
                "An old session cannot read a new connection's snapshot");
    }

    private record RememberedPage(String mode, int scroll) { }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
