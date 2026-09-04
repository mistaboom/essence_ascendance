package com.mistaboom.essence_ascendance.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Shared procedural presentation primitives for normal Essence Ascendance
 * machine/container screens. The fullscreen Nexus intentionally does not use
 * this system.
 *
 * <p>Component overloads are the preferred API. String overloads remain as a
 * compatibility bridge for non-player-facing/dynamic text while the rest of
 * the mod migrates to translation keys.</p>
 */
public final class MachineScreenUi {

    public static final int PANEL = 0xFF20242B;
    public static final int PANEL_INNER = 0xFF2D333D;
    public static final int BORDER = 0xFF8A70B5;
    public static final int DIVIDER = 0xFF535B68;
    public static final int TEXT = 0xFFE9E9EF;
    public static final int MUTED = 0xFFAEB4C0;
    public static final int GOOD = 0xFF86D98C;
    public static final int WARN = 0xFFE4C36A;
    public static final int BAD = 0xFFE27777;

    private MachineScreenUi() {
    }

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        outline(graphics, x, y, width, height, BORDER);
    }

    public static void inset(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_INNER);
        outline(graphics, x, y, width, height, DIVIDER);
    }

    public static void accentedInset(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_INNER);
        outline(graphics, x, y, width, height, BORDER);
    }

    /** Standard normal-machine input/socket treatment. */
    public static void inputSlot(GuiGraphics graphics, int x, int y) {
        accentedInset(graphics, x, y, 20, 20);
    }

    /** Standard normal-machine output treatment. */
    public static void outputSlot(GuiGraphics graphics, int x, int y) {
        inset(graphics, x, y, 20, 20);
    }

    public static void progressBar(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int progress,
            int required
    ) {
        progressBar(graphics, x, y, width, height, (long) progress, (long) required);
    }

    public static void progressBar(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            long progress,
            long required
    ) {
        inset(graphics, x, y, width, height);
        if (required <= 0L || progress <= 0L) {
            return;
        }
        long clamped = Math.min(required, progress);
        int innerWidth = Math.max(0, width - 2);
        int fillWidth = (int) Math.round(innerWidth * (clamped / (double) required));
        if (fillWidth > 0) {
            graphics.fill(
                    x + 1,
                    y + 1,
                    x + 1 + Math.min(innerWidth, fillWidth),
                    y + height - 1,
                    BORDER
            );
        }
    }

    public static void sectionHeader(
            GuiGraphics graphics,
            Font font,
            Component text,
            int x,
            int y
    ) {
        graphics.drawString(font, text, x, y, TEXT, false);
    }

    public static void sectionHeader(
            GuiGraphics graphics,
            Font font,
            String text,
            int x,
            int y
    ) {
        sectionHeader(graphics, font, Component.literal(text), x, y);
    }

    public static void row(
            GuiGraphics graphics,
            Font font,
            Component label,
            Component value,
            int left,
            int right,
            int y
    ) {
        row(graphics, font, label, value, left, right, y, TEXT);
    }

    public static void row(
            GuiGraphics graphics,
            Font font,
            Component label,
            Component value,
            int left,
            int right,
            int y,
            int valueColor
    ) {
        graphics.drawString(font, label, left, y, MUTED, false);
        graphics.drawString(font, value, right - font.width(value), y, valueColor, false);
    }


    public static void row(
            GuiGraphics graphics,
            Font font,
            Component label,
            String value,
            int left,
            int right,
            int y
    ) {
        row(graphics, font, label, Component.literal(value), left, right, y, TEXT);
    }

    public static void row(
            GuiGraphics graphics,
            Font font,
            Component label,
            String value,
            int left,
            int right,
            int y,
            int valueColor
    ) {
        row(graphics, font, label, Component.literal(value), left, right, y, valueColor);
    }

    public static void row(
            GuiGraphics graphics,
            Font font,
            String label,
            Component value,
            int left,
            int right,
            int y
    ) {
        row(graphics, font, Component.literal(label), value, left, right, y, TEXT);
    }

    public static void row(
            GuiGraphics graphics,
            Font font,
            String label,
            Component value,
            int left,
            int right,
            int y,
            int valueColor
    ) {
        row(graphics, font, Component.literal(label), value, left, right, y, valueColor);
    }
    public static void row(
            GuiGraphics graphics,
            Font font,
            String label,
            String value,
            int left,
            int right,
            int y
    ) {
        row(graphics, font, Component.literal(label), Component.literal(value), left, right, y, TEXT);
    }

    public static void row(
            GuiGraphics graphics,
            Font font,
            String label,
            String value,
            int left,
            int right,
            int y,
            int valueColor
    ) {
        row(graphics, font, Component.literal(label), Component.literal(value), left, right, y, valueColor);
    }

    public static void indentedLine(
            GuiGraphics graphics,
            Font font,
            Component text,
            int x,
            int y,
            int maxWidth
    ) {
        fitted(graphics, font, text, x + 8, y, Math.max(0, maxWidth - 8), MUTED);
    }

    public static void indentedLine(
            GuiGraphics graphics,
            Font font,
            String text,
            int x,
            int y,
            int maxWidth
    ) {
        indentedLine(graphics, font, Component.literal(text), x, y, maxWidth);
    }

    public static void fitted(
            GuiGraphics graphics,
            Font font,
            Component text,
            int x,
            int y,
            int maxWidth,
            int color
    ) {
        if (text == null) {
            return;
        }
        fitted(graphics, font, text.getString(), x, y, maxWidth, color);
    }

    public static void fitted(
            GuiGraphics graphics,
            Font font,
            String text,
            int x,
            int y,
            int maxWidth,
            int color
    ) {
        if (maxWidth <= 0 || text == null || text.isEmpty()) {
            return;
        }
        if (font.width(text) <= maxWidth) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (ellipsisWidth > maxWidth) {
            return;
        }
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end)) + ellipsisWidth > maxWidth) {
            end--;
        }
        graphics.drawString(font, text.substring(0, end) + ellipsis, x, y, color, false);
    }

    public static int wrapped(
            GuiGraphics graphics,
            Font font,
            Component text,
            int x,
            int y,
            int maxWidth,
            int color,
            int lineHeight,
            int maxLines
    ) {
        if (maxWidth <= 0 || maxLines <= 0 || text == null) {
            return 0;
        }

        List<net.minecraft.util.FormattedCharSequence> lines = font.split(text, maxWidth);
        int rendered = Math.min(maxLines, lines.size());
        for (int line = 0; line < rendered; line++) {
            graphics.drawString(
                    font,
                    lines.get(line),
                    x,
                    y + line * lineHeight,
                    color,
                    false
            );
        }
        return rendered;
    }

    public static int wrapped(
            GuiGraphics graphics,
            Font font,
            String text,
            int x,
            int y,
            int maxWidth,
            int color,
            int lineHeight,
            int maxLines
    ) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return wrapped(
                graphics,
                font,
                Component.literal(text),
                x,
                y,
                maxWidth,
                color,
                lineHeight,
                maxLines
        );
    }

    public static void outline(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
