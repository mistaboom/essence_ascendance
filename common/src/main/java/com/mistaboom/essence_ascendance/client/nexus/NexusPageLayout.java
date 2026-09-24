package com.mistaboom.essence_ascendance.client.nexus;

import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenLayout;

/** Nexus-specific track and reservoir reservations inside the shared fullscreen content region. */
public record NexusPageLayout(int left, int top, int right, int bottom,
                              int gaugeLeft, int gaugeRight, int tracksLeft, int tracksRight,
                              int sectionHeight, int middleTop, int middleBottom, int trackTop, int trackBottom) {
    private static final int TRACK_WIDTH = 66;
    private static final int TRACK_GAP = 4;
    private static final int MINIMUM_SIDE_WIDTH = 58;
    private static final int SECTION_HEIGHT = 34;
    private static final int NARROW_SECTION_HEIGHT = 30;

    public static NexusPageLayout compose(UiBounds content, int screenHeight) {
        // Grow the central reservation only when another complete preferred-width track fits.
        // Remaining space is split evenly around it, preserving the centered reservoirs.
        int centerBudget = Math.min(content.width(), Math.max(1, content.width() - 2 * MINIMUM_SIDE_WIDTH));
        int centerWidth;
        if (centerBudget < TRACK_WIDTH) {
            centerWidth = centerBudget;
        } else {
            int wholeTracks = Math.max(1, (centerBudget + TRACK_GAP) / (TRACK_WIDTH + TRACK_GAP));
            centerWidth = TRACK_WIDTH * wholeTracks + TRACK_GAP * (wholeTracks - 1);
        }
        int sideWidth = (content.width() - centerWidth) / 2;
        int gaugeWidth = sideWidth >= 20 ? Math.min(42, Math.max(20, sideWidth - 12)) : sideWidth;
        int gaugeLeft = content.x() + (sideWidth - gaugeWidth) / 2;
        int tracksLeft = content.x() + sideWidth;
        int tracksRight = content.right() - sideWidth;

        int desiredSectionHeight = screenHeight < 230 ? NARROW_SECTION_HEIGHT : SECTION_HEIGHT;
        int maximumSectionHeight = Math.max(18, (content.height() - 72) / 2);
        int sectionHeight = Math.min(Math.max(0, (content.height() - 1) / 2),
                Math.max(18, Math.min(desiredSectionHeight, maximumSectionHeight)));
        FullscreenLayout.Bands bands = FullscreenLayout.bands(content, sectionHeight, sectionHeight);
        int middleTop = bands.body().y();
        int middleBottom = bands.body().bottom();

        int trackHeaderReserve = screenHeight < 230 ? 32 : 38;
        int trackInfoReserve = screenHeight < 230 ? 29 : 33;
        int lastBodyPixel = Math.max(middleTop, middleBottom - 1);
        int trackTop = Math.min(lastBodyPixel, middleTop + trackHeaderReserve);
        int trackBottom = Math.min(lastBodyPixel, Math.max(trackTop + 1, middleBottom - trackInfoReserve));
        return new NexusPageLayout(content.x(), content.y(), content.right(), content.bottom(),
                gaugeLeft, gaugeLeft + gaugeWidth, tracksLeft, tracksRight,
                sectionHeight, middleTop, middleBottom, trackTop, trackBottom);
    }

    public int width() { return right - left; }
    public int height() { return bottom - top; }
}
