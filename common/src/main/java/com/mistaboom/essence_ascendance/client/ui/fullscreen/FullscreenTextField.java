package com.mistaboom.essence_ascendance.client.ui.fullscreen;

import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.visual.AscendanceUiPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Shared single-line editor with selection, clipboard, Unicode motion and horizontal reveal. */
public class FullscreenTextField implements FullscreenComposition.Input {
    private final Consumer<String> changed;
    private final Supplier<String> clipboardRead;
    private final Consumer<String> clipboardWrite;
    private final Component placeholder;
    private String query;
    private int cursor, anchor, start;
    private boolean focused, dragging;
    private Font font;
    private UiBounds bounds = new UiBounds(0, 0, 0, 0);

    public FullscreenTextField(String initial, Component placeholder, Consumer<String> changed,
                              Supplier<String> clipboardRead, Consumer<String> clipboardWrite) {
        query = initial == null ? "" : initial;
        cursor = anchor = query.length();
        this.placeholder = Objects.requireNonNull(placeholder);
        this.changed = Objects.requireNonNull(changed);
        this.clipboardRead = Objects.requireNonNull(clipboardRead);
        this.clipboardWrite = Objects.requireNonNull(clipboardWrite);
    }
    public String query() { return query; }
    public int cursor() { return cursor; }
    public int visibleStart() { return start; }
    public String selection() { return query.substring(Math.min(cursor, anchor), Math.max(cursor, anchor)); }
    public void query(String restored) {
        query = restored == null ? "" : restored;
        cursor = anchor = query.length(); start = 0; reveal();
    }
    public void bounds(UiBounds bounds) { this.bounds = bounds; reveal(); }
    public void prepare(Font font) { this.font = font; reveal(); }
    private int previous(int at) { return at == 0 ? 0 : query.offsetByCodePoints(at, -1); }
    private int next(int at) { return at == query.length() ? at : query.offsetByCodePoints(at, 1); }
    private static boolean space(int cp) { return Character.isWhitespace(cp) || Character.isSpaceChar(cp); }
    private int word(int direction) {
        int at = cursor;
        if (direction < 0) {
            while (at > 0 && space(query.codePointBefore(at))) at = previous(at);
            while (at > 0 && !space(query.codePointBefore(at))) at = previous(at);
        } else {
            while (at < query.length() && !space(query.codePointAt(at))) at = next(at);
            while (at < query.length() && space(query.codePointAt(at))) at = next(at);
        }
        return at;
    }
    private void move(int destination, boolean selecting) {
        cursor = destination;
        if (!selecting) anchor = cursor;
        reveal();
    }
    private void reveal() {
        start = Math.min(start, cursor);
        if (font == null) return;
        int width = Math.max(1, bounds.width() - 14);
        while (start < cursor && font.width(query.substring(start, cursor)) > width) start = next(start);
        while (start > 0 && font.width(query.substring(previous(start), cursor)) <= width) start = previous(start);
    }
    private int at(double x) {
        if (font == null) return cursor;
        double offset = x - bounds.x() - 6;
        int position = start;
        while (position < query.length()) {
            int after = next(position);
            int left = font.width(query.substring(start, position));
            int right = font.width(query.substring(start, after));
            if (offset < (left + right) / 2.0) break;
            position = after;
        }
        return position;
    }
    public void render(GuiGraphics graphics, Font font) {
        prepare(font);
        FullscreenControls.panel(graphics, bounds, AscendanceUiPalette.argb(AscendanceUiPalette.RAISED_SURFACE),
                AscendanceUiPalette.argb(focused ? AscendanceUiPalette.INTERACTIVE : AscendanceUiPalette.BORDER));
        FullscreenViewport.withClip(graphics, bounds.inset(4), () -> {
            int x = bounds.x() + 6, y = bounds.y() + Math.max(2, (bounds.height() - font.lineHeight) / 2);
            if (focused && cursor != anchor) {
                int left = font.width(query.substring(start, Math.max(start, Math.min(cursor, anchor))));
                int right = font.width(query.substring(start, Math.max(start, Math.max(cursor, anchor))));
                graphics.fill(x + left, y - 1, x + right, y + font.lineHeight + 1,
                        AscendanceUiPalette.controlHoverArgb(AscendanceUiPalette.INTERACTIVE));
            }
            graphics.drawString(font, query.isEmpty() ? placeholder : Component.literal(query.substring(start)), x, y,
                    AscendanceUiPalette.argb(query.isEmpty() ? AscendanceUiPalette.MUTED_TEXT : AscendanceUiPalette.PRIMARY_TEXT), false);
            if (focused && (System.currentTimeMillis() / 500L & 1L) == 0L) {
                int caret = x + font.width(query.substring(start, cursor));
                graphics.fill(caret, y - 1, caret + 1, y + font.lineHeight + 1, AscendanceUiPalette.argb(AscendanceUiPalette.PRIMARY_TEXT));
            }
        });
    }
    @Override public boolean click(double x, double y, int button) {
        if (!bounds.contains(x, y)) return false;
        if (button == 0) { move(at(x), false); dragging = true; }
        return true;
    }
    @Override public boolean drag(double x, double y, int button, double dx, double dy) {
        if (!dragging || button != 0) return false;
        move(x < bounds.x() + 6 ? previous(cursor) : x >= bounds.right() - 6 ? next(cursor) : at(x), true);
        return true;
    }
    @Override public boolean release(double x, double y, int button) { boolean handled = dragging; dragging = false; return handled; }
    @Override public void cancel() { dragging = false; }
    @Override public void focused(boolean value) { focused = value; if (!value) dragging = false; }
    @Override public boolean key(int key, int scan, int modifiers) {
        boolean shift = (modifiers & 1) != 0, control = (modifiers & 2) != 0;
        if (control) switch (key) {
            case 65 -> { anchor = 0; cursor = query.length(); reveal(); return true; }
            case 67 -> { clipboardWrite.accept(selection()); return true; }
            case 88 -> { clipboardWrite.accept(selection()); insert(""); return true; }
            case 86 -> { insert(clipboardRead.get()); return true; }
        }
        switch (key) {
            case 259 -> { if (anchor == cursor) anchor = control ? word(-1) : previous(cursor); insert(""); return true; }
            case 261 -> { if (anchor == cursor) anchor = control ? word(1) : next(cursor); insert(""); return true; }
            case 263 -> { move(control ? word(-1) : !shift && anchor != cursor ? Math.min(anchor, cursor) : previous(cursor), shift); return true; }
            case 262 -> { move(control ? word(1) : !shift && anchor != cursor ? Math.max(anchor, cursor) : next(cursor), shift); return true; }
            case 268 -> { move(0, shift); return true; }
            case 269 -> { move(query.length(), shift); return true; }
            default -> { return false; }
        }
    }
    @Override public boolean character(char character, int modifiers) {
        if ((modifiers & 2) != 0 || Character.isISOControl(character) || character == '§') return false;
        insert(Character.toString(character)); return true;
    }
    public void insert(String text) {
        StringBuilder clean = new StringBuilder();
        if (text != null) text.codePoints().forEach(cp -> {
            if (space(cp)) clean.append(' ');
            else if (!Character.isISOControl(cp) && cp != '§') clean.appendCodePoint(cp);
        });
        int left = Math.min(anchor, cursor), right = Math.max(anchor, cursor);
        String updated = query.substring(0, left) + clean + query.substring(right);
        cursor = anchor = left + clean.length();
        boolean modified = !query.equals(updated);
        query = updated; reveal();
        if (modified) changed.accept(query);
    }
}
