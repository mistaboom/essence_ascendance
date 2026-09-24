package com.mistaboom.essence_ascendance.client.ui.fullscreen;

import com.mistaboom.essence_ascendance.client.ui.UiBounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Screen-independent geometry for fullscreen navigation and content. */
public final class FullscreenLayout {
    private FullscreenLayout() { }

    public record Spec(int margin, int modeWidth, int modeY, int modeHeight,
                       int secondaryY, int secondaryHeight, int secondaryGap,
                       int bottomMargin, int contentTopWithoutSecondary, int contentTopWithSecondary) {
        public Spec {
            if (margin < 0 || modeWidth < 0 || modeY < 0 || modeHeight < 0
                    || secondaryY < 0 || secondaryHeight < 0 || secondaryGap < 0
                    || bottomMargin < 0 || contentTopWithoutSecondary < 0 || contentTopWithSecondary < 0) {
                throw new IllegalArgumentException("Fullscreen dimensions cannot be negative");
            }
        }

        public static Spec standard() {
            return new Spec(14, 306, 5, 24, 34, 20, 3, 18, 34, 58);
        }
    }

    public record Frame(UiBounds screen, UiBounds modes, UiBounds secondary, UiBounds content) { }
    public record Bands(UiBounds header, UiBounds body, UiBounds footer) { }
    public record Split(UiBounds list, UiBounds detail) { }
    public record Tabs(UiBounds leftArrow, UiBounds rightArrow, List<UiBounds> items, int visibleCount) {
        public Tabs {
            items = List.copyOf(items);
            if (visibleCount != items.size()) {
                throw new IllegalArgumentException("Visible tab count must match its bounds");
            }
        }
    }

    public static Frame frame(Spec spec, int width, int height, boolean secondary) {
        Objects.requireNonNull(spec, "spec");
        UiBounds screen = new UiBounds(0, 0, Math.max(0, width), Math.max(0, height));
        int margin = Math.min(spec.margin(), screen.width() / 2);
        int available = screen.width() - margin * 2;
        int modeWidth = Math.min(spec.modeWidth(), available);
        UiBounds modes = boundedBand(screen, (screen.width() - modeWidth) / 2,
                spec.modeY(), modeWidth, spec.modeHeight());
        UiBounds secondaryBand = boundedBand(screen, margin, spec.secondaryY(), available,
                secondary ? spec.secondaryHeight() : 0);
        int top = Math.min(screen.height(), secondary
                ? spec.contentTopWithSecondary() : spec.contentTopWithoutSecondary());
        int bottom = Math.max(top, screen.height() - spec.bottomMargin());
        return new Frame(screen, modes, secondaryBand, new UiBounds(margin, top, available, bottom - top));
    }

    /** Divide a band into equal adjacent cells, retaining every remainder pixel. */
    public static List<UiBounds> segments(UiBounds band, int count, int gap) {
        if (count <= 0) return List.of();
        int actualGap = count == 1 ? 0 : Math.min(Math.max(0, gap), band.width() / (count - 1));
        int available = band.width() - actualGap * (count - 1);
        int cellWidth = available / count;
        List<UiBounds> result = new ArrayList<>(count);
        int x = band.x();
        for (int index = 0; index < count; index++) {
            int width = index == count - 1 ? band.right() - x : cellWidth;
            result.add(new UiBounds(x, band.y(), width, band.height()));
            x += width + actualGap;
        }
        return List.copyOf(result);
    }

    /**
     * Center an ordered row at its requested widths. When the band is too small,
     * shrink cells proportionally and reduce gaps enough to retain usable cells.
     */
    public static List<UiBounds> fixedWidthsRow(UiBounds band, List<Integer> widths, int gap) {
        if (widths.isEmpty()) return List.of();
        long requestedWidth = 0;
        int positiveWidths = 0;
        for (int width : widths) {
            requestedWidth += Math.max(0, width);
            if (width > 0) positiveWidths++;
        }
        int gapBudget = Math.max(0, band.width() - Math.min(band.width(), positiveWidths));
        int actualGap = widths.size() == 1 ? 0
                : Math.min(Math.max(0, gap), gapBudget / (widths.size() - 1));
        int gapsWidth = actualGap * (widths.size() - 1);
        int cellsWidth = (int) Math.min(requestedWidth, band.width() - gapsWidth);
        int x = band.x() + (band.width() - gapsWidth - cellsWidth) / 2;
        int assignedWidth = 0;
        long cumulativeWidth = 0;
        List<UiBounds> items = new ArrayList<>(widths.size());
        for (int requested : widths) {
            cumulativeWidth += Math.max(0, requested);
            int cumulativePixels = requestedWidth == 0 ? 0
                    : (int) (cumulativeWidth * cellsWidth / requestedWidth);
            int width = cumulativePixels - assignedWidth;
            items.add(new UiBounds(x, band.y(), width, band.height()));
            assignedWidth += width;
            x += width + actualGap;
        }
        return List.copyOf(items);
    }

