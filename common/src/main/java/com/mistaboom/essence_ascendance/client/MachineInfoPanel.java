package com.mistaboom.essence_ascendance.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Flowing text builder for the Info popup shared by normal machine screens.
 *
 * <p>Section headers are flush-left. Normal detail rows use one indentation
 * level, and wrapped continuation lines use two indentation levels. This keeps
 * long operational values readable without hard-coded Y coordinates or
 * ellipsis truncation.</p>
 */
public final class MachineInfoPanel {

    public static final int TEXT_PADDING = 7;
    public static final int INDENT = 8;
    public static final int WRAP_INDENT = 16;

    private static final int TITLE_ADVANCE = 13;
    private static final int LINE_ADVANCE = 11;
    private static final int WRAPPED_LINE_HEIGHT = 10;
    private static final int SECTION_GAP = 6;

    private final GuiGraphics graphics;
    private final Font font;
    private final int textX;
    private final int textWidth;
    private int cursorY;
    private boolean hasBodyContent;

    public MachineInfoPanel(
            GuiGraphics graphics,
            Font font,
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight
    ) {
        this.graphics = graphics;
        this.font = font;
        this.textX = panelX + TEXT_PADDING;
        this.textWidth = Math.max(0, panelWidth - TEXT_PADDING * 2);
        this.cursorY = panelY + 7;
        MachineScreenUi.panel(graphics, panelX, panelY, panelWidth, panelHeight);
    }

    public MachineInfoPanel title(String text) {
        MachineScreenUi.sectionHeader(graphics, font, text, textX, cursorY);
        cursorY += TITLE_ADVANCE;
        return this;
    }

    /** Flush-left metadata directly beneath the Info title, such as Owner. */
    public MachineInfoPanel metadata(String text) {
        int lines = drawWrapped(text, 0, INDENT);
        cursorY += advanceFor(lines);
        hasBodyContent = true;
        return this;
    }

    /** Starts a flush-left category heading after the standard inter-section gap. */
    public MachineInfoPanel section(String text) {
        if (hasBodyContent) {
            cursorY += SECTION_GAP;
        }
        MachineScreenUi.sectionHeader(graphics, font, text, textX, cursorY);
        cursorY += LINE_ADVANCE;
        hasBodyContent = false;
        return this;
    }

    /**
     * Draws a normal detail row. First line = one indent; wrapped continuation
     * lines = two indents.
     */
    public MachineInfoPanel line(String text) {
        int lines = drawWrapped(text, INDENT, WRAP_INDENT);
        cursorY += advanceFor(lines);
        hasBodyContent = true;
        return this;
    }

    public int cursorY() {
        return cursorY;
    }

    private int drawWrapped(String text, int firstIndent, int continuationIndent) {
        if (text == null || text.isBlank() || textWidth <= 0) {
            return 0;
        }

        int firstWidth = Math.max(0, textWidth - firstIndent);
        int continuationWidth = Math.max(0, textWidth - continuationIndent);
        List<String> lines = wrap(text.trim(), firstWidth, continuationWidth);
        for (int index = 0; index < lines.size(); index++) {
            int indent = index == 0 ? firstIndent : continuationIndent;
            graphics.drawString(
                    font,
                    lines.get(index),
                    textX + indent,
                    cursorY + index * WRAPPED_LINE_HEIGHT,
                    MachineScreenUi.MUTED,
                    false
            );
        }
        return lines.size();
    }

    private List<String> wrap(String text, int firstWidth, int continuationWidth) {
        List<String> lines = new ArrayList<>();
        String remaining = text;
        int maxWidth = firstWidth;

        while (!remaining.isEmpty()) {
            if (maxWidth <= 0) {
                break;
            }
            if (font.width(remaining) <= maxWidth) {
                lines.add(remaining);
                break;
            }

            int end = fittingPrefixLength(remaining, maxWidth);
            if (end <= 0) {
                break;
            }

            int breakAt = remaining.lastIndexOf(' ', Math.min(end, remaining.length() - 1));
            if (breakAt <= 0) {
                breakAt = end;
            }

            String line = remaining.substring(0, breakAt).stripTrailing();
            if (line.isEmpty()) {
                line = remaining.substring(0, end);
                breakAt = end;
            }
            lines.add(line);
            remaining = remaining.substring(Math.min(remaining.length(), breakAt)).stripLeading();
            maxWidth = continuationWidth;
        }

        if (lines.isEmpty() && !text.isEmpty()) {
            lines.add(text);
        }
        return lines;
    }

    private int fittingPrefixLength(String text, int maxWidth) {
        int low = 1;
        int high = text.length();
        int best = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (font.width(text.substring(0, mid)) <= maxWidth) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private static int advanceFor(int lines) {
        return lines <= 0 ? 0 : lines * WRAPPED_LINE_HEIGHT + 1;
    }
}
