package com.mistaboom.essence_ascendance.client.ui.data;

import com.mistaboom.essence_ascendance.client.ui.StyledTextLayout;
import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.client.ui.content.ItemIllustrationRenderer;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenControls;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenScroll;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenViewport;
import com.mistaboom.essence_ascendance.visual.AscendanceUiPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToIntFunction;
import java.util.function.Consumer;

/** Shared focus, selection, hit testing, clipping, scrolling and visible-row rendering for read-only tables. */
public final class ReadOnlyDataTableView<R> {
    public static final int HEADER_HEIGHT = 18;
    public static final int ROW_HEIGHT = 17;

    private final ReadOnlyDataTable<R> table;
    private final Consumer<String> navigate;
    private final FullscreenScroll scroll = new FullscreenScroll();
    private String selectedKey;
    private String sortColumn;
    private ReadOnlyDataTable.SortDirection sortDirection = ReadOnlyDataTable.SortDirection.ASCENDING;
    private UiBounds bounds = new UiBounds(0, 0, 0, 0);
    private List<ReadOnlyDataTable.ColumnWidth> widths = List.of();
    private List<ReadOnlyDataTable.Row<R>> ordered;
    private ToIntFunction<Component> measureText;
    private Font wrappingFont;
    private int rowHeight = ROW_HEIGHT;
    private int headerHeight = HEADER_HEIGHT;
    private boolean stacked;
    private List<Integer> fieldHeights = List.of();
    private Font measuredFont;
    private int measuredWidth = -1;
    private int sortFocus = -1;
    private String activeLink;
    private Boolean hasInlineLinks;

    public ReadOnlyDataTableView(ReadOnlyDataTable<R> table) {
        this(table, ignored -> { });
    }

    public ReadOnlyDataTableView(ReadOnlyDataTable<R> table, Consumer<String> navigate) {
        this.table = Objects.requireNonNull(table, "Table definition");
        this.navigate = Objects.requireNonNull(navigate);
    }

    public String selectedKey() { return selectedKey; }
    public String sortColumn() { return sortColumn; }
    public ReadOnlyDataTable.SortDirection sortDirection() { return sortDirection; }
    public int scrollOffset() { return scroll.offset(); }
    public int rowHeight() { return rowHeight; }
    public int headerHeight() { return headerHeight; }
    public boolean stacked() { return stacked; }
    public void activeLink(String target) { activeLink = target; }
    public void blur() { sortFocus = -1; activeLink = null; }
    public boolean hasOverflow() { return scroll.maximumOffset() > 0; }
    public List<ReadOnlyDataTable.Row<R>> orderedRows() {
        if (ordered == null) ordered = table.ordered(sortColumn, sortDirection);
        return ordered;
    }

