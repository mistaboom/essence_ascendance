package com.mistaboom.essence_ascendance.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Width-driven layout shared by current and future compact multi-value tooltips. */
public final class TooltipLayout {
    private TooltipLayout() { }

    public static int compactWidth(int existingWidth, int screenWidth) {
        return Math.max(1, Math.min(Math.max(1, screenWidth - 32),
                Math.min(220, Math.max(144, existingWidth))));
    }

    public record Viewport<T>(List<T> lines, int offset, int maximumOffset) { }

    /** Pin the title and reserve one footer row when content needs wheel scrolling. */
    public static <T> Viewport<T> viewport(List<T> lines, int maximumLines, int offset) {
        if (maximumLines < 3) throw new IllegalArgumentException("Tooltip viewport needs at least three rows");
        if (lines.size() <= maximumLines) return new Viewport<>(List.copyOf(lines), 0, 0);
        int contentRows = maximumLines - 2;
        int maximumOffset = lines.size() - 1 - contentRows;
        int clamped = Math.clamp(offset, 0, maximumOffset);
        List<T> visible = new ArrayList<>();
        visible.add(lines.getFirst());
        visible.addAll(lines.subList(1 + clamped, 1 + clamped + contentRows));
        return new Viewport<>(List.copyOf(visible), clamped, maximumOffset);
    }

    /** Keep each value/name pair atomic; never wrap a number away from its Essence. */
    public static <T> List<List<T>> wrapTokens(List<T> tokens, ToIntFunction<T> width,
                                              int firstPrefixWidth, int continuationPrefixWidth,
                                              int separatorWidth, int maximumWidth) {
        if (firstPrefixWidth < 0 || continuationPrefixWidth < 0 || separatorWidth < 0 || maximumWidth < 1)
            throw new IllegalArgumentException("Invalid tooltip dimensions");
        List<List<T>> lines = new ArrayList<>();
        List<T> current = new ArrayList<>();
        int used = firstPrefixWidth;
        for (T token : tokens) {
            int tokenWidth = width.applyAsInt(token);
            if (tokenWidth < 0) throw new IllegalArgumentException("Negative token width");
            long next = (long) used + (current.isEmpty() ? 0 : separatorWidth) + tokenWidth;
            if (!current.isEmpty() && next > maximumWidth) {
                lines.add(List.copyOf(current));
                current.clear();
                used = continuationPrefixWidth;
            }
            used += (current.isEmpty() ? 0 : separatorWidth) + tokenWidth;
            current.add(token);
        }
        if (!current.isEmpty()) lines.add(List.copyOf(current));
        return List.copyOf(lines);
    }
}
