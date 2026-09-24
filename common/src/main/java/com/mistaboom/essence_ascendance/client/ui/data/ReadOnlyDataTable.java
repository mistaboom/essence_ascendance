package com.mistaboom.essence_ascendance.client.ui.data;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Immutable, typed definition for read-only tabular data. Rows retain stable
 * keys independently of presentation order; sorting is opt-in per column.
 */
public final class ReadOnlyDataTable<R> {
    /** Canonical width for compact rank/index columns across shared data views. */
    public static final int RANK_COLUMN_WIDTH = 48;

    public enum Alignment { LEFT, RIGHT }
    public enum SortDirection { ASCENDING, DESCENDING }

    public record Row<R>(String key, R value) {
        public Row {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("Table row key cannot be blank");
            Objects.requireNonNull(value, "Table row value");
        }
    }

    public static final class Column<R, T> {
        private final String id;
        private final Component header;
        private final int minimumWidth;
        private final int weight;
        private final Alignment alignment;
        private final Function<R, T> value;
        private final Function<T, Component> presentation;
        private final Comparator<T> comparator;

        public Column(String id, Component header, int minimumWidth, int weight, Alignment alignment,
                      Function<R, T> value, Function<T, Component> presentation, Comparator<T> comparator) {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Table column ID cannot be blank");
            if (minimumWidth < 1 || weight < 0) throw new IllegalArgumentException("Invalid table column sizing");
            this.id = id;
            this.header = Objects.requireNonNull(header, "Table column header");
            this.minimumWidth = minimumWidth;
            this.weight = weight;
            this.alignment = Objects.requireNonNull(alignment, "Table column alignment");
            this.value = Objects.requireNonNull(value, "Table value provider");
            this.presentation = Objects.requireNonNull(presentation, "Table value presentation");
            this.comparator = comparator;
        }

        public static <R> Column<R, Component> text(String id, Component header, int minimumWidth, int weight,
                                                     Function<R, Component> value, Comparator<Component> comparator) {
            return new Column<>(id, header, minimumWidth, weight, Alignment.LEFT, value, Function.identity(), comparator);
        }

        public static <R, N extends Number & Comparable<N>> Column<R, N> number(
                String id, Component header, int minimumWidth, int weight,
                Function<R, N> value, Function<N, Component> presentation) {
            return new Column<>(id, header, minimumWidth, weight, Alignment.RIGHT,
                    value, presentation, Comparator.naturalOrder());
        }

        public String id() { return id; }
        public Component header() { return header; }
        public int minimumWidth() { return minimumWidth; }
        public int weight() { return weight; }
        public Alignment alignment() { return alignment; }
        public boolean sortable() { return comparator != null; }
        public Component display(R row) { return presentation.apply(value.apply(row)); }

        private int compare(R left, R right) {
            if (comparator == null) return 0;
            return comparator.compare(value.apply(left), value.apply(right));
        }
    }

    public record ColumnWidth(String id, int x, int width) { }

    private final List<Column<R, ?>> columns;
    private final List<Row<R>> rows;

    public ReadOnlyDataTable(List<Column<R, ?>> columns, List<Row<R>> rows) {
        this.columns = List.copyOf(columns);
        this.rows = List.copyOf(rows);
        if (this.columns.isEmpty()) throw new IllegalArgumentException("A table requires at least one column");
        HashSet<String> columnIds = new HashSet<>();
        for (Column<R, ?> column : this.columns) {
            if (!columnIds.add(column.id())) throw new IllegalArgumentException("Duplicate table column: " + column.id());
        }
        HashSet<String> rowKeys = new HashSet<>();
        for (Row<R> row : this.rows) {
            if (!rowKeys.add(row.key())) throw new IllegalArgumentException("Duplicate table row: " + row.key());
        }
    }

    public List<Column<R, ?>> columns() { return columns; }
    public List<Row<R>> rows() { return rows; }
    public Column<R, ?> column(String id) {
        return columns.stream().filter(column -> column.id().equals(id)).findFirst().orElse(null);
    }

    /** Natural input order is retained when sorting is absent or disabled. */
    public List<Row<R>> ordered(String columnId, SortDirection direction) {
        Column<R, ?> column = columnId == null ? null : column(columnId);
        if (column == null || !column.sortable()) return rows;
        List<Row<R>> ordered = new ArrayList<>(rows);
        Comparator<Row<R>> comparator = (left, right) -> column.compare(left.value(), right.value());
        if (direction == SortDirection.DESCENDING) comparator = comparator.reversed();
        // List.sort is stable: equal values retain natural order without a linear
        // indexOf scan on every comparison in a large table.
        ordered.sort(comparator);
        return List.copyOf(ordered);
    }

    public List<Row<R>> visibleRows(String columnId, SortDirection direction, int start, int count) {
        List<Row<R>> ordered = ordered(columnId, direction);
        int from = Math.min(ordered.size(), Math.max(0, start));
        int to = Math.min(ordered.size(), from + Math.max(0, count));
        return ordered.subList(from, to);
    }

    /**
     * Measure the schema (declared minimums and localized headers), then
     * distribute spare width by declared column weight. Row content never
     * changes column allocation, so equivalent tables retain identical
     * geometry even when one page contains much longer values. If the
     * viewport is narrower, minimums compress proportionally. A zero-width
     * viewport may collapse columns completely.
     */
    public List<ColumnWidth> measure(int availableWidth, int gap, ToIntFunction<Component> measureText) {
        int width = Math.max(0, availableWidth);
        int actualGap = columns.size() == 1 ? 0 : Math.min(Math.max(0, gap), width / (columns.size() - 1));
        int cellBudget = Math.max(0, width - actualGap * (columns.size() - 1));
        int[] desired = new int[columns.size()];
        long desiredTotal = 0;
        int totalWeight = 0;
        for (int index = 0; index < columns.size(); index++) {
            Column<R, ?> column = columns.get(index);
            int measured = measureText.applyAsInt(column.header()) + 10;
            desired[index] = Math.max(column.minimumWidth(), measured);
            desiredTotal += desired[index];
            totalWeight += column.weight();
        }
        int[] assigned = new int[columns.size()];
        if (desiredTotal > cellBudget && desiredTotal > 0) {
            int used = 0;
            long cumulative = 0;
            for (int index = 0; index < desired.length; index++) {
                cumulative += desired[index];
                int target = (int) (cumulative * cellBudget / desiredTotal);
                assigned[index] = Math.max(0, target - used);
                used += assigned[index];
            }
        } else {
            System.arraycopy(desired, 0, assigned, 0, desired.length);
            int spare = cellBudget - (int) desiredTotal;
            int used = 0;
            for (int index = 0; index < assigned.length; index++) {
                int addition = totalWeight == 0 ? 0
                        : (int) ((long) spare * columns.get(index).weight() / totalWeight);
                assigned[index] += addition;
                used += addition;
            }
            if (assigned.length > 0) assigned[assigned.length - 1] += spare - used;
        }
        int x = 0;
        List<ColumnWidth> result = new ArrayList<>(columns.size());
        for (int index = 0; index < columns.size(); index++) {
            result.add(new ColumnWidth(columns.get(index).id(), x, assigned[index]));
            x += assigned[index] + actualGap;
        }
        return List.copyOf(result);
    }
}
