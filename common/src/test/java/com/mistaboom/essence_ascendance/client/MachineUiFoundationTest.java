package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.ui.StyledTextLayout;
import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.client.ui.UiFocusController;
import com.mistaboom.essence_ascendance.client.ui.UiOverlayStack;
import com.mistaboom.essence_ascendance.client.ui.UiViewport;
import com.mistaboom.essence_ascendance.text.EssenceText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.List;

/** Executable contracts for the shared machine UI architecture. */
public final class MachineUiFoundationTest {
    private static int checks;

    private MachineUiFoundationTest() {
    }

    public static void main(String[] args) {
        layoutAndResize();
        overlayOwnership();
        focusAndScrollBoundaries();
        styledTextContract();
        System.out.println("MachineUiFoundationTest: " + checks + " checks PASS");
    }

    private static void layoutAndResize() {
        var spec = new MachineScreenLayout.Spec(
                230,
                319,
                new UiBounds(32, 235, 166, 80),
                34,
                224
        );
        var large = MachineScreenLayout.centered(spec, 800, 600);
        check(large.mainPanel().equals(new UiBounds(285, 140, 230, 319)),
                "Main panel follows vanilla centered reflow");
        check(large.inventoryArea().equals(new UiBounds(317, 375, 166, 80)),
                "Relative inventory bounds move with the main panel");

        var right = MachineScreenLayout.sidePanel(
                large.mainPanel(), 800, 600, 180, 198,
                MachineScreenLayout.SidePreference.RIGHT_FIRST
        );
        check(right.placement() == MachineScreenLayout.Placement.EXTERNAL_RIGHT
                        && right.bounds().x() == large.mainPanel().right() + MachineScreenLayout.SIDE_PANEL_GAP,
                "Info panels prefer the external right side");

        var left = MachineScreenLayout.sidePanel(
                new UiBounds(300, 100, 230, 319), 600, 500, 172, 180,
                MachineScreenLayout.SidePreference.RIGHT_FIRST
        );
        check(left.placement() == MachineScreenLayout.Placement.EXTERNAL_LEFT,
                "Side panels reflow to the alternate external side");

        var narrowMain = MachineScreenLayout.centered(spec, 420, 360).mainPanel();
        var overlap = MachineScreenLayout.sidePanel(
                narrowMain, 420, 360, 180, 198,
                MachineScreenLayout.SidePreference.RIGHT_FIRST
        );
        check(overlap.placement() == MachineScreenLayout.Placement.CLAMPED_OVERLAP
                        && overlap.bounds().x() >= MachineScreenLayout.SCREEN_EDGE_MARGIN
                        && overlap.bounds().right() <= 420 - MachineScreenLayout.SCREEN_EDGE_MARGIN,
                "Narrow layouts clamp the complete popup to the screen");
        check(overlap.bounds().intersects(narrowMain)
                        && overlap.bounds().contains(overlap.bounds().x(), overlap.bounds().y()),
                "The rendered fallback bounds are also the hit-test bounds");
    }

    private static void overlayOwnership() {
        var under = new UiOverlayStack.Layer(
                "under",
                new UiBounds(10, 10, 100, 100),
                100,
                UiOverlayStack.PointerPolicy.CAPTURE_BOUNDS,
                UiOverlayStack.TooltipPolicy.SUPPRESS_BOUNDS,
                false
        );
        var top = new UiOverlayStack.Layer(
                "top",
                new UiBounds(40, 40, 100, 100),
                300,
                UiOverlayStack.PointerPolicy.CAPTURE_BOUNDS,
                UiOverlayStack.TooltipPolicy.SUPPRESS_BOUNDS,
                true
        );
        var stack = new UiOverlayStack(List.of(top, under));
        check(stack.pointerOwner(50, 50).orElseThrow().id().equals("top"),
                "Highest overlay owns overlapping pointer input");
        check(stack.pointerOwner(20, 20).orElseThrow().id().equals("under"),
                "Lower overlay owns its uncovered region");
        check(stack.pointerOwner(200, 200).isEmpty(),
                "Bounded overlays do not steal outside input");
        check(!stack.allowsTooltip(50, 50) && stack.allowsTooltip(200, 200),
                "Tooltip ordering follows the same overlay geometry");
        check(stack.keyboardOwner().orElseThrow().id().equals("top"),
                "Top declared keyboard owner receives routing");

        var modal = new UiOverlayStack.Layer(
                "modal",
                new UiBounds(250, 250, 20, 20),
                400,
                UiOverlayStack.PointerPolicy.MODAL,
                UiOverlayStack.TooltipPolicy.SUPPRESS_ALL,
                true
        );
        var modalStack = new UiOverlayStack(List.of(under, modal));
        check(modalStack.pointerOwner(0, 0).orElseThrow().id().equals("modal")
                        && !modalStack.allowsTooltip(0, 0),
                "Modal layers own outside input and suppress lower tooltips");
    }

    private static void focusAndScrollBoundaries() {
        UiViewport viewport = UiViewport.create(420, 100, -80);
        check(viewport.offset() == 0 && viewport.maximumOffset() == 320,
                "Viewport clamps its leading boundary");
        check(viewport.scrollBy(999).offset() == 320
                        && viewport.end().offset() == 320
                        && viewport.end().scrollBy(1).offset() == 320,
                "Viewport clamps its trailing boundary");
        check(viewport.pageBy(1).offset() == 90 && viewport.pageBy(-1).offset() == 0,
                "Viewport pages deterministically within bounds");

        var focus = new UiFocusController<String>();
        focus.setOrder(List.of("first", "second", "third"));
        check(focus.move(1).orElseThrow().equals("first")
                        && focus.move(-1).orElseThrow().equals("third"),
                "Focus enters and wraps in declared order");
        focus.focus("second");
        focus.setOrder(List.of("first", "third"));
        check(focus.focused().isEmpty(), "Focus clears when reflow removes its target");
    }

    private static void styledTextContract() {
        ClickEvent click = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/example");
        Style interactive = Style.EMPTY
                .withColor(ChatFormatting.GOLD)
                .withBold(true)
                .withClickEvent(click)
                .withInsertion("example");
        Component content = Component.empty()
                .append(Component.literal("Styled").withStyle(interactive))
                .append(Component.literal(" plain").withStyle(ChatFormatting.GRAY));
        List<StyledTextLayout.Run> runs = StyledTextLayout.runs(content);
        check(runs.size() == 2 && runs.getFirst().text().equals("Styled"),
                "Styled layout retains logical component runs");
        check(runs.getFirst().style().getClickEvent().equals(click)
                        && runs.getFirst().style().isBold()
                        && "example".equals(runs.getFirst().style().getInsertion()),
                "Click, emphasis and insertion metadata survive measurement");
        check(runs.get(1).style().getColor().getValue()
                        == ChatFormatting.GRAY.getColor(),
                "Independent run colors remain distinct");

        Component translated = EssenceText.gui("crucible.title");
        check(translated.getContents() instanceof TranslatableContents,
                "Shared UI receives translatable Components without eager flattening");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
