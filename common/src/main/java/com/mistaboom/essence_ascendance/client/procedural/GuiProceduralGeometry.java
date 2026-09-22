package com.mistaboom.essence_ascendance.client.procedural;

import com.mistaboom.essence_ascendance.visual.ProceduralMotion;
import net.minecraft.client.gui.GuiGraphics;

/** Pixel-aligned Ascendance primitives for any GUI. All positions use screen coordinates. */
public final class GuiProceduralGeometry {
    private static final int DEFAULT_EMPTY_LEFT = 0xFF2D333D;
    private static final int DEFAULT_EMPTY_RIGHT = 0xFF20242B;
    private static final int DEFAULT_GLINT = 0xFFF0EDF4;

    private GuiProceduralGeometry() { }

    /** Original one-pixel DDA beam, including its endpoint. */
    public static void beam(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) { graphics.fill(x1, y1, x1 + 1, y1 + 1, color); return; }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (int) Math.round((x2 - x1) * (double) i / steps);
            int y = y1 + (int) Math.round((y2 - y1) * (double) i / steps);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    /** Polygonal ellipse; a small side count creates the existing faceted sigil vocabulary. */
    public static void orbit(GuiGraphics graphics, int x, int y, double rx, double ry,
                             int sides, double rotation, int color) {
        int segments = Math.clamp(sides, 3, 96);
        for (int i = 0; i < segments; i++) {
            double a = ProceduralMotion.orbitAngle(i, segments, rotation);
            double b = ProceduralMotion.orbitAngle(i + 1, segments, rotation);
            beam(graphics, x + (int) Math.round(Math.cos(a) * rx), y + (int) Math.round(Math.sin(a) * ry),
                    x + (int) Math.round(Math.cos(b) * rx), y + (int) Math.round(Math.sin(b) * ry), color);
        }
    }

    /** Connected polygonal sweep; a sweep smaller than a full turn leaves a deliberate gap. */
    public static void arc(GuiGraphics graphics, int x, int y, double rx, double ry,
                           double start, double sweep, int segments, int color) {
        if (segments < 1) return;
        int px = x + (int) Math.round(Math.cos(start) * rx);
        int py = y + (int) Math.round(Math.sin(start) * ry);
        for (int i = 1; i <= segments; i++) {
            double angle = start + sweep * i / segments;
            int nx = x + (int) Math.round(Math.cos(angle) * rx);
            int ny = y + (int) Math.round(Math.sin(angle) * ry);
            beam(graphics, px, py, nx, ny, color);
            px = nx; py = ny;
        }
    }

    public static void brokenOrbit(GuiGraphics graphics, int x, int y, double rx, double ry,
                                   int pieces, int segmentsPerPiece, double rotation,
                                   double gapFraction, int color) {
        if (pieces < 1) return;
        double sector = Math.PI * 2.0 / pieces;
        double visible = sector * (1.0 - Math.clamp(gapFraction, 0.0, 1.0));
        for (int i = 0; i < pieces; i++)
            arc(graphics, x, y, rx, ry, rotation + sector * i, visible, segmentsPerPiece, color);
    }

    public static void spokes(GuiGraphics graphics, int x, int y, double innerRadius,
                              double outerRadius, int count, double rotation, int color) {
        if (count < 1) return;
        for (int i = 0; i < count; i++) {
            double angle = ProceduralMotion.orbitAngle(i, count, rotation);
            beam(graphics,
                    x + (int) Math.round(Math.cos(angle) * innerRadius),
                    y + (int) Math.round(Math.sin(angle) * innerRadius),
                    x + (int) Math.round(Math.cos(angle) * outerRadius),
                    y + (int) Math.round(Math.sin(angle) * outerRadius), color);
        }
    }

    /** Compact polygon and radial ticks around an arbitrary screen point. */
    public static void sigil(GuiGraphics graphics, int x, int y, double radius,
                             int sides, int ticks, double rotation, int color) {
        orbit(graphics, x, y, radius, radius, sides, rotation, color);
        spokes(graphics, x, y, radius + 1, radius + 3, ticks, rotation, color);
    }

    /** Radius interpolation supports both expansion and contraction. */
    public static void changingOrbit(GuiGraphics graphics, int x, int y,
                                     double startRadius, double endRadius, double progress,
                                     int sides, double rotation, int color) {
        double radius = startRadius + (endRadius - startRadius) * Math.clamp(progress, 0.0, 1.0);
        orbit(graphics, x, y, radius, radius, sides, rotation, color);
    }

    /** Rising faceted diamond. Empty-facet and glint colors can be supplied by another screen. */
    public static void crystal(GuiGraphics graphics, int x, int y, int radius,
                               double fraction, int color) {
        crystal(graphics, x, y, radius, fraction, color,
                DEFAULT_EMPTY_LEFT, DEFAULT_EMPTY_RIGHT, DEFAULT_GLINT);
    }

    public static void crystal(GuiGraphics graphics, int x, int y, int radius, double fraction,
                               int color, int emptyLeft, int emptyRight, int glint) {
        int r = Math.max(1, radius);
        double bounded = Math.clamp(fraction, 0, 1);
        int fillTop = y + r - (int) Math.ceil(2 * r * bounded);
        for (int dy = -r; dy <= r; dy++) {
            int half = r - Math.abs(dy);
            int rowColor = y + dy >= fillTop ? opacity(color, 215) : emptyLeft;
            graphics.fill(x - half, y + dy, x + 1, y + dy + 1, rowColor);
            graphics.fill(x + 1, y + dy, x + half + 1, y + dy + 1,
                    y + dy >= fillTop ? opacity(color, 120) : emptyRight);
        }
        beam(graphics, x, y - r, x + r, y, color);
        beam(graphics, x + r, y, x, y + r, color);
        beam(graphics, x, y + r, x - r, y, color);
        beam(graphics, x - r, y, x, y - r, color);
        beam(graphics, x, y - r, x, y + r, opacity(glint, 105));
        beam(graphics, x - r, y, x, y + r / 3, opacity(glint, 85));
        beam(graphics, x, y + r / 3, x + r, y, opacity(glint, 85));
    }

    /** A faceted pulse along a straight GUI path. Keep phase selection with the owning effect. */
    public static void travelingCrystal(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                        double phase, int radius, double fraction, int color) {
        int x = x1 + (int) ((x2 - x1) * phase);
        int y = y1 + (int) ((y2 - y1) * phase);
        crystal(graphics, x, y, radius, fraction, color);
    }

    public static int opacity(int color, int alpha) {
        return Math.clamp(alpha, 0, 255) << 24 | color & 0xFFFFFF;
    }

    /** Restore the previous scissor stack even if drawing throws. Coordinates are screen pixels. */
    public static void clipped(GuiGraphics graphics, int left, int top, int right, int bottom,
                               Runnable draw) {
        graphics.enableScissor(left, top, right, bottom);
        try { draw.run(); }
        finally { graphics.disableScissor(); }
    }

    /** A scoped pose transform for transient effects anchored to a point or slot center. */
    public static void transformed(GuiGraphics graphics, double x, double y, float scale,
                                   Runnable draw) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scale, scale, 1);
            draw.run();
        } finally { graphics.pose().popPose(); }
    }
}