    /** Explicit reveal is separate from restoration: Back preserves the saved viewport exactly. */
    public void revealSelection() {
        var rows = orderedRows();
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).key().equals(selectedKey)) { scroll.ensureVisible(index * rowHeight, rowHeight); return; }
        }
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
        configure(bounds, font::width, font);
    }

    public void prepare(UiBounds bounds, ToIntFunction<Component> measureText) {
        configure(bounds, measureText, null);
    }

    /** Uniform height required to show every wrapped cell in this table. */
    public int uniformRowHeight(Font font, int availableWidth) {
        measureLayout(font, Math.max(0, availableWidth - 2));
        return rowHeight;
    }

    private void measureLayout(Font font, int width) {
        if (font == measuredFont && width == measuredWidth) return;
        measuredFont = font;
        measuredWidth = width;
        stacked = table.minimumContentWidth(font::width) > width;
        widths = table.measureContent(width, font::width);
        if (!stacked) {
            headerHeight = HEADER_HEIGHT;
            for (int index = 0; index < widths.size(); index++)
                headerHeight = Math.max(headerHeight, font.split(measuredHeader(table.columns().get(index)),
                        Math.max(1, widths.get(index).width() - 8)).size() * (font.lineHeight + 1) + 8);
            rowHeight = measureUniformRowHeight(font, widths);
            fieldHeights = List.of();
            return;
        }
        headerHeight = 0;
        java.util.ArrayList<Integer> fields = new java.util.ArrayList<>();
        int available = Math.max(1, width - 8);
        rowHeight = 4;
        for (var column : table.columns()) {
            int labelHeight = Math.max(1, font.split(measuredHeader(column), available).size()) * (font.lineHeight + 1);
            int valueHeight = 0;
            for (var row : table.rows()) {
                if (column.isItem()) valueHeight = Math.max(valueHeight, 32
                        + font.split(column.item(row.value()).caption(), available).size() * (font.lineHeight + 1));
                else valueHeight = Math.max(valueHeight, Math.max(1, font.split(column.display(row.value()), available).size())
                        * (font.lineHeight + 1));
            }
            int height = labelHeight + valueHeight + 8;
            fields.add(height); rowHeight += height;
        }
        fieldHeights = List.copyOf(fields);
    }

    private void configure(UiBounds bounds, ToIntFunction<Component> measureText, Font wrappingFont) {
        this.bounds = bounds;
        this.measureText = Objects.requireNonNull(measureText);
        this.wrappingFont = wrappingFont;
        if (wrappingFont == null) {
            measuredFont = null; measuredWidth = -1;
            widths = table.measure(Math.max(0, bounds.width() - 2), 1, measureText);
            rowHeight = ROW_HEIGHT; headerHeight = HEADER_HEIGHT; stacked = false;
        } else measureLayout(wrappingFont, Math.max(0, bounds.width() - 2));
        scroll.configure(table.rows().size() * rowHeight, Math.max(0, bounds.height() - headerHeight - 2));
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        FullscreenControls.panel(graphics, bounds, AscendanceUiPalette.argb(AscendanceUiPalette.SURFACE),
                AscendanceUiPalette.argb(AscendanceUiPalette.BORDER));
        UiBounds header = new UiBounds(bounds.x() + 1, bounds.y() + 1, Math.max(0, bounds.width() - 2),
                Math.min(headerHeight, Math.max(0, bounds.height() - 2)));
        graphics.fill(header.x(), header.y(), header.right(), header.bottom(),
                AscendanceUiPalette.argb(AscendanceUiPalette.RAISED_SURFACE));
        for (int index = 0; !stacked && index < widths.size(); index++) {
            ReadOnlyDataTable.ColumnWidth measured = widths.get(index);
            ReadOnlyDataTable.Column<R, ?> column = table.columns().get(index);
            UiBounds cell = new UiBounds(bounds.x() + 1 + measured.x(), header.y(), measured.width(), header.height());
            if (sortFocus == index) graphics.fill(cell.x(), cell.y(), cell.right(), cell.bottom(),
                    AscendanceUiPalette.controlHoverArgb(AscendanceUiPalette.INTERACTIVE));
            Component label = column.header();
            if (column.id().equals(sortColumn)) {
                label = label.copy().append(Component.translatable(sortDirection == ReadOnlyDataTable.SortDirection.ASCENDING
                        ? "gui.essence_ascendance.table.sort.ascending"
                        : "gui.essence_ascendance.table.sort.descending"));
            }
            int color = column.sortable() && cell.contains(mouseX, mouseY)
                    ? AscendanceUiPalette.argb(AscendanceUiPalette.INTERACTIVE)
                    : AscendanceUiPalette.argb(AscendanceUiPalette.PRIMARY_TEXT);
            drawWrappedCell(graphics, font, cell, label, column.alignment(), color);
        }

        UiBounds body = new UiBounds(bounds.x() + 1, header.bottom(), Math.max(0, bounds.width() - 2),
                Math.max(0, bounds.bottom() - 1 - header.bottom()));
        List<ReadOnlyDataTable.Row<R>> rows = orderedRows();
        int first = Math.min(scroll.offset() / rowHeight, rows.size());
        int count = Math.min(rows.size() - first, Math.max(0, body.height() / rowHeight + 2));
        List<ReadOnlyDataTable.Row<R>> visibleRows = rows.subList(first, first + count);
        List<StyledTextLayout.LinkRegion> inlineLinks = textLinks();
        FullscreenViewport.withClip(graphics, body, () -> {
            for (int visible = 0; visible < visibleRows.size(); visible++) {
                ReadOnlyDataTable.Row<R> row = visibleRows.get(visible);
                int y = body.y() + visible * rowHeight - scroll.offset() % rowHeight;
                UiBounds rowBounds = new UiBounds(body.x(), y, body.width(), rowHeight);
                boolean selected = row.key().equals(selectedKey);
                boolean hovered = rowBounds.contains(mouseX, mouseY);
                if (selected || hovered) {
                    graphics.fill(rowBounds.x(), rowBounds.y(), rowBounds.right(), rowBounds.bottom(),
                            selected ? AscendanceUiPalette.controlHoverArgb(AscendanceUiPalette.INTERACTIVE) : 0x55353B46);
                }
                for (var link : inlineLinks) if (rowBounds.intersects(link.bounds())
                        && (link.target().equals(activeLink) || link.bounds().contains(mouseX, mouseY)))
                    graphics.fill(link.bounds().x(), link.bounds().y(), link.bounds().right(), link.bounds().bottom(),
                            AscendanceUiPalette.controlHoverArgb(AscendanceUiPalette.INTERACTIVE));
                for (int columnIndex = 0; columnIndex < widths.size(); columnIndex++) {
                    var measured = widths.get(columnIndex);
                    var column = table.columns().get(columnIndex);
                    UiBounds cell = valueBounds(columnIndex, y, font);
                    if (stacked) {
                        int fieldY = y + 2;
                        for (int prior = 0; prior < columnIndex; prior++) fieldY += fieldHeights.get(prior);
                        UiBounds labelBounds = new UiBounds(body.x(), fieldY, body.width(), cell.y() - fieldY);
                        if (sortFocus == columnIndex) graphics.fill(labelBounds.x(), labelBounds.y(), labelBounds.right(), labelBounds.bottom(),
                                AscendanceUiPalette.controlHoverArgb(AscendanceUiPalette.INTERACTIVE));
                        drawWrappedCell(graphics, font, labelBounds, headerLabel(column), ReadOnlyDataTable.Alignment.LEFT,
                                AscendanceUiPalette.argb(AscendanceUiPalette.MUTED_TEXT));
                    }
                    if (column.isItem()) {
                        var item = column.item(row.value());
                        boolean captioned = !item.caption().getString().isEmpty();
                        int size = Math.min(28, Math.min(cell.width(), cell.height()) - 4);
                        UiBounds figure = new UiBounds(cell.x() + (cell.width() - size) / 2,
                                captioned ? cell.y() + 2 : cell.y() + (cell.height() - size) / 2,
                                Math.max(0, size), Math.max(0, size));
                        ItemIllustrationRenderer.renderStack(graphics, item.stack(), figure);
                        if (captioned) drawWrappedCell(graphics, font,
                                new UiBounds(cell.x(), figure.bottom(), cell.width(), Math.max(0, cell.bottom() - figure.bottom())),
                                item.caption(), ReadOnlyDataTable.Alignment.LEFT,
                                AscendanceUiPalette.argb(AscendanceUiPalette.PRIMARY_TEXT));
                    } else {
                        Component value = column.display(row.value());
                        drawWrappedCell(graphics, font, cell, value, stacked ? ReadOnlyDataTable.Alignment.LEFT : column.alignment(),
                                AscendanceUiPalette.argb(AscendanceUiPalette.PRIMARY_TEXT));
                    }
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
        sortFocus = -1;
        if (y < bounds.y() + 1 + headerHeight) {
            for (int index = 0; index < widths.size(); index++) {
                var measured = widths.get(index);
                var column = table.columns().get(index);
                UiBounds cell = new UiBounds(bounds.x() + 1 + measured.x(), bounds.y() + 1,
                        measured.width(), headerHeight);
                if (cell.contains(x, y) && column.sortable()) {
                    sort(column);
                    return true;
                }
            }
            return true;
        }
        int rowIndex = (int) ((scroll.offset() + y - bounds.y() - 1 - headerHeight) / rowHeight);
        List<ReadOnlyDataTable.Row<R>> rows = orderedRows();
        if (rowIndex >= 0 && rowIndex < rows.size()) {
            if (stacked) {
                int fieldY = bounds.y() + 1 + rowIndex * rowHeight - scroll.offset() + 2;
                for (int col = 0; col < table.columns().size(); col++) {
                    UiBounds value = valueBounds(col, bounds.y() + 1 + rowIndex * rowHeight - scroll.offset(), wrappingFont);
                    if (y >= fieldY && y < value.y() && table.columns().get(col).sortable()) { sort(table.columns().get(col)); return true; }
                    fieldY += fieldHeights.get(col);
                }
            }
            var row = rows.get(rowIndex);
            selectedKey = row.key();
            if (row.target() != null) navigate.accept(row.target());
        }
        return true;
    }

    public boolean scroll(double amount) {
        int previous = scroll.offset();
        scroll.wheel(amount, Math.min(rowHeight * 3, 48));
        return scroll.offset() != previous;
    }

    public boolean key(int key) {
        if (key == 262 || key == 263) {
            for (int count = 0; count < table.columns().size(); count++) {
                sortFocus = Math.floorMod(sortFocus + (key == 262 ? 1 : -1), table.columns().size());
                if (table.columns().get(sortFocus).sortable()) return true;
            }
            sortFocus = -1;
            return false;
        }
        if (key == 257 || key == 335 || key == 32) {
            if (sortFocus >= 0) { sort(table.columns().get(sortFocus)); return true; }
            orderedRows().stream().filter(row -> row.key().equals(selectedKey) && row.target() != null)
                    .findFirst().ifPresent(row -> navigate.accept(row.target()));
            return true;
        }
        if (key == 264 || key == 265) {
            sortFocus = -1;
            List<ReadOnlyDataTable.Row<R>> rows = orderedRows();
            if (rows.isEmpty()) return true;
            int index = -1;
            for (int i = 0; i < rows.size(); i++) if (rows.get(i).key().equals(selectedKey)) index = i;
            index = index < 0 ? 0 : Math.max(0, Math.min(rows.size() - 1, index + (key == 264 ? 1 : -1)));
            selectedKey = rows.get(index).key();
            scroll.ensureVisible(index * rowHeight, rowHeight);
            return true;
        }
        return scroll.key(key, 14, Math.max(1, bounds.height() - headerHeight - 2));
    }

    private void sort(ReadOnlyDataTable.Column<R, ?> column) {
        if (column.id().equals(sortColumn)) sortDirection = sortDirection == ReadOnlyDataTable.SortDirection.ASCENDING
                ? ReadOnlyDataTable.SortDirection.DESCENDING : ReadOnlyDataTable.SortDirection.ASCENDING;
        else { sortColumn = column.id(); sortDirection = ReadOnlyDataTable.SortDirection.ASCENDING; }
        ordered = null;
        scroll.restore(0);
        scroll.configure(table.rows().size() * rowHeight, Math.max(0, bounds.height() - headerHeight - 2));
    }

    private Component headerLabel(ReadOnlyDataTable.Column<R, ?> column) {
        return column.id().equals(sortColumn) ? column.header().copy().append(Component.translatable(
                sortDirection == ReadOnlyDataTable.SortDirection.ASCENDING ? "gui.essence_ascendance.table.sort.ascending"
                        : "gui.essence_ascendance.table.sort.descending")) : column.header();
    }

    private Component measuredHeader(ReadOnlyDataTable.Column<R, ?> column) {
        return column.sortable() ? column.header().copy().append(Component.translatable("gui.essence_ascendance.table.sort.descending"))
                : column.header();
    }

    /** Full styled value for compressed cells; render its tooltip outside enclosing clips. */
    public Optional<Component> overflowTextAt(double x, double y) {
        if (measureText == null || !bounds.inset(1).contains(x, y)) return Optional.empty();
        boolean header = y < bounds.y() + 1 + headerHeight;
        int rowIndex = (int) ((scroll.offset() + y - bounds.y() - 1 - headerHeight) / rowHeight);
        List<ReadOnlyDataTable.Row<R>> rows = orderedRows();
        if (!header && (rowIndex < 0 || rowIndex >= rows.size())) return Optional.empty();
        for (int index = 0; index < widths.size(); index++) {
            var measured = widths.get(index);
            int rowY = bounds.y() + 1 + headerHeight + rowIndex * rowHeight - scroll.offset();
            UiBounds cell = header ? new UiBounds(bounds.x() + 1 + measured.x(), bounds.y() + 1,
                    measured.width(), headerHeight) : valueBounds(index, rowY, wrappingFont);
            if (!cell.contains(x, y)) continue;
            var column = table.columns().get(index);
            Component value = header ? column.header() : column.display(rows.get(rowIndex).value());
            if (!header && column.isItem()) return Optional.of(value);
            if (!header && wrappingFont != null) return Optional.empty();
            return measureText.applyAsInt(value) > Math.max(0, measured.width() - 8)
                    ? Optional.of(value) : Optional.empty();
        }
        return Optional.empty();
    }

    private UiBounds valueBounds(int column, int rowY, Font font) {
        var measured = widths.get(column);
        if (!stacked) return new UiBounds(bounds.x() + 1 + measured.x(), rowY, measured.width(), rowHeight);
        int y = rowY + 2;
        for (int prior = 0; prior < column; prior++) y += fieldHeights.get(prior);
        int labelHeight = Math.max(1, font.split(measuredHeader(table.columns().get(column)),
                Math.max(1, bounds.width() - 10)).size()) * (font.lineHeight + 1) + 4;
        return new UiBounds(bounds.x() + 1, y + labelHeight, Math.max(0, bounds.width() - 2),
                Math.max(0, fieldHeights.get(column) - labelHeight));
    }

    /** Inline references use exactly the same wrapped cell geometry as the renderer. */
    public List<StyledTextLayout.LinkRegion> textLinks() {
        if (wrappingFont == null) return List.of();
        if (hasInlineLinks == null) hasInlineLinks = table.rows().stream().anyMatch(row -> table.columns().stream()
                .filter(column -> !column.isItem()).anyMatch(column -> StyledTextLayout.runs(column.display(row.value()))
                        .stream().anyMatch(run -> run.style().getClickEvent() != null)));
        if (!hasInlineLinks) return List.of();
        java.util.ArrayList<StyledTextLayout.LinkRegion> links = new java.util.ArrayList<>();
        var rows = orderedRows();
        int first = Math.min(rows.size(), scroll.offset() / rowHeight);
        int end = Math.min(rows.size(), (scroll.offset() + Math.max(0, bounds.height() - headerHeight - 2) + rowHeight - 1) / rowHeight);
        for (int row = first; row < end; row++) for (int col = 0; col < table.columns().size(); col++) {
            var column = table.columns().get(col);
            if (column.isItem()) continue;
            Component value = column.display(rows.get(row).value());
            if (StyledTextLayout.runs(value).stream().noneMatch(run -> run.style().getClickEvent() != null)) continue;
            UiBounds cell = valueBounds(col, bounds.y() + 1 + headerHeight + row * rowHeight - scroll.offset(), wrappingFont);
            var lines = wrappingFont.split(value, Math.max(1, cell.width() - 8));
            int step = wrappingFont.lineHeight + 1;
            int y = cell.y() + Math.max(2, (cell.height() - Math.max(wrappingFont.lineHeight, lines.size() * step - 1)) / 2);
            for (var line : lines) {
                int x = !stacked && column.alignment() == ReadOnlyDataTable.Alignment.RIGHT
                        ? cell.right() - 4 - wrappingFont.width(line) : cell.x() + 4;
                UiBounds body = new UiBounds(bounds.x() + 1, bounds.y() + 1 + headerHeight,
                        Math.max(0, bounds.width() - 2), Math.max(0, bounds.height() - headerHeight - 2));
                for (var link : StyledTextLayout.links(wrappingFont, List.of(line), x, y, step)) {
                    UiBounds clipped = link.bounds().intersection(cell).intersection(body);
                    if (clipped.width() > 0 && clipped.height() > 0)
                        links.add(new StyledTextLayout.LinkRegion(clipped, link.target()));
                }
                y += step;
            }
        }
        return List.copyOf(links);
    }

    private static void drawWrappedCell(GuiGraphics graphics, Font font, UiBounds bounds, Component value,
                                        ReadOnlyDataTable.Alignment alignment, int color) {
        int available = Math.max(1, bounds.width() - 8);
        List<FormattedCharSequence> lines = font.split(value, available);
        if (lines.isEmpty()) lines = List.of(FormattedCharSequence.EMPTY);
        int lineStep = font.lineHeight + 1;
        int textHeight = Math.max(font.lineHeight, lines.size() * lineStep - 1);
        int y = bounds.y() + Math.max(2, (bounds.height() - textHeight) / 2);
        List<FormattedCharSequence> rendered = lines;
        int startY = y;
        FullscreenViewport.withClip(graphics, bounds, () -> {
            int lineY = startY;
            for (FormattedCharSequence line : rendered) {
                int x = alignment == ReadOnlyDataTable.Alignment.RIGHT
                        ? bounds.right() - 4 - font.width(line) : bounds.x() + 4;
                graphics.drawString(font, line, x, lineY, color, false);
                lineY += lineStep;
            }
        });
    }

    private int measureUniformRowHeight(Font font, List<ReadOnlyDataTable.ColumnWidth> measuredWidths) {
        int maximumLines = 1;
        int imageHeight = ROW_HEIGHT;
        for (ReadOnlyDataTable.Row<R> row : table.rows()) {
            for (int index = 0; index < measuredWidths.size(); index++) {
                int available = Math.max(1, measuredWidths.get(index).width() - 8);
                if (table.columns().get(index).isItem()) {
                    Component caption = table.columns().get(index).item(row.value()).caption();
                    imageHeight = Math.max(imageHeight, 32 + (caption.getString().isEmpty() ? 0
                            : font.split(caption, available).size() * (font.lineHeight + 1) + 5));
                    continue;
                }
                int lines = Math.max(1, font.split(table.columns().get(index).display(row.value()), available).size());
                maximumLines = Math.max(maximumLines, lines);
            }
        }
        return Math.max(imageHeight, maximumLines * (font.lineHeight + 1) + 5);
    }

    private void renderScrollbar(GuiGraphics graphics, UiBounds body, int rowCount) {
        if (scroll.maximumOffset() <= 0 || body.height() <= 0) return;
        int thumbHeight = Math.max(8, (int) ((long) body.height() * body.height() / Math.max(1, rowCount * rowHeight)));
        int travel = Math.max(0, body.height() - thumbHeight);
        int y = body.y() + travel * scroll.offset() / Math.max(1, scroll.maximumOffset());
        graphics.fill(body.right() - 2, body.y(), body.right(), body.bottom(), 0x88535B68);
        graphics.fill(body.right() - 2, y, body.right(), y + thumbHeight,
                AscendanceUiPalette.argb(AscendanceUiPalette.BORDER));
    }
}
