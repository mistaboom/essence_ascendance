package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.client.ui.UiOverlayStack;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenComposition;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenControls;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenLayout;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenNavigation;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenScroll;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenScreen;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenViewport;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/** Nonshipping second consumer: ordinary-screen composition without Nexus or menu lifecycle code. */
public final class FullscreenCompositionTest {
    private static int checks;
    private static final FullscreenComposition.Renderer NO_RENDER = (graphics, x, y, tick) -> { };

    public static void main(String[] args) {
        archiveFixture();
        reflow();
        focusAndRouting();
        overlayCapture();
        synchronousCaptureRefresh();
        nestedModalFocus();
        primaryContentKeys();
        attunementControls();
        capture();
        viewports();
        rowScrolling();
        System.out.println("FullscreenCompositionTest: " + checks + " checks PASS");
    }

    private static void archiveFixture() {
        ArchiveFixture fixture = new ArchiveFixture();
        fixture.refresh();
        check(fixture.ui.scene().frame().secondary().height() > 0,
                "Guide declares secondary navigation");
        check(fixture.ui.scene().regions().stream().map(FullscreenComposition.Region::id).toList()
                        .equals(List.of("guide/list", "guide/detail")),
                "Guide assembles list and detail from shared regions");
        var guideList = fixture.ui.scene().regions().getFirst().bounds();
        var guideDetail = fixture.ui.scene().regions().getLast().bounds();
        check(guideList.right() < guideDetail.x() && guideList.bottom() == guideDetail.bottom(),
                "List/detail layout shares a bounded content region");

        fixture.click("mode/reference");
        check(fixture.navigation.mode().equals("reference") && fixture.ui.scene().frame().secondary().height() > 0,
                "Reference selects its own secondary navigation through shared controls");
        check(fixture.ui.scene().regions().size() == 1,
                "Reference uses a full content region without copying Guide layout");
        fixture.click("section/skills");
        fixture.click("mode/guide");
        check(fixture.navigation.section().equals("getting-started"), "Guide retains its independent selected section");
        fixture.click("mode/reference");
        check(fixture.navigation.section().equals("skills"), "Reference restores its own section");

        fixture.click("mode/search");
        check(fixture.ui.scene().frame().secondary().height() == 0
                        && fixture.ui.scene().controls().stream().noneMatch(c -> c.id().startsWith("section")),
                "Search declares no section tabs or paging controls");
        check(fixture.ui.scene().regions().stream().map(FullscreenComposition.Region::id).toList()
                        .equals(List.of("search/field", "search/results")),
                "Search composes a field and results with shared bands");
        fixture.clickRegion("search/field");
        check(fixture.ui.charTyped('s', 0) && fixture.ui.charTyped('k', 0)
                        && fixture.search.query.toString().equals("sk"),
                "An ordinary field receives focused text input through the same composition");
        check(fixture.ui.keyPressed(259, 0, 0) && fixture.search.query.toString().equals("s"),
                "Field editing remains content-owned behind shared focus dispatch");

        var frame = fixture.ui.scene().frame();
        var manyModes = new FullscreenComposition.Builder("extra", frame).modes(
                List.of("a", "b", "c", "d", "e").stream()
                        .map(id -> new FullscreenComposition.Mode<>(id, Component.literal(id), true, 0)).toList(),
                "e", ignored -> { }).build();
        check(manyModes.controls().size() == 5 && manyModes.controls().getLast().selected(),
                "The same builder accepts arbitrary mode counts and stable IDs");
    }

