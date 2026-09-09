package com.mistaboom.essence_ascendance.client;

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

    /**
     * Chooses the normal side-panel position: right first, then left, then an
     * opaque clamped overlap when the current GUI scale cannot fit either side.
     */
    public static int sidePanelX(
            int leftPos,
            int imageWidth,
            int screenWidth,
            int panelWidth
    ) {
        int right = leftPos + imageWidth + SIDE_PANEL_GAP;
        if (right + panelWidth <= screenWidth - SCREEN_EDGE_MARGIN) {
            return right;
        }

        int left = leftPos - SIDE_PANEL_GAP - panelWidth;
        if (left >= SCREEN_EDGE_MARGIN) {
            return left;
        }

        int maxX = Math.max(
                SCREEN_EDGE_MARGIN,
                screenWidth - SCREEN_EDGE_MARGIN - panelWidth
        );
        return Math.max(SCREEN_EDGE_MARGIN, Math.min(right, maxX));
    }

    public static int infoPanelX(int leftPos, int imageWidth, int screenWidth) {
        return sidePanelX(leftPos, imageWidth, screenWidth, INFO_PANEL_WIDTH);
    }

    public static boolean contains(
            double mouseX,
            double mouseY,
            int x,
            int y,
            int width,
            int height
    ) {
        return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
    }
}