    /** Label widths are measured before the shared fourteen-pixel tab padding is added. */
    public static Tabs tabs(UiBounds band, List<Integer> measuredWidths,
                            int minWidth, int maxWidth, int arrowWidth, int gap) {
        int actualArrowWidth = Math.min(Math.max(0, arrowWidth), band.width() / 2);
        UiBounds leftArrow = new UiBounds(band.x(), band.y(), actualArrowWidth, band.height());
        UiBounds rightArrow = new UiBounds(band.right() - actualArrowWidth, band.y(), actualArrowWidth, band.height());
        int spaceBetweenArrows = band.width() - actualArrowWidth * 2;
        int actualGap = Math.min(Math.max(0, gap), spaceBetweenArrows / 2);
        int available = spaceBetweenArrows - actualGap * 2;
        if (measuredWidths.isEmpty()) return new Tabs(leftArrow, rightArrow, List.of(), 0);
        int desiredWidth = Math.max(1, minWidth);
        for (int labelWidth : measuredWidths) {
            desiredWidth = Math.max(desiredWidth, Math.max(0, labelWidth) + 14);
        }
        desiredWidth = Math.min(available, Math.min(Math.max(1, maxWidth), desiredWidth));
        int visibleCount = Math.min(measuredWidths.size(), Math.max(1,
                (available + actualGap) / Math.max(1, desiredWidth + actualGap)));
        int usedWidth = desiredWidth * visibleCount + actualGap * (visibleCount - 1);
        int x = leftArrow.right() + actualGap + (available - usedWidth) / 2;
        List<UiBounds> items = new ArrayList<>(visibleCount);
        for (int index = 0; index < visibleCount; index++) {
            items.add(new UiBounds(x, band.y(), desiredWidth, band.height()));
            x += desiredWidth + actualGap;
        }
        return new Tabs(leftArrow, rightArrow, items, visibleCount);
    }

    public static Bands bands(UiBounds bounds, int topHeight, int bottomHeight) {
        return bands(bounds, topHeight, bottomHeight, 0);
    }

    /** Partition vertical content with a shared gap between each populated band. */
    public static Bands bands(UiBounds bounds, int topHeight, int bottomHeight, int gap) {
        int headerHeight = Math.min(Math.max(0, topHeight), bounds.height());
        int footerHeight = Math.min(Math.max(0, bottomHeight), bounds.height() - headerHeight);
        int separators = (headerHeight > 0 ? 1 : 0) + (footerHeight > 0 ? 1 : 0);
        int remaining = bounds.height() - headerHeight - footerHeight;
        int actualGap = separators == 0 ? 0 : Math.min(Math.max(0, gap), remaining / separators);
        int headerGap = headerHeight > 0 ? actualGap : 0;
        int footerGap = footerHeight > 0 ? actualGap : 0;
        UiBounds header = new UiBounds(bounds.x(), bounds.y(), bounds.width(), headerHeight);
        UiBounds body = new UiBounds(bounds.x(), header.bottom() + headerGap, bounds.width(),
                remaining - headerGap - footerGap);
        UiBounds footer = new UiBounds(bounds.x(), body.bottom() + footerGap, bounds.width(), footerHeight);
        return new Bands(header, body, footer);
    }

    public static Split listDetail(UiBounds bounds, int preferredListWidth, int gap) {
        int actualGap = Math.min(Math.max(0, gap), bounds.width());
        int listWidth = Math.min(Math.max(0, preferredListWidth), bounds.width() - actualGap);
        UiBounds list = new UiBounds(bounds.x(), bounds.y(), listWidth, bounds.height());
        UiBounds detail = new UiBounds(list.right() + actualGap, bounds.y(),
                bounds.width() - listWidth - actualGap, bounds.height());
        return new Split(list, detail);
    }

    public static UiBounds centered(UiBounds bounds, int preferredWidth, int preferredHeight, int margin) {
        UiBounds available = bounds.inset(margin);
        int width = Math.min(Math.max(0, preferredWidth), available.width());
        int height = Math.min(Math.max(0, preferredHeight), available.height());
        return new UiBounds(available.x() + (available.width() - width) / 2,
                available.y() + (available.height() - height) / 2, width, height);
    }

    private static UiBounds boundedBand(UiBounds screen, int x, int y, int width, int height) {
        int top = Math.min(y, screen.height());
        return new UiBounds(x, top, width, Math.min(height, screen.height() - top));
    }
}