    private static void reflow() {
        for (int width : new int[] {0, 1, 24, 140, 320, 853}) {
            for (int height : new int[] {0, 12, 120, 480}) {
                for (boolean secondary : new boolean[] {false, true}) {
                    var frame = FullscreenLayout.frame(FullscreenLayout.Spec.standard(), width, height, secondary);
                    check(inside(frame.modes(), frame.screen()) && inside(frame.secondary(), frame.screen())
                                    && inside(frame.content(), frame.screen()),
                            "Frame regions stay inside very small and wide resized hosts");
                    var split = FullscreenLayout.listDetail(frame.content(), 170, 8);
                    check(inside(split.list(), frame.content()) && inside(split.detail(), frame.content()),
                            "List/detail reflows with the available content dimensions");
                    var bands = FullscreenLayout.bands(frame.content(), 28, 16);
                    check(bands.header().bottom() == bands.body().y() && bands.body().bottom() == bands.footer().y()
                                    && bands.footer().bottom() == frame.content().bottom(),
                            "Bands partition available height without overlap");
                    var tabs = FullscreenLayout.tabs(frame.secondary(), List.of(30, 80, 42, 150), 64, 102, 18, 3);
                    check(tabs.items().stream().allMatch(item -> inside(item, frame.secondary())),
                            "Tab draw/hit bounds remain inside their shared band");
                }
            }
        }
        ArchiveFixture fixture = new ArchiveFixture();
        fixture.width = 240;
        fixture.refresh();
        String selected = fixture.navigation.section();
        fixture.click("sections/next");
        fixture.refresh();
        check(fixture.navigation.section().equals(selected) && fixture.navigation.sectionWindow() == 1,
                "Paging does not change the active section or force it back into view");
        check(fixture.ui.scene().controls().stream().noneMatch(c -> c.id().equals("section/" + selected)),
                "The active section may remain outside the visible tab window");
        fixture.width = 1000;
        fixture.refresh();
        check(fixture.navigation.sectionWindow() == 0 && fixture.navigation.section().equals(selected),
                "A wider reflow clamps paging while preserving section identity");
    }

    private static void focusAndRouting() {
        ArchiveFixture fixture = new ArchiveFixture();
        fixture.select("search");
        fixture.clickRegion("search/field");
        check(fixture.search.focused && fixture.ui.focusedId().equals("search/field"),
                "Pointer focus and content focus notification agree");

        AtomicInteger applied = new AtomicInteger();
        CountingInput modal = new CountingInput();
        var base = fixture.ui.scene();
        var dialog = FullscreenLayout.centered(base.frame().screen(), 200, 100, 12);
        var modalButtons = FullscreenLayout.segments(FullscreenLayout.bands(dialog, 60, 20).footer(), 2, 4);
        var apply = new FullscreenComposition.Control("modal/apply", modalButtons.getFirst(), Component.literal("Apply"),
                false, false, FullscreenControls.Style.ACTION, 0, applied::incrementAndGet);
        var back = new FullscreenComposition.Control("modal/back", modalButtons.getLast(), Component.literal("Back"),
                true, false, FullscreenControls.Style.ACTION, 0, () -> fixture.ui.update(base));
        var overlay = new FullscreenComposition.Overlay(new UiOverlayStack.Layer("confirm", dialog, 500,
                UiOverlayStack.PointerPolicy.MODAL, UiOverlayStack.TooltipPolicy.SUPPRESS_ALL, true),
                NO_RENDER, List.of(apply, back), modal);
        fixture.ui.update(new FullscreenComposition.Scene(base.pageKey(), base.frame(), base.backgroundTop(), base.backgroundBottom(),
                base.regions(), base.controls(), List.of(overlay), base.primaryInput()));
        check(fixture.ui.focusedId().equals("modal/back") && !fixture.search.focused,
                "Modal focus excludes covered regions and disabled actions");
        check(fixture.ui.mouseClicked(-100, -100, 1) && modal.clicks == 1,
                "A modal captures all pointer buttons outside its visual bounds");
        check(fixture.ui.mouseScrolled(-100, -100, 0, 1) && modal.scrolls == 1,
                "A modal captures scrolling outside its visual bounds");
        check(fixture.ui.keyPressed(256, 0, 0) && modal.keys == 1,
                "Escape is delivered to the modal's lifecycle callback");
        check(fixture.ui.charTyped('x', 0) && modal.characters == 1 && fixture.search.query.isEmpty(),
                "Modal character capture cannot leak to a focused field below");
        check(!fixture.ui.allowsTooltip(-100, -100), "Modal suppresses covered tooltips across the entire screen");
        click(fixture.ui, apply.bounds());
        check(applied.get() == 0, "Disabled controls consume input without performing actions");
        check(fixture.ui.keyPressed(258, 0, 0) && fixture.ui.focusedId().equals("modal/back"),
                "Modal Tab wraps within enabled modal controls");
        check(fixture.ui.keyPressed(257, 0, 0) && fixture.ui.focusedId().equals("search/field") && fixture.search.focused,
                "Closing a modal restores the previous region focus");
        check(fixture.ui.allowsTooltip(1, 1), "Closing a modal restores tooltip ownership");
        fixture.ui.update(new FullscreenComposition.Builder(base.pageKey(), base.frame()).build());
        check(fixture.ui.focusedId() == null && !fixture.search.focused,
                "Removing focused content clears both shared and content focus state");
    }

