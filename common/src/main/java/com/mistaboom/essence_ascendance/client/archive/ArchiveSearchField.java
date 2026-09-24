package com.mistaboom.essence_ascendance.client.archive;

import com.mistaboom.essence_ascendance.client.ui.StyledTextLayout;
import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenComposition;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenControls;
import com.mistaboom.essence_ascendance.visual.AscendanceUiPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;

/** Focusable Archive query field owned by the shared fullscreen region router. */
public final class ArchiveSearchField implements FullscreenComposition.Input {
    private final Consumer<String> changed;
    private final Component placeholder;
    private String query;
    private int cursor;
    private boolean focused;
    private UiBounds bounds = new UiBounds(0, 0, 0, 0);

    public ArchiveSearchField(String initial, Component placeholder, Consumer<String> changed) {
        query = initial == null ? "" : initial;
        cursor = query.length();
        this.placeholder = Objects.requireNonNull(placeholder);
        this.changed = Objects.requireNonNull(changed);
    }

    public String query() { return query; }
    public void query(String restored) {
        query = restored == null ? "" : restored;
        cursor = query.length();
    }
    public void bounds(UiBounds bounds) { this.bounds = bounds; }

    public void render(GuiGraphics graphics, Font font) {
        FullscreenControls.panel(graphics, bounds, AscendanceUiPalette.argb(AscendanceUiPalette.RAISED_SURFACE),
                AscendanceUiPalette.argb(focused ? AscendanceUiPalette.INTERACTIVE : AscendanceUiPalette.BORDER));
        Component display = query.isEmpty() ? placeholder : Component.literal(query);
        int color = AscendanceUiPalette.argb(query.isEmpty()
                ? AscendanceUiPalette.MUTED_TEXT : AscendanceUiPalette.PRIMARY_TEXT);
        graphics.drawString(font, StyledTextLayout.fit(font, display, Math.max(0, bounds.width() - 12)),
                bounds.x() + 6, bounds.y() + Math.max(2, (bounds.height() - font.lineHeight) / 2), color, false);
        if (focused && (System.currentTimeMillis() / 500L & 1L) == 0L) {
            int prefix = Math.min(bounds.width() - 12, font.width(query.substring(0, cursor)));
            int x = bounds.x() + 6 + Math.max(0, prefix);
            graphics.fill(x, bounds.y() + 5, x + 1, bounds.bottom() - 5,
                    AscendanceUiPalette.argb(AscendanceUiPalette.PRIMARY_TEXT));
        }
    }

    @Override public boolean click(double x, double y, int button) {
        if (!bounds.contains(x, y)) return false;
        if (button == 0) cursor = query.length();
        return true;
    }

    @Override public boolean key(int key, int scan, int modifiers) {
        switch (key) {
            case 259 -> { if (cursor > 0) replace(cursor - 1, cursor, ""); return true; }
            case 261 -> { if (cursor < query.length()) replace(cursor, cursor + 1, ""); return true; }
            case 263 -> { cursor = Math.max(0, cursor - 1); return true; }
            case 262 -> { cursor = Math.min(query.length(), cursor + 1); return true; }
            case 268 -> { cursor = 0; return true; }
            case 269 -> { cursor = query.length(); return true; }
            default -> { return false; }
        }
    }

    @Override public boolean character(char character, int modifiers) {
        if (character < 32 || character == 127) return false;
        replace(cursor, cursor, Character.toString(character));
        return true;
    }

    @Override public void focused(boolean focused) { this.focused = focused; }

    private void replace(int start, int end, String value) {
        query = query.substring(0, start) + value + query.substring(end);
        cursor = start + value.length();
        changed.accept(query);
    }
}
