package com.mistaboom.essence_ascendance.client.nexus;

/** Responsive geometry and asynchronous handoff invariants, independent of a live OpenGL context. */
public final class NexusAttunementPresentationTest {
    private static int checks;
    public static void main(String[] args) {
        for (int width : new int[] {280, 360, 480, 720}) {
            for (int height : new int[] {120, 180, 280, 400}) {
                for (int count : new int[] {1, 2, 3, 6, 8, 12}) {
                    var layout = NexusConstellationLayout.create(12, 30, width, height, count);
                    check(layout.equals(NexusConstellationLayout.create(12, 30, width, height, count)), "Deterministic layout");
                    check(layout.nodes().size() == count, "Every registered category receives one node");
                    for (var node : layout.nodes()) {
                        check(node.x() - node.radius() >= 12 && node.x() + node.radius() < 12 + width,
                                "Crystal inside horizontal viewport");
                        check(node.y() - node.radius() >= 30 && node.y() + node.radius() < 30 + height,
                                "Crystal inside vertical viewport");
                        check(node.contains(node.x(), node.y()), "Crystal hit region includes center");
                        check(!node.contains(node.x() + node.radius() + 6, node.y()), "Hit region excludes unrelated space");
                    }
                    if (count == 6) {
                        check(layout.medallionRadius() >= 17, "Narrow supported layout retains readable central medallion");
                        for (var node : layout.nodes()) {
                            int labelY = node.y() + (Math.sin(node.angle()) < -0.1 ? -node.radius() - 27 : node.radius() + 13);
                            check(labelY >= 30 && labelY + 19 <= 30 + height, "Two-line category caption remains visible");
                            check(Math.hypot(node.x() - layout.centerX(), node.y() - layout.centerY())
                                            > node.radius() + layout.medallionRadius(), "Category and medallion do not overlap");
                        }
                    }
                }
            }
        }
        var handoff = new NexusAscensionHandoff();
        handoff.begin("dormant");
        check(!handoff.ready(true, 99, "awakened"), "Sending request never closes the Nexus");
        handoff.acknowledge(false, false, 5);
        check(!handoff.ready(true, 99, "awakened"), "Rejected or stale request leaves Nexus open");
        handoff.acknowledge(true, false, 5);
        check(!handoff.ready(true, 99, "awakened"), "Ordinary Bonus/skill commit does not animate Ascension");
        handoff.acknowledge(true, true, 8);
        check(!handoff.ready(false, 8, "awakened"), "Unready snapshot cannot close");
        check(!handoff.ready(true, 7, "awakened"), "Wait for authoritative revision");
        check(!handoff.ready(true, 8, "dormant"), "Wait for authoritative tier transition");
        check(handoff.ready(true, 8, "awakened"), "Confirmed and synchronized Ascension closes and animates");
        handoff.begin("awakened");
        check(!handoff.ready(true, 9, "resonant"), "Previous acknowledgement cannot authorize next chapter");
        actionPriority();
        navigation();
        System.out.println("NexusAttunementPresentationTest: " + checks + " checks PASS");
    }
    private static void actionPriority() {
        var available = com.mistaboom.essence_ascendance.network.PlayerEssenceSyncPayload.ProgressStatus.AVAILABLE;
        var maxTier = com.mistaboom.essence_ascendance.network.PlayerEssenceSyncPayload.ProgressStatus.MAX_TIER;
        var configurationError = com.mistaboom.essence_ascendance.network.PlayerEssenceSyncPayload.ProgressStatus.CONFIGURATION_ERROR;

        var apply = NexusAscendanceAction.resolve(available, false, true, false, false);
        check(apply.kind() == NexusAscendanceAction.Kind.APPLY_CHANGES && apply.enabled(),
                "A valid draft is applied without requiring or attempting Ascension");
        check(NexusAscendanceAction.resolve(available, true, true, false, false).kind()
                        == NexusAscendanceAction.Kind.APPLY_CHANGES,
                "Pending changes take priority even when Ascension is ready");
        var incomplete = NexusAscendanceAction.resolve(available, false, false, false, false);
        check(incomplete.kind() == NexusAscendanceAction.Kind.ASCEND && !incomplete.enabled(),
                "Ascend remains disabled while requirements are incomplete and no draft exists");
        var ascend = NexusAscendanceAction.resolve(available, true, false, false, false);
        check(ascend.kind() == NexusAscendanceAction.Kind.ASCEND && ascend.enabled(),
                "Ascend becomes available only for a clean draft with completed requirements");
        check(NexusAscendanceAction.resolve(maxTier, true, false, false, false).kind()
                        == NexusAscendanceAction.Kind.MAXIMUM_TIER,
                "Maximum tier replaces the Ascend action");
        check(NexusAscendanceAction.resolve(configurationError, true, false, false, false).kind()
                        == NexusAscendanceAction.Kind.UNAVAILABLE,
                "Configuration errors cannot expose Ascend");
        check(NexusAscendanceAction.resolve(available, true, true, true, false).kind()
                        == NexusAscendanceAction.Kind.DISCARD_DRAFT,
                "An invalidated draft offers its safe discard recovery");
        var applying = NexusAscendanceAction.resolve(available, true, true, false, true);
        check(applying.kind() == NexusAscendanceAction.Kind.APPLYING && !applying.enabled(),
                "A pending server request disables the shared action");
    }
    private static void navigation() {
        var firstPlayer = new java.util.UUID(4, 1);
        var otherPlayer = new java.util.UUID(4, 2);
        var offense = net.minecraft.resources.ResourceLocation.parse("essence_ascendance:offense");
        var defense = net.minecraft.resources.ResourceLocation.parse("essence_ascendance:defense");
        check(NexusNavigationState.recall(firstPlayer).mode() == NexusMode.ASCENDANCE,
                "An unseen player retains Ascendance as the initial default");
        var pages = new java.util.EnumMap<NexusMode, NexusNavigationState.Page>(NexusMode.class);
        pages.put(NexusMode.BONUSES, new NexusNavigationState.Page(offense, 1, 3));
        pages.put(NexusMode.SKILLS, new NexusNavigationState.Page(defense, 2, 0));
        var detail = new NexusNavigationState.Attunement(defense.toString(), 12, 1);
        for (NexusMode mode : NexusMode.values()) {
            var saved = new NexusNavigationState.Snapshot(mode, pages, detail,
                    java.util.Map.of(defense, 45.0), java.util.Map.of(defense, 26.0));
            NexusNavigationState.remember(firstPlayer, saved);
            var reopened = NexusNavigationState.recall(firstPlayer);
            check(reopened.equals(saved), "Every top-level mode reopens its complete navigation snapshot");
            check(reopened.page(NexusMode.BONUSES).category().equals(offense)
                    && reopened.page(NexusMode.SKILLS).category().equals(defense), "Bonuses and Skills retain independent category tabs");
            check(reopened.page(NexusMode.BONUSES).trackWindow() == 3 && reopened.page(NexusMode.SKILLS).tabWindow() == 2,
                    "Category/track paging survives reopening");
            check(reopened.skillScrollX().get(defense) == 45 && reopened.skillScrollY().get(defense) == 26,
                    "The selected skill-tree viewport survives reopening");
            check(NexusNavigationState.availableMode(mode, false, candidate -> false) == mode,
                    "Waiting for initial synchronization cannot erase the requested last screen");
            check(NexusNavigationState.availableMode(mode, true, candidate -> candidate == NexusMode.ASCENDANCE) == NexusMode.ASCENDANCE,
                    "Authoritatively locked modes fall back to Ascendance");
            check(NexusNavigationState.availableMode(mode, true, candidate -> true) == mode,
                    "Available remembered modes remain selected");
        }
        pages.clear();
        check(NexusNavigationState.recall(firstPlayer).pages().size() == 2, "Saved navigation snapshots cannot be changed by a previous screen's mutable map");
        check(NexusNavigationState.recall(otherPlayer).mode() == NexusMode.ASCENDANCE
                && NexusNavigationState.recall(otherPlayer).pages().isEmpty(), "Another player cannot inherit prior navigation");
        check(NexusNavigationState.categoryIndex(defense, java.util.List.of(offense, defense)) == 1
                && NexusNavigationState.categoryIndex(defense, java.util.List.of(defense, offense)) == 0,
                "Remembered tabs resolve registry identity after category reordering");
        check(NexusNavigationState.categoryIndex(defense, java.util.List.of(offense)) == 0
                && NexusNavigationState.categoryIndex(null, java.util.List.of(offense)) == 0,
                "Removed/unset category falls back to the first available tab");
        var view = new com.mistaboom.essence_ascendance.client.NexusAttunementView();
        view.restoreNavigation(detail);
        check(view.navigation().equals(detail), "Ascendance category detail and its scroll restore without rendering or gameplay state");
        check(view.back() && view.navigation().category() == null && view.navigation().scroll() == 0,
                "Returning to the constellation remembers the overview rather than reopening an old detail");
        var recreated = new com.mistaboom.essence_ascendance.client.NexusAttunementView();
        recreated.restoreNavigation(view.navigation());
        check(recreated.navigation().category() == null, "Constellation overview remains the overview on recreation");
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