    private static void capture() {
        var frame = FullscreenLayout.frame(FullscreenLayout.Spec.standard(), 400, 300, false);
        var ui = new FullscreenComposition();
        CountingInput input = new CountingInput();
        var bounds = new UiBounds(30, 70, 100, 80);
        var scene = new FullscreenComposition.Builder("first", frame)
                .region(new FullscreenComposition.Region("canvas", bounds, NO_RENDER, input, true)).build();
        ui.update(scene);
        check(ui.mouseClicked(40, 80, 0) && ui.mouseDragged(600, 500, 0, 560, 420)
                        && ui.mouseReleased(600, 500, 0),
                "A content drag remains captured after the pointer leaves its region");
        check(input.drags == 1 && input.releases == 1, "Captured content receives the drag and release exactly once");
        check(!ui.mouseDragged(50, 80, 0, 1, 1), "Release ends pointer capture");
        ui.mouseClicked(40, 80, 0);
        ui.resized();
        check(input.cancellations == 1 && !ui.mouseDragged(50, 80, 0, 1, 1),
                "Resize cancels an in-flight content gesture");
        ui.mouseClicked(40, 80, 0);
        ui.update(new FullscreenComposition.Builder("second", frame)
                .region(new FullscreenComposition.Region("canvas", bounds, NO_RENDER, input, true)).build());
        check(input.cancellations == 2 && !ui.mouseReleased(40, 80, 0),
                "Mode/page navigation cancels captures even when region IDs are reused");
    }

    private static void overlayCapture() {
        var frame = FullscreenLayout.frame(FullscreenLayout.Spec.standard(), 400, 300, false);
        var ui = new FullscreenComposition();
        CountingInput original = new CountingInput();
        CountingInput refreshed = new CountingInput();
        var bounds = new UiBounds(30, 70, 100, 80);
        var layer = new UiOverlayStack.Layer("popup", bounds, 100, UiOverlayStack.PointerPolicy.CAPTURE_BOUNDS,
                UiOverlayStack.TooltipPolicy.SUPPRESS_BOUNDS, false);
        ui.update(new FullscreenComposition.Builder("page", frame)
                .overlay(new FullscreenComposition.Overlay(layer, NO_RENDER, List.of(), original)).build());
        check(ui.mouseClicked(40, 80, 0), "Overlay custom input can begin a pointer gesture");
        ui.update(new FullscreenComposition.Builder("page", frame)
                .overlay(new FullscreenComposition.Overlay(layer, NO_RENDER, List.of(), refreshed)).build());
        check(ui.mouseDragged(700, 600, 0, 1, 1) && ui.mouseReleased(700, 600, 0),
                "An overlay gesture remains captured outside its original bounds");
        check(original.drags == 1 && original.releases == 1 && refreshed.drags == 0 && refreshed.releases == 0,
                "Scene refresh preserves the input handler that initiated the gesture");
        ui.mouseClicked(40, 80, 0);
        ui.update(new FullscreenComposition.Builder("page", frame).build());
        check(refreshed.cancellations == 1 && !ui.mouseDragged(40, 80, 0, 1, 1),
                "Removing an overlay cancels its captured handler");
    }

    private static void nestedModalFocus() {
        ArchiveFixture fixture = new ArchiveFixture();
        fixture.select("search");
        fixture.clickRegion("search/field");
        var base = fixture.ui.scene();
        var dialog = FullscreenLayout.centered(base.frame().screen(), 200, 100, 12);
        var slots = FullscreenLayout.segments(FullscreenLayout.bands(dialog, 60, 20).footer(), 2, 4);
        var outer = modal("outer", 500, dialog, slots);
        var inner = modal("inner", 600, dialog, slots);
        var outerScene = withOverlays(base, List.of(outer));
        fixture.ui.update(outerScene);
        fixture.ui.keyPressed(258, 0, 0);
        check(fixture.ui.focusedId().equals("outer/second"), "Outer modal can focus its second control");
        fixture.ui.update(withOverlays(base, List.of(outer, inner)));
        check(fixture.ui.focusedId().equals("inner/first"), "A nested modal takes the keyboard scope");
        fixture.ui.keyPressed(258, 0, 0);
        fixture.ui.update(outerScene);
        check(fixture.ui.focusedId().equals("outer/second"), "Closing a nested modal restores its parent's prior focus");
        fixture.ui.update(base);
        check(fixture.ui.focusedId().equals("search/field"), "Closing the outer modal restores content focus");
        fixture.ui.update(outerScene);
        fixture.ui.update(new FullscreenComposition.Scene("new-page", base.frame(), base.backgroundTop(), base.backgroundBottom(),
                base.regions(), base.controls(), List.of(), base.primaryInput()));
        check(fixture.ui.focusedId() == null, "Changing pages while closing a modal cannot resurrect focus from the old page");
    }

