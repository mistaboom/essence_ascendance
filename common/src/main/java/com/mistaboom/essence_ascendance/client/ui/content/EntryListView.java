package com.mistaboom.essence_ascendance.client.ui.content;

import com.mistaboom.essence_ascendance.client.ui.StyledTextLayout;
import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenComposition;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenControls;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenScroll;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenViewport;
import com.mistaboom.essence_ascendance.visual.AscendanceUiPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Reusable stable-key list with selection, clipping, scrolling and keyboard navigation. */
public final class EntryListView<T> implements FullscreenComposition.Input {
    public record Entry<T>(String key, Component label, Component summary, T value) {
        public Entry {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("Entry-list key cannot be blank");
            Objects.requireNonNull(label); Objects.requireNonNull(summary); Objects.requireNonNull(value);
        }
    }

    private static final int ROW_HEIGHT = 30;
    private final FullscreenScroll scroll = new FullscreenScroll();
    private final Consumer<T> choose;
    private final Consumer<T> activate;
    private List<Entry<T>> entries = List.of();
    private String selectedKey;
    private UiBounds bounds = new UiBounds(0, 0, 0, 0);

    /** Selection is the action for navigation lists such as Guide and Reference. */
    public EntryListView(Consumer<T> choose) { this(choose, null); }

    /** Separate activation lets result lists move selection without opening on arrow keys. */
    public EntryListView(Consumer<T> choose, Consumer<T> activate) {
        this.choose = Objects.requireNonNull(choose);
        this.activate = activate;
    }

    public String selectedKey() { return selectedKey; }
    public int scrollOffset() { return scroll.offset(); }
    public void restore(String selectedKey, int offset) { this.selectedKey = selectedKey; scroll.restore(offset); }

    public void prepare(UiBounds bounds, List<Entry<T>> entries) {
        HashSet<String> keys = new HashSet<>();
        for (Entry<T> entry : entries) if (!keys.add(entry.key())) throw new IllegalArgumentException("Duplicate list entry " + entry.key());
        this.bounds = bounds;
        this.entries = List.copyOf(entries);
        if (this.entries.stream().noneMatch(entry -> entry.key().equals(selectedKey))) {
            selectedKey = this.entries.isEmpty() ? null : this.entries.getFirst().key();
        }
        scroll.configure(this.entries.size(), Math.max(0, (bounds.height() - 2) / ROW_HEIGHT));
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        FullscreenControls.panel(graphics, bounds, AscendanceUiPalette.argb(AscendanceUiPalette.SURFACE),
                AscendanceUiPalette.argb(AscendanceUiPalette.BORDER));
        UiBounds clip = bounds.inset(1);
        int first = Math.min(entries.size(), scroll.offset());
        int visible = Math.min(entries.size() - first, Math.max(0, clip.height() / ROW_HEIGHT + 1));
        FullscreenViewport.withClip(graphics, clip, () -> {
            for (int row = 0; row < visible; row++) {
                Entry<T> entry = entries.get(first + row);
                UiBounds rowBounds = new UiBounds(clip.x(), clip.y() + row * ROW_HEIGHT, clip.width(), ROW_HEIGHT);
                boolean selected = entry.key().equals(selectedKey);
                if (selected || rowBounds.contains(mouseX, mouseY)) {
                    graphics.fill(rowBounds.x(), rowBounds.y(), rowBounds.right(), rowBounds.bottom(), selected
                            ? AscendanceUiPalette.controlHoverArgb(AscendanceUiPalette.INTERACTIVE) : 0x55353B46);
                }
                graphics.drawString(font, StyledTextLayout.fit(font, entry.label(), Math.max(0, rowBounds.width() - 10)),
                        rowBounds.x() + 5, rowBounds.y() + 4,
                        AscendanceUiPalette.argb(selected ? AscendanceUiPalette.INTERACTIVE : AscendanceUiPalette.PRIMARY_TEXT), false);
                graphics.drawString(font, StyledTextLayout.fit(font, entry.summary(), Math.max(0, rowBounds.width() - 10)),
                        rowBounds.x() + 5, rowBounds.y() + 17,
                        AscendanceUiPalette.argb(AscendanceUiPalette.MUTED_TEXT), false);
                graphics.fill(rowBounds.x(), rowBounds.bottom() - 1, rowBounds.right(), rowBounds.bottom(), 0x66535B68);
            }
        });
        if (scroll.maximumOffset() > 0) {
            int height = Math.max(8, clip.height() * Math.max(1, clip.height() / ROW_HEIGHT) / entries.size());
            int travel = Math.max(0, clip.height() - height);
            int y = clip.y() + travel * scroll.offset() / Math.max(1, scroll.maximumOffset());
            graphics.fill(clip.right() - 2, clip.y(), clip.right(), clip.bottom(), 0x88535B68);
            graphics.fill(clip.right() - 2, y, clip.right(), y + height,
                    AscendanceUiPalette.argb(AscendanceUiPalette.BORDER));
        }
    }

    @Override public boolean click(double x, double y, int button) {
        if (!bounds.contains(x, y)) return false;
        if (button != 0 || !bounds.inset(1).contains(x, y)) return true;
        int row = scroll.offset() + (int) ((y - bounds.y() - 1) / ROW_HEIGHT);
        if (row >= 0 && row < entries.size()) {
            Entry<T> entry = entries.get(row);
            select(entry, true);
            if (activate != null) activate.accept(entry.value());
        }
        return true;
    }

    @Override public boolean scroll(double x, double y, double dx, double dy) {
        scroll.wheel(dy, 2);
        return true;
    }

    @Override public boolean key(int key, int scan, int modifiers) {
        if (key == 257 || key == 335 || key == 32) {
            entries.stream().filter(entry -> entry.key().equals(selectedKey)).findFirst()
                    .ifPresent(entry -> (activate == null ? choose : activate).accept(entry.value()));
            return true;
        }
        if (key == 264 || key == 265) {
            if (entries.isEmpty()) return true;
            int selected = 0;
            for (int index = 0; index < entries.size(); index++) if (entries.get(index).key().equals(selectedKey)) selected = index;
            selected = Math.max(0, Math.min(entries.size() - 1, selected + (key == 264 ? 1 : -1)));
            scroll.ensureVisible(selected, 1);
            select(entries.get(selected), true);
            return true;
        }
        return scroll.key(key, 1, Math.max(1, bounds.height() / ROW_HEIGHT));
    }

    private void select(Entry<T> entry, boolean notify) {
        selectedKey = entry.key();
        if (notify) choose.accept(entry.value());
    }
}
