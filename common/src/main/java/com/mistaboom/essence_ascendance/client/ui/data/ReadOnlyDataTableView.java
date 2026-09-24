package com.mistaboom.essence_ascendance.client.ui.data;

import com.mistaboom.essence_ascendance.client.ui.StyledTextLayout;
import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenControls;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenScroll;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenViewport;
import com.mistaboom.essence_ascendance.visual.AscendanceUiPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToIntFunction;

/** Shared focus, selection, hit testing, clipping, scrolling and visible-row rendering for read-only tables. */
public final class ReadOnlyDataTableView<R> {
    public static final int HEADER_HEIGHT = 18;
    public static final int ROW_HEIGHT = 17;

    private final ReadOnlyDataTable<R> table;
    private final FullscreenScroll scroll = new FullscreenScroll();
    private String selectedKey;
    private String sortColumn;
    private ReadOnlyDataTable.SortDirection sortDirection = ReadOnlyDataTable.SortDirection.ASCENDING;
    private UiBounds bounds = new UiBounds(0, 0, 0, 0);
    private List<ReadOnlyDataTable.ColumnWidth> widths = List.of();
    private List<ReadOnlyDataTable.Row<R>> ordered;
    private ToIntFunction<Component> measureText;

    public ReadOnlyDataTableView(ReadOnlyDataTable<R> table) {
        this.table = Objects.requireNonNull(table, "Table definition");
    }

    public String selectedKey() { return selectedKey; }
    public String sortColumn() { return sortColumn; }
    public ReadOnlyDataTable.SortDirection sortDirection() { return sortDirection; }
    public int scrollOffset() { return scroll.offset(); }
    public boolean hasOverflow() { return scroll.maximumOffset() > 0; }
    public List<ReadOnlyDataTable.Row<R>> orderedRows() {
        if (ordered == null) ordered = table.ordered(sortColumn, sortDirection);
        return ordered;
    }

    public void restore(String selectedKey, int scrollOffset, String sortColumn,
                        ReadOnlyDataTable.SortDirection direction) {
        this.selectedKey = table.rows().stream().anyMatch(row -> row.key().equals(selectedKey)) ? selectedKey : null;
        this.sortColumn = table.column(sortColumn) != null && table.column(sortColumn).sortable() ? sortColumn : null;
        this.sortDirection = direction == null ? ReadOnlyDataTable.SortDirection.ASCENDING : direction;
        ordered = null;
        scroll.restore(scrollOffset);
    }

    public void prepare(Font font, UiBounds bounds) {
        prepare(bounds, font::width);
    }