    private static void synchronousCaptureRefresh() {
        var frame = FullscreenLayout.frame(FullscreenLayout.Spec.standard(), 400, 300, false);
        var ui = new FullscreenComposition();
        CountingInput refreshed = new CountingInput();
        var bounds = new UiBounds(30, 70, 100, 80);
        CountingInput original = new CountingInput() {
            @Override public boolean click(double x, double y, int button) {
                ui.update(new FullscreenComposition.Builder("page", frame)
                        .region(new FullscreenComposition.Region("content", bounds, NO_RENDER, refreshed, true)).build());
                return true;
            }
        };
        ui.update(new FullscreenComposition.Builder("page", frame)
                .region(new FullscreenComposition.Region("content", bounds, NO_RENDER, original, true)).build());
        ui.mouseClicked(40, 80, 0);
        check(ui.mouseDragged(700, 600, 0, 1, 1) && ui.mouseReleased(700, 600, 0)
                        && original.drags == 1 && original.releases == 1 && refreshed.drags == 0,
                "A synchronous scene refresh during click preserves the initiating input's capture");

        ui.mouseClicked(40, 80, 0);
        var blocker = new FullscreenComposition.Overlay(new UiOverlayStack.Layer("pointer-blocker", bounds, 500,
                UiOverlayStack.PointerPolicy.MODAL, UiOverlayStack.TooltipPolicy.SUPPRESS_ALL, false),
                NO_RENDER, List.of(), new CountingInput());
        ui.update(withOverlays(ui.scene(), List.of(blocker)));
        check(refreshed.cancellations == 1 && ui.mouseDragged(700, 600, 0, 1, 1) && refreshed.drags == 0,
                "A pointer-only modal cancels and blocks a covered gesture without taking keyboard ownership");

        AtomicInteger actions = new AtomicInteger();
        var control = new FullscreenComposition.Control("popup/action", bounds, Component.literal("Action"), true,
                false, FullscreenControls.Style.ACTION, 0, actions::incrementAndGet);
        var popup = new FullscreenComposition.Overlay(new UiOverlayStack.Layer("popup", bounds, 100,
                UiOverlayStack.PointerPolicy.CAPTURE_BOUNDS, UiOverlayStack.TooltipPolicy.SUPPRESS_BOUNDS, false),
                NO_RENDER, List.of(control), FullscreenComposition.Input.NONE);
        ui.update(new FullscreenComposition.Builder("page", frame).overlay(popup).build());
        click(ui, bounds);
        check(ui.focusedId().equals("popup/action") && ui.keyPressed(257, 0, 0) && actions.get() == 2,
                "A pointer popup's controls also receive standard keyboard focus and activation");
    }

    private static FullscreenComposition.Overlay modal(String id, int z, UiBounds bounds, List<UiBounds> slots) {
        return new FullscreenComposition.Overlay(new UiOverlayStack.Layer(id, bounds, z,
                UiOverlayStack.PointerPolicy.MODAL, UiOverlayStack.TooltipPolicy.SUPPRESS_ALL, true), NO_RENDER,
                List.of(new FullscreenComposition.Control(id + "/first", slots.getFirst(), Component.literal("First"), true,
                                false, FullscreenControls.Style.ACTION, 0, () -> { }),
                        new FullscreenComposition.Control(id + "/second", slots.getLast(), Component.literal("Second"), true,
                                false, FullscreenControls.Style.ACTION, 0, () -> { })), new CountingInput());
    }

    private static FullscreenComposition.Scene withOverlays(FullscreenComposition.Scene base, List<FullscreenComposition.Overlay> overlays) {
        return new FullscreenComposition.Scene(base.pageKey(), base.frame(), base.backgroundTop(), base.backgroundBottom(),
                base.regions(), base.controls(), overlays, base.primaryInput());
    }

