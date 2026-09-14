package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

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
    public static final int BORDER = AscendancePalette.opaque(AscendancePalette.AWAKENED.primaryRgb());
    public static final int DIVIDER = 0xFF535B68;
    public static final int TEXT = 0xFFE9E9EF;
    public static final int MUTED = 0xFFAEB4C0;
    public static final int GOOD = 0xFF86D98C;
    public static final int WARN = 0xFFE4C36A;
    public static final int BAD = 0xFFE27777;

    private static final int ITEM_SLOT_SIZE = 20;
    private static final int ITEM_SLOT_INSET = 2; // (20px frame - 16px item) / 2

    private MachineScreenUi() {
    }

    /** Shared integer drawing primitives for crystals, activity glyphs and future machine feedback. */
    public static void beam(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) { graphics.fill(x1, y1, x1 + 1, y1 + 1, color); return; }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (int) Math.round((x2 - x1) * (double) i / steps);
            int y = y1 + (int) Math.round((y2 - y1) * (double) i / steps);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    public static void orbit(GuiGraphics graphics, int x, int y, double rx, double ry,
                             int sides, double rotation, int color) {
        int segments = Math.clamp(sides, 3, 96);
        for (int i = 0; i < segments; i++) {
            double a = rotation + Math.PI * 2 * i / segments;
            double b = rotation + Math.PI * 2 * (i + 1) / segments;
            beam(graphics, x + (int) Math.round(Math.cos(a) * rx), y + (int) Math.round(Math.sin(a) * ry),
                    x + (int) Math.round(Math.cos(b) * rx), y + (int) Math.round(Math.sin(b) * ry), color);
        }
    }

    public static int opacity(int color, int alpha) {
        return Math.clamp(alpha, 0, 255) << 24 | color & 0xFFFFFF;
    }

    /** A rising faceted diamond, with a bright edge and a darker opposing face. */
    public static void crystal(GuiGraphics graphics, int x, int y, int radius, double fraction, int color) {
        int r = Math.max(1, radius);
        double bounded = Math.clamp(fraction, 0, 1);
        int fillTop = y + r - (int) Math.ceil(2 * r * bounded);
        for (int dy = -r; dy <= r; dy++) {
            int half = r - Math.abs(dy);
            int rowColor = y + dy >= fillTop ? opacity(color, 215) : PANEL_INNER;
            graphics.fill(x - half, y + dy, x + 1, y + dy + 1, rowColor);
            graphics.fill(x + 1, y + dy, x + half + 1, y + dy + 1,
                    y + dy >= fillTop ? opacity(color, 120) : PANEL);
        }
        beam(graphics, x, y - r, x + r, y, color);
        beam(graphics, x + r, y, x, y + r, color);
        beam(graphics, x, y + r, x - r, y, color);
        beam(graphics, x - r, y, x, y - r, color);
        beam(graphics, x, y - r, x, y + r, opacity(0xFFF0EDF4, 105));
        beam(graphics, x - r, y, x, y + r / 3, opacity(0xFFF0EDF4, 85));
        beam(graphics, x, y + r / 3, x + r, y, opacity(0xFFF0EDF4, 85));
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
        accentedInset(graphics, x, y, ITEM_SLOT_SIZE, ITEM_SLOT_SIZE);
    }

    /** Standard normal-machine output treatment. */
    public static void outputSlot(GuiGraphics graphics, int x, int y) {
        inset(graphics, x, y, ITEM_SLOT_SIZE, ITEM_SLOT_SIZE);
    }

    /** Draws a socket around the actual menu slot, not an independent guessed origin. */
    public static void inputSlot(GuiGraphics graphics, int leftPos, int topPos, Slot slot) {
        itemSlot(graphics, leftPos, topPos, slot, BORDER);
    }

    public static void outputSlot(GuiGraphics graphics, int leftPos, int topPos, Slot slot) {
        itemSlot(graphics, leftPos, topPos, slot, DIVIDER);
    }

    /** Shared frame geometry, including unavailable-but-occupied Crucible lanes. */
    public static void itemSlot(
            GuiGraphics graphics, int leftPos, int topPos, Slot slot, int borderColor
    ) {
        int x = leftPos + slot.x - ITEM_SLOT_INSET;
        int y = topPos + slot.y - ITEM_SLOT_INSET;
        graphics.fill(x, y, x + ITEM_SLOT_SIZE, y + ITEM_SLOT_SIZE, PANEL_INNER);
        outline(graphics, x, y, ITEM_SLOT_SIZE, ITEM_SLOT_SIZE, borderColor);
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
