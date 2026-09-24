package com.mistaboom.essence_ascendance.visual;

/** Canonical interface roles, independent of Essence categories and equipment tiers.
 * Colors are 24-bit RGB for text styles; use {@link #argb(int)} for GUI drawing.
 * Skill-specific art direction remains separate.
 */
public final class AscendanceUiPalette {
    public static final int SURFACE = 0x20242B;
    public static final int RAISED_SURFACE = 0x2D333D;
    public static final int PRIMARY_TEXT = 0xE9E9EF;
    public static final int MUTED_TEXT = 0xAEB4C0;
    public static final int BORDER = 0x858F9A;
    public static final int DIVIDER = 0x535B68;
    public static final int SUCCESS = 0x86D98C;
    public static final int WARNING = 0xE4C36A;
    public static final int ERROR = 0xE27777;
    public static final int MUTED_ERROR = 0x6E5151;
    public static final int INFORMATION = 0x79C8D8;
    public static final int INTERACTIVE = 0xD0ADEB;
    public static final int SPECIAL = 0xC8B0C5;

    private AscendanceUiPalette() { }

    public static int argb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** A raised control surface with a subtle 1/8 accent tint. */
    public static int controlHoverArgb(int accentRgb) {
        int red = (((RAISED_SURFACE >> 16) & 255) * 7 + ((accentRgb >> 16) & 255)) / 8;
        int green = (((RAISED_SURFACE >> 8) & 255) * 7 + ((accentRgb >> 8) & 255)) / 8;
        int blue = ((RAISED_SURFACE & 255) * 7 + (accentRgb & 255)) / 8;
        return argb((red << 16) | (green << 8) | blue);
    }
}
