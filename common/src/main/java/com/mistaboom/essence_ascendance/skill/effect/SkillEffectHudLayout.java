package com.mistaboom.essence_ascendance.skill.effect;

/** One fixed information budget. Extra context is omitted, never given extra height or smaller type. */
public final class SkillEffectHudLayout {
    public static final int WIDTH = 200, HEIGHT = 44, PADDING = 7, GAP = 3;
    public static final int TITLE_Y = 4, BADGE_Y = 14, DETAIL_Y = 24, ROW_HEIGHT = 9, DETAIL_ROWS = 2;
    public static final int CONTENT_WIDTH = WIDTH - 2 * PADDING;
    public static final int ACCENT_WIDTH = 3, FILL_WIDTH = WIDTH - ACCENT_WIDTH, STATUS_GAP = 6;
    public static final int BACKGROUND = 0xFF11131A;
    private SkillEffectHudLayout() { }
    public static int height(int detailLines) { return HEIGHT; }
    /** Both status columns use full-size text; redundant timer labels yield before any values do. */
    public static String timerText(String badge, String label, String value,
                                   java.util.function.ToIntFunction<String> width) {
        String labelled = label.isBlank() || label.equalsIgnoreCase(badge) ? value : label + " " + value;
        return width.applyAsInt(badge) + STATUS_GAP + width.applyAsInt(labelled) <= CONTENT_WIDTH
                ? labelled : value;
    }
    /** All cards share this background meter; states and timers have no fabricated progress. */
    public static int progressWidth(SkillEffectHudEntry.Meter meter) {
        if (meter.kind() != SkillEffectHudEntry.MeterKind.PROGRESS || !Double.isFinite(meter.fraction())) return 0;
        return (int) Math.round(FILL_WIDTH * Math.clamp(meter.fraction(), 0.0, 1.0));
    }
    public static int fillColor(int accent) {
        return blend(BACKGROUND, accent, 0.4);
    }
    /** One stable foreground works on both sides of the moving fill boundary. */
    public static int textColor(int preferred, int fill) {
        for (int step = 0; step <= 20; step++) {
            int candidate = blend(preferred, 0xFFFFFFFF, step / 20.0);
            if (contrast(candidate, fill) >= 4.5 && contrast(candidate, BACKGROUND) >= 4.5) return candidate;
        }
        return 0xFFFFFFFF;
    }
    public static double contrast(int first, int second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }
    private static double luminance(int color) {
        return 0.2126 * linear((color >> 16) & 255) + 0.7152 * linear((color >> 8) & 255)
                + 0.0722 * linear(color & 255);
    }
    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
    private static int blend(int from, int to, double fraction) {
        int color = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int a = (from >> shift) & 255, b = (to >> shift) & 255;
            color |= (int) Math.round(a + (b - a) * fraction) << shift;
        }
        return color;
    }
    public static Header header(int titlePixels, int badgePixels) {
        return new Header(1, CONTENT_WIDTH, CONTENT_WIDTH, CONTENT_WIDTH);
    }
    public record Header(float scale, int rightEdge, int titleLimit, int badgeLimit) { }
}
