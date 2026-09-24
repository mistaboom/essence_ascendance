package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.procedural.GuiProceduralGeometry;
import com.mistaboom.essence_ascendance.client.ui.StyledTextLayout;
import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.client.ui.UiViewport;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Measured, component-preserving content model for machine information panels.
 * Content is declared independently of rendering, then reflowed for the actual
 * width. The resulting lines retain Minecraft styles for links and hover data.
 */
public final class MachineInfoPanel {

    public static final int TEXT_PADDING = 7;
    public static final int INDENT = 8;
    public static final int WRAP_INDENT = 16;

    private static final int TOP_PADDING = 7;
    private static final int BOTTOM_PADDING = 7;
    private static final int TITLE_ADVANCE = 13;
    private static final int LINE_ADVANCE = 11;
    private static final int WRAPPED_LINE_HEIGHT = 10;
    private static final int SECTION_GAP = 6;

    private enum EntryRole {
        TITLE,
        METADATA,
        SECTION,
        LINE
    }

    private record Entry(EntryRole role, Component text) {
    }

    public record PositionedLine(
            FormattedCharSequence text,
            int x,
            int y,
            int width,
            int color
    ) {
    }

    public record Layout(List<PositionedLine> lines, int contentHeight) {
        public Layout {
            lines = List.copyOf(lines);
        }

        public UiViewport viewport(int panelHeight, int requestedOffset) {
            int visibleHeight = Math.max(0, panelHeight - 2);
            return UiViewport.create(contentHeight, visibleHeight, requestedOffset);
        }

