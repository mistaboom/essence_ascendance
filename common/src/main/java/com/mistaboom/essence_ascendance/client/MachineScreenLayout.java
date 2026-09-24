package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.ui.UiBounds;

import java.util.Objects;

/**
 * Shared geometry conventions for normal Essence Ascendance machine screens.
 * The fullscreen Ascendance Nexus intentionally does not use this layout.
 */
public final class MachineScreenLayout {

    /** Standard width for the main panel used by normal machine screens. */
    public static final int MAIN_PANEL_WIDTH = 230;
    /** Standard width for the Info side panel used by normal machines. */
    public static final int INFO_PANEL_WIDTH = 180;
    /** Gap between a machine's main panel and a side panel. */
    public static final int SIDE_PANEL_GAP = 4;
    /** Minimum margin retained between a side panel and the physical screen edge. */
    public static final int SCREEN_EDGE_MARGIN = 4;
    /** Standard vertical offset for side panels relative to the main machine panel. */
    public static final int SIDE_PANEL_TOP_OFFSET = 4;

    private MachineScreenLayout() {
    }

    public enum SidePreference {
        RIGHT_FIRST,
        LEFT_FIRST
    }

    public enum Placement {
        EXTERNAL_RIGHT,
        EXTERNAL_LEFT,
        CLAMPED_OVERLAP
    }

    /** Relative machine sizing supplied once by a container screen. */
    public record Spec(
            int imageWidth,
            int imageHeight,
            UiBounds inventoryArea,
            int inventoryLabelX,
            int inventoryLabelY
    ) {
        public Spec {
            if (imageWidth <= 0 || imageHeight <= 0) {
                throw new IllegalArgumentException("Machine image size must be positive");
            }
            Objects.requireNonNull(inventoryArea, "inventoryArea");
        }
    }

    /** Reflow result shared by drawing, widgets, overlays and hit testing. */
    public record Composition(UiBounds mainPanel, UiBounds inventoryArea) {
    }

    public record SidePanel(UiBounds bounds, Placement placement) {
    }

    public static Composition compose(Spec spec, int leftPos, int topPos) {
        UiBounds main = new UiBounds(leftPos, topPos, spec.imageWidth(), spec.imageHeight());
        return new Composition(main, spec.inventoryArea().translate(leftPos, topPos));
    }

    /** Pure resize/reflow entry point used by tests and non-container hosts. */
    public static Composition centered(Spec spec, int screenWidth, int screenHeight) {
        return compose(
                spec,
                (screenWidth - spec.imageWidth()) / 2,
                (screenHeight - spec.imageHeight()) / 2
        );
    }

    public static SidePanel sidePanel(
            UiBounds mainPanel,
            int screenWidth,
            int screenHeight,
            int panelWidth,
            int panelHeight,
            SidePreference preference
    ) {
        int rightX = mainPanel.right() + SIDE_PANEL_GAP;
        int leftX = mainPanel.x() - SIDE_PANEL_GAP - panelWidth;
        int panelY = mainPanel.y() + SIDE_PANEL_TOP_OFFSET;
        boolean rightFits = rightX + panelWidth <= screenWidth - SCREEN_EDGE_MARGIN;
        boolean leftFits = leftX >= SCREEN_EDGE_MARGIN;

        if (preference == SidePreference.RIGHT_FIRST) {
            if (rightFits) {
                return externalPanel(rightX, panelY, panelWidth, panelHeight, screenWidth, screenHeight,
                        Placement.EXTERNAL_RIGHT);
            }
            if (leftFits) {
                return externalPanel(leftX, panelY, panelWidth, panelHeight, screenWidth, screenHeight,
                        Placement.EXTERNAL_LEFT);
            }
        } else {
            if (leftFits) {
                return externalPanel(leftX, panelY, panelWidth, panelHeight, screenWidth, screenHeight,
                        Placement.EXTERNAL_LEFT);
            }
            if (rightFits) {
                return externalPanel(rightX, panelY, panelWidth, panelHeight, screenWidth, screenHeight,
                        Placement.EXTERNAL_RIGHT);
            }
        }

        int preferredX = preference == SidePreference.RIGHT_FIRST ? rightX : leftX;
        UiBounds screen = new UiBounds(
                SCREEN_EDGE_MARGIN,
                SCREEN_EDGE_MARGIN,
                Math.max(0, screenWidth - SCREEN_EDGE_MARGIN * 2),
                Math.max(0, screenHeight - SCREEN_EDGE_MARGIN * 2)
        );
        UiBounds clamped = new UiBounds(preferredX, panelY, panelWidth, panelHeight).clampInside(screen);
        return new SidePanel(clamped, Placement.CLAMPED_OVERLAP);
    }

    private static SidePanel externalPanel(
            int x,
            int y,
            int width,
            int height,
            int screenWidth,
            int screenHeight,
            Placement placement
    ) {
        UiBounds screen = new UiBounds(
                SCREEN_EDGE_MARGIN,
                SCREEN_EDGE_MARGIN,
                Math.max(0, screenWidth - SCREEN_EDGE_MARGIN * 2),
                Math.max(0, screenHeight - SCREEN_EDGE_MARGIN * 2)
        );
        return new SidePanel(new UiBounds(x, y, width, height).clampInside(screen), placement);
    }

}