    public void prepare(UiBounds bounds, ToIntFunction<Component> measureText) {
        this.bounds = bounds;
        this.measureText = Objects.requireNonNull(measureText);
        widths = table.measure(Math.max(0, bounds.width() - 2), 1, measureText);
        int visibleRows = Math.max(0, (bounds.height() - HEADER_HEIGHT - 2) / ROW_HEIGHT);
        scroll.configure(table.rows().size(), visibleRows);
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        FullscreenControls.panel(graphics, bounds, AscendanceUiPalette.argb(AscendanceUiPalette.SURFACE),
                AscendanceUiPalette.argb(AscendanceUiPalette.BORDER));
        UiBounds header = new UiBounds(bounds.x() + 1, bounds.y() + 1, Math.max(0, bounds.width() - 2),
                Math.min(HEADER_HEIGHT, Math.max(0, bounds.height() - 2)));
        graphics.fill(header.x(), header.y(), header.right(), header.bottom(),
                AscendanceUiPalette.argb(AscendanceUiPalette.RAISED_SURFACE));
        for (int index = 0; index < widths.size(); index++) {
            ReadOnlyDataTable.ColumnWidth measured = widths.get(index);
            ReadOnlyDataTable.Column<R, ?> column = table.columns().get(index);
            UiBounds cell = new UiBounds(bounds.x() + 1 + measured.x(), header.y(), measured.width(), header.height());
            Component label = column.header();
            if (column.id().equals(sortColumn)) {
                label = label.copy().append(Component.translatable(sortDirection == ReadOnlyDataTable.SortDirection.ASCENDING
                        ? "gui.essence_ascendance.table.sort.ascending"
                        : "gui.essence_ascendance.table.sort.descending"));
            }
            int color = column.sortable() && cell.contains(mouseX, mouseY)
                    ? AscendanceUiPalette.argb(AscendanceUiPalette.INTERACTIVE)
                    : AscendanceUiPalette.argb(AscendanceUiPalette.PRIMARY_TEXT);
            drawCell(graphics, font, cell, label, column.alignment(), color);
        }

        UiBounds body = new UiBounds(bounds.x() + 1, header.bottom(), Math.max(0, bounds.width() - 2),
                Math.max(0, bounds.bottom() - 1 - header.bottom()));
        List<ReadOnlyDataTable.Row<R>> rows = orderedRows();
        int first = Math.min(scroll.offset(), rows.size());
        int count = Math.min(rows.size() - first, Math.max(0, body.height() / ROW_HEIGHT + 1));
        List<ReadOnlyDataTable.Row<R>> visibleRows = rows.subList(first, first + count);
        FullscreenViewport.withClip(graphics, body, () -> {
            for (int visible = 0; visible < visibleRows.size(); visible++) {
                ReadOnlyDataTable.Row<R> row = visibleRows.get(visible);
                int y = body.y() + visible * ROW_HEIGHT;
                UiBounds rowBounds = new UiBounds(body.x(), y, body.width(), ROW_HEIGHT);
                boolean selected = row.key().equals(selectedKey);
                boolean hovered = rowBounds.contains(mouseX, mouseY);
                if (selected || hovered) {
                    graphics.fill(rowBounds.x(), rowBounds.y(), rowBounds.right(), rowBounds.bottom(),
                            selected ? AscendanceUiPalette.controlHoverArgb(AscendanceUiPalette.INTERACTIVE) : 0x55353B46);
                }
                for (int columnIndex = 0; columnIndex < widths.size(); columnIndex++) {
                    var measured = widths.get(columnIndex);
                    var column = table.columns().get(columnIndex);
                    UiBounds cell = new UiBounds(bounds.x() + 1 + measured.x(), y, measured.width(), ROW_HEIGHT);
                    drawCell(graphics, font, cell, column.display(row.value()), column.alignment(),
                            AscendanceUiPalette.argb(AscendanceUiPalette.PRIMARY_TEXT));
                }
                graphics.fill(body.x(), rowBounds.bottom() - 1, body.right(), rowBounds.bottom(),
                        0x66535B68);
            }
        });
        renderScrollbar(graphics, body, rows.size());
    }

    public boolean click(double x, double y, int button) {
        if (!bounds.contains(x, y)) return false;
        if (button != 0 || !bounds.inset(1).contains(x, y)) return true;
        if (y < bounds.y() + 1 + HEADER_HEIGHT) {
            for (int index = 0; index < widths.size(); index++) {
                var measured = widths.get(index);
                var column = table.columns().get(index);
                UiBounds cell = new UiBounds(bounds.x() + 1 + measured.x(), bounds.y() + 1,
                        measured.width(), HEADER_HEIGHT);
                if (cell.contains(x, y) && column.sortable()) {
                    if (column.id().equals(sortColumn)) {
                        sortDirection = sortDirection == ReadOnlyDataTable.SortDirection.ASCENDING
                                ? ReadOnlyDataTable.SortDirection.DESCENDING
                                : ReadOnlyDataTable.SortDirection.ASCENDING;
                    } else {
                        sortColumn = column.id();
                        sortDirection = ReadOnlyDataTable.SortDirection.ASCENDING;
                    }
                    ordered = null;
                    scroll.key(268, 1, 1);
                    return true;
                }
            }
            return true;
        }
        int rowIndex = scroll.offset() + (int) ((y - bounds.y() - 1 - HEADER_HEIGHT) / ROW_HEIGHT);
        List<ReadOnlyDataTable.Row<R>> rows = orderedRows();
        if (rowIndex >= 0 && rowIndex < rows.size()) selectedKey = rows.get(rowIndex).key();
        return true;
    }