    private static void primaryContentKeys() {
        var frame = FullscreenLayout.frame(FullscreenLayout.Spec.standard(), 400, 300, false);
        var view = new NexusAttunementView();
        view.restoreNavigation(new com.mistaboom.essence_ascendance.client.nexus.NexusNavigationState.Attunement("test:category", 12, -1));
        var ui = new FullscreenComposition();
        CountingInput content = new CountingInput() {
            @Override public boolean key(int key, int scan, int modifiers) {
                keys++;
                return view.key(key, (modifiers & 1) != 0) != NexusAttunementView.Click.NONE;
            }
        };
        ui.update(new FullscreenComposition.Builder("attunement", frame)
                .modes(List.of(new FullscreenComposition.Mode<>("overview", Component.literal("Overview"), true, 0)), "overview", ignored -> { })
                .region(new FullscreenComposition.Region("content", frame.content(), NO_RENDER, content, true))
                .primaryInput("content").build());
        click(ui, ui.scene().controls().getFirst().bounds());
        check(ui.keyPressed(256, 0, 0) && view.navigation().category() == null,
                "Escape from a focused shell control still offers content its Back intent before host close");
        ui.keyPressed(258, 0, 0);
        check(ui.focusedId().equals("content") && content.focused,
                "Outer Tab reaches the content focus scope after the shell controls");
        check(ui.keyPressed(262, 0, 0) && content.keys == 2,
                "Directional keys are delivered once to the content's own focus model");
        check(!ui.mouseClicked(frame.content().right(), frame.content().bottom(), 0),
                "Region pointer routing excludes its right and bottom boundaries");
    }

    private static void attunementControls() {
        var first = new com.mistaboom.essence_ascendance.attunement.AttunementSnapshot.Category(
                "test:first", 0, 100, 1, List.of(), List.of());
        var second = new com.mistaboom.essence_ascendance.attunement.AttunementSnapshot.Category(
                "test:second", 0, 100, 1, List.of(), List.of());
        var state = new com.mistaboom.essence_ascendance.attunement.AttunementSnapshot(
                "test:chapter", 1, 0, false, List.of(first, second));
        var frame = FullscreenLayout.frame(FullscreenLayout.Spec.standard(), 400, 300, false);
        var view = new NexusAttunementView();
        view.restoreNavigation(new com.mistaboom.essence_ascendance.client.nexus.NexusNavigationState.Attunement(
                "test:second", 12, 1, "test:second"));
        view.prepare(state, frame.content());
        view.prepare(new com.mistaboom.essence_ascendance.attunement.AttunementSnapshot(
                "test:chapter", 1, 0, false, List.of(second, first)), frame.content());
        check(view.navigation().focusedIndex() == 0 && view.navigation().focusedId().equals("test:second"),
                "Attunement focus restores by stable category identity after registry reorder");
        var controls = view.controls(frame.content(), 9);
        check(controls.size() == 1 && controls.getFirst().style() == FullscreenControls.Style.LINK,
                "A restored detail page declares its Back control before the first rendered frame");
        var ui = new FullscreenComposition();
        var builder = new FullscreenComposition.Builder("attunement", frame);
        controls.forEach(builder::control);
        ui.update(builder.build());
        click(ui, controls.getFirst().bounds());
        check(view.navigation().category() == null && view.navigation().scroll() == 0,
                "The shared Back control invokes domain navigation and resets detail scroll");
        view.restoreNavigation(new com.mistaboom.essence_ascendance.client.nexus.NexusNavigationState.Attunement(
                "test:second", 12, 0, "test:second"));
        view.prepare(new com.mistaboom.essence_ascendance.attunement.AttunementSnapshot(
                "test:chapter", 1, 0, false, List.of(first)), frame.content());
        check(view.navigation().category() == null && view.navigation().focusedId() == null
                        && view.controls(frame.content(), 9).isEmpty(),
                "Removed authoritative categories clear invalid detail and focus without redirecting to another category");
    }

