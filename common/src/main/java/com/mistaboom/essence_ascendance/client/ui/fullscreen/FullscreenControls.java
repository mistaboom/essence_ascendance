package com.mistaboom.essence_ascendance.client.ui.fullscreen;

import com.mistaboom.essence_ascendance.client.ui.StyledTextLayout;
import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.visual.AscendanceUiPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Palette-backed common fullscreen chrome; labels retain their Component styles. */
public final class FullscreenControls {
    public enum Style { MODE, TAB, ACTION, PRIMARY, ARROW, LINK }

    // The shared TAB recipe retains the established translucent tab skin. Keep
    // these compatibility surfaces/baseline separate from raised action controls.
    private static final int TAB_SURFACE = 0xCC1C1C20;
    private static final int TAB_SELECTED_SURFACE = 0xCC29292D;
    private static final int TAB_TEXT_OFFSET = 6;

    private FullscreenControls() { }

    public static void panel(GuiGraphics graphics, UiBounds bounds, int surface, int border) {
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), surface);
        outline(graphics, bounds, border);
    }

    public static void modalBackdrop(GuiGraphics graphics, UiBounds screen, UiBounds panel) {
        graphics.fill(screen.x(), screen.y(), screen.right(), screen.bottom(), 0xB0000000);
        panel(graphics, panel, AscendanceUiPalette.argb(AscendanceUiPalette.SURFACE),
                AscendanceUiPalette.argb(AscendanceUiPalette.BORDER));
    }

    public static void button(GuiGraphics graphics, Font font, UiBounds bounds, Component label,
                              boolean enabled, boolean selected, boolean hovered, boolean focused,
                              Style style, int accent) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        int opaqueAccent = AscendanceUiPalette.argb(accent);
        boolean active = enabled && (hovered || focused);
        int surface;
        int border;
        int text = AscendanceUiPalette.argb(enabled ? AscendanceUiPalette.PRIMARY_TEXT : AscendanceUiPalette.MUTED_TEXT);
        switch (style) {
            case MODE -> {
                surface = !enabled ? opacity(AscendanceUiPalette.SURFACE, 0x33)
                        : selected ? AscendanceUiPalette.argb(AscendanceUiPalette.RAISED_SURFACE)
                        : active ? AscendanceUiPalette.controlHoverArgb(accent)
                        : opacity(AscendanceUiPalette.RAISED_SURFACE, 0x44);
                border = !enabled ? opacity(AscendanceUiPalette.DIVIDER, 0x55)
                        : selected || active ? opaqueAccent : opacity(AscendanceUiPalette.BORDER, 0x88);
                text = AscendanceUiPalette.argb(selected && enabled
                        ? AscendanceUiPalette.PRIMARY_TEXT : AscendanceUiPalette.MUTED_TEXT);
            }
            case TAB -> {
                surface = selected ? TAB_SELECTED_SURFACE : TAB_SURFACE;
                border = !enabled ? AscendanceUiPalette.argb(AscendanceUiPalette.DIVIDER)
                        : selected || focused ? opaqueAccent : opacity(accent, 120);
                text = enabled ? opaqueAccent : AscendanceUiPalette.argb(AscendanceUiPalette.MUTED_TEXT);
            }
            case ACTION -> {
                surface = !enabled ? AscendanceUiPalette.argb(AscendanceUiPalette.SURFACE)
                        : active ? AscendanceUiPalette.controlHoverArgb(accent)
                        : AscendanceUiPalette.argb(AscendanceUiPalette.RAISED_SURFACE);
                border = !enabled ? AscendanceUiPalette.argb(AscendanceUiPalette.DIVIDER)
                        : active || selected ? opaqueAccent : AscendanceUiPalette.argb(AscendanceUiPalette.BORDER);
            }
            case PRIMARY, ARROW -> {
                surface = !enabled ? opacity(AscendanceUiPalette.SURFACE, 0x88)
                        : active ? AscendanceUiPalette.controlHoverArgb(accent)
                        : AscendanceUiPalette.argb(AscendanceUiPalette.RAISED_SURFACE);
                border = enabled ? opaqueAccent : AscendanceUiPalette.argb(AscendanceUiPalette.DIVIDER);
            }
            case LINK -> {
                surface = 0;
                border = enabled ? opaqueAccent : AscendanceUiPalette.argb(AscendanceUiPalette.DIVIDER);
            }
            default -> throw new IllegalStateException("Unhandled control style: " + style);
        }
        if (style != Style.LINK) graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), surface);
        outline(graphics, bounds, border);
        if (enabled && selected && style == Style.TAB && bounds.width() > 4 && bounds.height() > 4) {
            graphics.fill(bounds.x() + 2, bounds.bottom() - 3, bounds.right() - 2, bounds.bottom() - 1, opaqueAccent);
        }
        if (enabled && focused && bounds.width() > 4 && bounds.height() > 4) {
            outline(graphics, bounds.inset(2), opacity(AscendanceUiPalette.PRIMARY_TEXT, 0xCC));
        }
        int padding = style == Style.TAB || style == Style.LINK ? 10 : style == Style.MODE ? 8 : 6;
        FormattedCharSequence fitted = StyledTextLayout.fit(font, label, Math.max(0, bounds.width() - padding));
        int textY = bounds.y() + (style == Style.TAB ? TAB_TEXT_OFFSET : style == Style.LINK ? 5
                : Math.max(2, (bounds.height() - font.lineHeight) / 2));
        int textColor = text;
        FullscreenViewport.withClip(graphics, bounds, () -> {
            if (style == Style.LINK) graphics.drawString(font, fitted, bounds.x() + 5, textY, textColor, false);
            else graphics.drawCenteredString(font, fitted, bounds.x() + bounds.width() / 2, textY, textColor);
        });
    }

    public static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        outline(graphics, new UiBounds(x, y, Math.max(0, width), Math.max(0, height)), color);
    }

    public static void outline(GuiGraphics graphics, UiBounds bounds, int color) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1, color);
        graphics.fill(bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), color);
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + 1, bounds.bottom(), color);
        graphics.fill(bounds.right() - 1, bounds.y(), bounds.right(), bounds.bottom(), color);
    }

    private static int opacity(int rgb, int alpha) {
        return alpha << 24 | rgb & 0xFFFFFF;
    }
}