    public boolean scroll(double amount) {
        int previous = scroll.offset();
        scroll.wheel(amount, 3);
        return scroll.offset() != previous;
    }

    public boolean key(int key) {
        if (key == 264 || key == 265) {
            List<ReadOnlyDataTable.Row<R>> rows = orderedRows();
            if (rows.isEmpty()) return true;
            int index = -1;
            for (int i = 0; i < rows.size(); i++) if (rows.get(i).key().equals(selectedKey)) index = i;
            index = index < 0 ? 0 : Math.max(0, Math.min(rows.size() - 1, index + (key == 264 ? 1 : -1)));
            selectedKey = rows.get(index).key();
            scroll.ensureVisible(index, 1);
            return true;
        }
        return scroll.key(key, 1, Math.max(1, (bounds.height() - HEADER_HEIGHT) / ROW_HEIGHT));
    }

    /** Full styled value for compressed cells; render its tooltip outside enclosing clips. */
    public Optional<Component> overflowTextAt(double x, double y) {
        if (measureText == null || !bounds.inset(1).contains(x, y)) return Optional.empty();
        boolean header = y < bounds.y() + 1 + HEADER_HEIGHT;
        int rowIndex = scroll.offset() + (int) ((y - bounds.y() - 1 - HEADER_HEIGHT) / ROW_HEIGHT);
        List<ReadOnlyDataTable.Row<R>> rows = orderedRows();
        if (!header && (rowIndex < 0 || rowIndex >= rows.size())) return Optional.empty();
        for (int index = 0; index < widths.size(); index++) {
            var measured = widths.get(index);
            int left = bounds.x() + 1 + measured.x();
            if (x < left || x >= left + measured.width()) continue;
            var column = table.columns().get(index);
            Component value = header ? column.header() : column.display(rows.get(rowIndex).value());
            return measureText.applyAsInt(value) > Math.max(0, measured.width() - 8)
                    ? Optional.of(value) : Optional.empty();
        }
        return Optional.empty();
    }

    private static void drawCell(GuiGraphics graphics, Font font, UiBounds bounds, Component value,
                                 ReadOnlyDataTable.Alignment alignment, int color) {
        int available = Math.max(0, bounds.width() - 8);
        var text = StyledTextLayout.fit(font, value, available);
        int x = alignment == ReadOnlyDataTable.Alignment.RIGHT
                ? bounds.right() - 4 - font.width(text) : bounds.x() + 4;
        FullscreenViewport.withClip(graphics, bounds, () -> graphics.drawString(font, text, x,
                bounds.y() + Math.max(1, (bounds.height() - font.lineHeight) / 2), color, false));
    }

    private void renderScrollbar(GuiGraphics graphics, UiBounds body, int rowCount) {
        if (scroll.maximumOffset() <= 0 || body.height() <= 0) return;
        int visible = Math.max(1, body.height() / ROW_HEIGHT);
        int thumbHeight = Math.max(8, body.height() * visible / Math.max(visible, rowCount));
        int travel = Math.max(0, body.height() - thumbHeight);
        int y = body.y() + travel * scroll.offset() / Math.max(1, scroll.maximumOffset());
        graphics.fill(body.right() - 2, body.y(), body.right(), body.bottom(), 0x88535B68);
        graphics.fill(body.right() - 2, y, body.right(), y + thumbHeight,
                AscendanceUiPalette.argb(AscendanceUiPalette.BORDER));
    }
}