    private static void viewports() {
        FullscreenViewport viewport = new FullscreenViewport();
        viewport.restore(46.5, 30.25);
        check(viewport.x() == 46.5 && viewport.y() == 30.25, "Restored viewport survives until first measured content");
        viewport.configure(new UiBounds(20, 40, 100, 80), 400, 300);
        var content = viewport.screenToContent(75, 95);
        var screen = viewport.contentToScreen(content.x(), content.y());
        check(screen.x() == 75 && screen.y() == 95, "Rendering and hit testing share inverse coordinate transforms");
        viewport.beginPan(75, 95);
        viewport.pan(55, 85);
        check(viewport.x() == 66.5 && viewport.y() == 40.25, "Panning retains fractional two-axis offsets");
        viewport.endPan();
        check(!viewport.pan(10, 10), "Completed pan does not keep moving content");
        viewport.scroll(0, -1, 28, true, false);
        check(viewport.x() == 94.5 && viewport.y() == 40.25, "Shift-wheel redirects to horizontal scrolling");
        viewport.keyboard(269, false, 28);
        check(viewport.y() == viewport.maximumY(), "End reaches the vertical content boundary");
        viewport.keyboard(268, true, 28);
        check(viewport.x() == 0, "Shift-Home reaches the horizontal start");
        viewport.configure(new UiBounds(20, 40, 100, 80), 400, 50);
        viewport.scroll(0, -1, 28, false, true);
        check(viewport.x() == 28 && viewport.y() == 0, "Horizontal-only content accepts vertical wheel fallback");
        viewport.configure(new UiBounds(20, 40, 100, 80), 60, 20);
        check(viewport.x() == 0 && viewport.y() == 0 && viewport.originX(true) == 40 && viewport.originY(true) == 70,
                "Reflow clamps scrolling and centers content smaller than its viewport");
        viewport.configure(new UiBounds(20, 40, 101, 81), 60, 20);
        check(viewport.pixelOriginX(true) == 40 && viewport.pixelOriginY(true) == 70
                        && viewport.originX(true) == 40.5 && viewport.originY(true) == 70.5,
                "Pixel origins preserve integer centering while subpixel origins retain odd spare halves");
        check(viewport.pixelOriginX(false) == 20 && viewport.pixelOriginY(false) == 40,
                "Uncentered pixel origins keep content at the viewport edge");
        viewport.configure(new UiBounds(20, 40, 100, 80), 400, 300);
        viewport.restore(46.5, 30.5);
        check(viewport.pixelOriginX(true) == -27 && viewport.pixelOriginY(true) == 9
                        && viewport.originX(true) == -26.5 && viewport.originY(true) == 9.5,
                "Pixel origins round half-pixel scrolls before subtraction, preserving existing content placement");
        viewport.restore(46.49, 30.49);
        check(viewport.pixelOriginX(false) == -26 && viewport.pixelOriginY(false) == 10,
                "Pixel scroll snapping switches exactly at the half-pixel boundary on both axes");
    }

    private static void rowScrolling() {
        FullscreenScroll rows = new FullscreenScroll();
        rows.restore(12);
        check(rows.offset() == 12, "Unmeasured row content preserves restored navigation");
        rows.configure(60, 20);
        rows.wheel(-1, 3);
        check(rows.offset() == 15, "Detail wheel advances three rows");
        rows.key(267, 1, 8);
        check(rows.offset() == 23, "Detail paging preserves its eight-row policy");
        rows.key(265, 1, 8);
        check(rows.offset() == 22, "Detail arrow scrolling uses one row");
        rows.key(269, 1, 8);
        rows.wheel(-1, 3);
        check(rows.offset() == 40, "End and wheel clamp at the same measured row boundary");
        rows.configure(12, 20);
        check(rows.offset() == 0 && rows.maximumOffset() == 0, "Row reflow clamps removed or shortened content");
        rows.restore(12);
        check(rows.offset() == 12, "A fresh restore is not erased by an earlier measurement");
        check(!rows.key(267, 1, 8) && rows.offset() == 12, "Input before current measurement preserves restored offsets");
    }

    /** Compile-time host proof: the same factory plugs into an ordinary screen without menu rules. */
    private static final class ArchiveScreenExample extends FullscreenScreen {
        private final ArchiveFixture fixture = new ArchiveFixture(fullscreen);
        ArchiveScreenExample() { super(Component.literal("Archive composition fixture")); }
        @Override protected FullscreenComposition.Scene composeFullscreen() {
            fixture.width = width;
            fixture.height = height;
            return fixture.compose();
        }
    }