        /** Resolve the original styled run at a rendered point, if any. */
        public Style styleAt(
                Font font,
                UiBounds panelBounds,
                int scrollOffset,
                double mouseX,
                double mouseY
        ) {
            if (!panelBounds.contains(mouseX, mouseY)) {
                return null;
            }
            double localX = mouseX - panelBounds.x();
            double localY = mouseY - panelBounds.y() + scrollOffset;
            for (PositionedLine line : lines) {
                if (localY < line.y() || localY >= line.y() + font.lineHeight
                        || localX < line.x() || localX >= line.x() + line.width()) {
                    continue;
                }
                return font.getSplitter().componentStyleAtWidth(
                        line.text(),
                        (int) localX - line.x()
                );
            }
            return null;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public MachineInfoPanel title(Component text) {
        return add(EntryRole.TITLE, text);
    }

    /** Flush-left metadata directly beneath the title, such as Owner. */
    public MachineInfoPanel metadata(Component text) {
        return add(EntryRole.METADATA, text);
    }

    /** Starts a flush-left category heading after the standard inter-section gap. */
    public MachineInfoPanel section(Component text) {
        return add(EntryRole.SECTION, text);
    }

    /** Normal detail row: one indent, with a second indent for continuations. */
    public MachineInfoPanel line(Component text) {
        return add(EntryRole.LINE, text);
    }

    public Layout layout(Font font, int panelWidth) {
        int textWidth = Math.max(0, panelWidth - TEXT_PADDING * 2);
        int cursorY = TOP_PADDING;
        boolean hasBodyContent = false;
        List<PositionedLine> lines = new ArrayList<>();

        for (Entry entry : entries) {
            Component text = entry.text();
            if (text == null || textWidth <= 0) {
                continue;
            }
            switch (entry.role()) {
                case TITLE -> {
                    addFittedLine(lines, font, text, TEXT_PADDING, cursorY, textWidth,
                            MachineScreenUi.TEXT);
                    cursorY += TITLE_ADVANCE;
                }
                case SECTION -> {
                    if (hasBodyContent) {
                        cursorY += SECTION_GAP;
                    }
                    addFittedLine(lines, font, text, TEXT_PADDING, cursorY, textWidth,
                            MachineScreenUi.TEXT);
                    cursorY += LINE_ADVANCE;
                    hasBodyContent = false;
                }
                case METADATA -> {
                    int count = addWrappedLines(
                            lines, font, text, textWidth, 0, INDENT, cursorY
                    );
                    cursorY += advanceFor(count);
                    hasBodyContent = true;
                }
                case LINE -> {
                    int count = addWrappedLines(
                            lines, font, text, textWidth, INDENT, WRAP_INDENT, cursorY
                    );
                    cursorY += advanceFor(count);
                    hasBodyContent = true;
                }
            }
        }
        return new Layout(lines, cursorY + BOTTOM_PADDING);
    }

    public void render(
            GuiGraphics graphics,
            Font font,
            UiBounds bounds,
            Layout layout,
            int requestedScrollOffset
    ) {
        MachineScreenUi.panel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        UiViewport viewport = layout.viewport(bounds.height(), requestedScrollOffset);
        UiBounds clip = bounds.inset(1);
        GuiProceduralGeometry.clipped(
                graphics,
                clip.x(),
                clip.y(),
                clip.right(),
                clip.bottom(),
                () -> {
                    for (PositionedLine line : layout.lines()) {
                        int renderedY = bounds.y() + line.y() - viewport.offset();
                        if (renderedY + font.lineHeight <= clip.y() || renderedY >= clip.bottom()) {
                            continue;
                        }
                        graphics.drawString(
                                font,
                                line.text(),
                                bounds.x() + line.x(),
                                renderedY,
                                line.color(),
                                false
                        );
                    }
                }
        );
        if (viewport.scrollable()) {
            int trackTop = bounds.y() + 4;
            int trackHeight = Math.max(1, bounds.height() - 8);
            int thumbHeight = Math.max(
                    8,
                    (int) Math.round(trackHeight * (viewport.viewportSize() / (double) viewport.contentSize()))
            );
            thumbHeight = Math.min(trackHeight, thumbHeight);
            int travel = trackHeight - thumbHeight;
            int thumbY = trackTop + (viewport.maximumOffset() == 0
                    ? 0
                    : (int) Math.round(travel * (viewport.offset() / (double) viewport.maximumOffset())));
            graphics.fill(bounds.right() - 3, trackTop, bounds.right() - 2,
                    trackTop + trackHeight, MachineScreenUi.DIVIDER);
            graphics.fill(bounds.right() - 4, thumbY, bounds.right() - 1,
                    thumbY + thumbHeight, MachineScreenUi.BORDER);
        }
    }

    private MachineInfoPanel add(EntryRole role, Component text) {
        if (text != null) {
            entries.add(new Entry(role, text));
        }
        return this;
    }

    private static void addFittedLine(
            List<PositionedLine> output,
            Font font,
            Component text,
            int x,
            int y,
            int maximumWidth,
            int color
    ) {
        FormattedCharSequence line = StyledTextLayout.fit(font, text, maximumWidth);
        output.add(new PositionedLine(line, x, y, font.width(line), color));
    }

    private static int addWrappedLines(
            List<PositionedLine> output,
            Font font,
            Component text,
            int textWidth,
            int firstIndent,
            int continuationIndent,
            int y
    ) {
        /*
         * Wrapping at the narrower continuation width guarantees that every
         * styled line remains valid at either indentation without flattening
         * and re-tokenizing localized text.
         */
        int maximumWidth = Math.max(1, textWidth - Math.max(firstIndent, continuationIndent));
        List<FormattedCharSequence> wrapped = StyledTextLayout.wrap(font, text, maximumWidth);
        for (int index = 0; index < wrapped.size(); index++) {
            FormattedCharSequence line = wrapped.get(index);
            int indent = index == 0 ? firstIndent : continuationIndent;
            output.add(new PositionedLine(
                    line,
                    TEXT_PADDING + indent,
                    y + index * WRAPPED_LINE_HEIGHT,
                    font.width(line),
                    MachineScreenUi.MUTED
            ));
        }
        return wrapped.size();
    }

    private static int advanceFor(int lines) {
        return lines <= 0 ? 0 : lines * WRAPPED_LINE_HEIGHT + 1;
    }
}