    private static final class ArchiveFixture {
        private record Page(List<String> sections, BiConsumer<FullscreenComposition.Builder, UiBounds> compose) { }
        final FullscreenComposition ui;
        final FullscreenNavigation<String, String> navigation = new FullscreenNavigation<>("guide");
        final SearchField search = new SearchField();
        int width = 700;
        int height = 400;
        final Map<String, Page> pages = Map.of(
                "guide", new Page(List.of("getting-started", "progression", "crafting", "equipment"), (builder, bounds) -> {
                    var split = FullscreenLayout.listDetail(bounds, 170, 8);
                    builder.region(new FullscreenComposition.Region("guide/list", split.list(), NO_RENDER, new CountingInput(), true))
                            .region(new FullscreenComposition.Region("guide/detail", split.detail(), NO_RENDER, new CountingInput(), true));
                }),
                "reference", new Page(List.of("essence", "skills"), (builder, bounds) ->
                        builder.region(new FullscreenComposition.Region("reference/content", bounds, NO_RENDER, new CountingInput(), true))),
                "search", new Page(List.of(), (builder, bounds) -> {
                    var bands = FullscreenLayout.bands(bounds, 24, 0);
                    builder.region(new FullscreenComposition.Region("search/field", bands.header(), NO_RENDER, search, true))
                            .region(new FullscreenComposition.Region("search/results", bands.body(), NO_RENDER, new CountingInput(), true));
                }));

        ArchiveFixture() { this(new FullscreenComposition()); }
        ArchiveFixture(FullscreenComposition ui) { this.ui = ui; }

        void select(String mode) { navigation.selectMode(mode); refresh(); }

        void refresh() {
            ui.update(compose());
        }

        FullscreenComposition.Scene compose() {
            Page page = pages.get(navigation.mode());
            var frame = FullscreenLayout.frame(FullscreenLayout.Spec.standard(), width, height, !page.sections().isEmpty());
            var builder = new FullscreenComposition.Builder(navigation.mode(), frame).modes(
                    List.of("guide", "reference", "search").stream()
                            .map(id -> new FullscreenComposition.Mode<>(id, Component.literal(id), true, 0)).toList(),
                    navigation.mode(), this::select);
            if (!page.sections().isEmpty()) {
                var tabs = FullscreenLayout.tabs(frame.secondary(), page.sections().stream().map(id -> id.length() * 6).toList(),
                        64, 102, 18, 3);
                navigation.reconcileSections(page.sections(), tabs.visibleCount(), true);
                builder.sections(navigation, page.sections().stream()
                        .map(id -> new FullscreenComposition.Section<>(id, Component.literal(id), 0)).toList(), tabs,
                        id -> { navigation.selectSection(id); refresh(); });
            }
            page.compose().accept(builder, frame.content());
            return builder.build();
        }

        void click(String id) {
            FullscreenCompositionTest.click(ui, ui.scene().controls().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow().bounds());
        }

        void clickRegion(String id) {
            FullscreenCompositionTest.click(ui, ui.scene().regions().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow().bounds());
        }
    }

    private static class CountingInput implements FullscreenComposition.Input {
        int clicks, drags, releases, scrolls, keys, characters, cancellations;
        boolean focused;
        public boolean click(double x, double y, int button) { clicks++; return true; }
        public boolean drag(double x, double y, int button, double dx, double dy) { drags++; return true; }
        public boolean release(double x, double y, int button) { releases++; return true; }
        public boolean scroll(double x, double y, double dx, double dy) { scrolls++; return true; }
        public boolean key(int key, int scan, int modifiers) { keys++; return true; }
        public boolean character(char character, int modifiers) { characters++; return true; }
        public void focused(boolean focused) { this.focused = focused; }
        public void cancel() { cancellations++; }
    }

    private static final class SearchField extends CountingInput {
        final StringBuilder query = new StringBuilder();
        public boolean character(char character, int modifiers) { query.append(character); return true; }
        public boolean key(int key, int scan, int modifiers) {
            if (key != 259) return false;
            if (!query.isEmpty()) query.deleteCharAt(query.length() - 1);
            return true;
        }
    }

    private static void click(FullscreenComposition ui, UiBounds bounds) {
        check(ui.mouseClicked(bounds.x() + bounds.width() / 2.0, bounds.y() + bounds.height() / 2.0, 0),
                "Declared draw bounds also receive pointer input");
    }

    private static boolean inside(UiBounds child, UiBounds parent) {
        return child.x() >= parent.x() && child.y() >= parent.y()
                && child.right() <= parent.right() && child.bottom() <= parent.bottom();
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
