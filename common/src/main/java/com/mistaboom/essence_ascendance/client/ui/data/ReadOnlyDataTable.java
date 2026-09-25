package com.mistaboom.essence_ascendance.client.ui.data;

import com.mistaboom.essence_ascendance.client.ui.content.ItemPresentation;
import com.mistaboom.essence_ascendance.client.ui.StyledTextLayout;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public record Row<R>(String key, R value, String target) {
        public Row(String key, R value) { this(key, value, null); }
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
        private Function<R, ItemPresentation> item;
        private String equalWidthGroup;
        private boolean keepTogether;

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

        public static <R> Column<R, ItemPresentation> item(String id, Component header,
                                                          Function<R, ItemPresentation> value) {
            return item(id, header, 36, value);
        }

        public static <R> Column<R, ItemPresentation> item(String id, Component header, int width,
                                                          Function<R, ItemPresentation> value) {
            Column<R, ItemPresentation> column = new Column<>(id, header, width, 0, Alignment.LEFT,
                    value, ItemPresentation::label, null);
            column.item = value;
            return column;
        }

        public static <R, N extends Number & Comparable<N>> Column<R, N> number(
                String id, Component header, int minimumWidth, int weight,
                Function<R, N> value, Function<N, Component> presentation) {
            return new Column<>(id, header, minimumWidth, weight, Alignment.RIGHT,
                    value, presentation, Comparator.naturalOrder());
        }

        public String id() { return id; }
        /** Opt-in equal sizing; ungrouped columns absorb indivisible pixel remainders. */
        public Column<R, T> equalWidthGroup(String group) {
            if (group == null || group.isBlank()) throw new IllegalArgumentException("Blank width group");
            equalWidthGroup = group;
            return this;
        }
        public Component header() { return header; }
        /** A localized unit or other short semantic value must remain a complete phrase. */
        public Column<R, T> keepValuesTogether() { keepTogether = true; return this; }
        public int minimumWidth() { return minimumWidth; }
        public int weight() { return weight; }
        public Alignment alignment() { return alignment; }
        public boolean sortable() { return comparator != null; }
        public Component display(R row) { return presentation.apply(value.apply(row)); }
        public boolean isItem() { return item != null; }
        public ItemPresentation item(R row) { return item == null ? null : item.apply(row); }

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

    private int[] protectedWidths(ToIntFunction<Component> measureText) {
        int[] result = new int[columns.size()];
        for (int index = 0; index < columns.size(); index++) {
            Column<R, ?> column = columns.get(index);
            int longest = wordWidth(column.header(), measureText);
            for (Row<R> row : rows) {
                Component text = column.isItem() ? column.item(row.value()).caption() : column.display(row.value());
                longest = Math.max(longest, column.keepTogether ? measureText.applyAsInt(text) : wordWidth(text, measureText));
            }
            result[index] = Math.max(column.isItem() ? 36 : 16, longest + 8);
        }
        for (int index = 0; index < columns.size(); index++) {
            String group = columns.get(index).equalWidthGroup;
            if (group == null) continue;
            int maximum = result[index];
            for (int other = 0; other < columns.size(); other++)
                if (group.equals(columns.get(other).equalWidthGroup)) maximum = Math.max(maximum, result[other]);
            for (int other = 0; other < columns.size(); other++)
                if (group.equals(columns.get(other).equalWidthGroup)) result[other] = maximum;
        }
        return result;
    }

    private static int wordWidth(Component text, ToIntFunction<Component> measure) {
        int maximum = 0;
        for (var run : StyledTextLayout.runs(text)) for (String word : run.text().split("\\s+"))
            maximum = Math.max(maximum, measure.applyAsInt(Component.literal(word).setStyle(run.style())));
        return maximum;
    }

    public int minimumContentWidth(ToIntFunction<Component> measureText) {
        return java.util.Arrays.stream(protectedWidths(measureText)).sum() + columns.size() - 1;
    }

    /** Preserve complete words and declared units before distributing preferred schema widths. */
    public List<ColumnWidth> measureContent(int availableWidth, ToIntFunction<Component> measureText) {
        int[] minimum = protectedWidths(measureText);
        int available = Math.max(0, availableWidth - columns.size() + 1);
        int total = java.util.Arrays.stream(minimum).sum();
        if (total > available) return measure(availableWidth, 1, measureText);
        var preferred = measure(availableWidth, 1, measureText);
        int[] assigned = new int[minimum.length];
        int used = 0;
        for (int index = 0; index < assigned.length; index++) {
            assigned[index] = Math.max(minimum[index], preferred.get(index).width());
            used += assigned[index];
        }
        while (used > available) {
            boolean reduced = false;
            for (int index = assigned.length - 1; index >= 0 && used > available; index--)
                if (assigned[index] > minimum[index]) { assigned[index]--; used--; reduced = true; }
            if (!reduced) break;
        }
        // Equal groups retain the same protected width; redistribute their rounding pixels to flexible text.
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < columns.size(); i++) if (columns.get(i).equalWidthGroup != null)
            groups.computeIfAbsent(columns.get(i).equalWidthGroup, ignored -> new ArrayList<>()).add(i);
        int remainder = 0;
        for (var group : groups.values()) {
            int sum = group.stream().mapToInt(i -> assigned[i]).sum();
            for (int i : group) assigned[i] = sum / group.size();
            remainder += sum % group.size();
        }
        for (int i = 0; i < columns.size(); i++) if (columns.get(i).equalWidthGroup == null) {
            assigned[i] += remainder; break;
        }
        int x = 0;
        List<ColumnWidth> result = new ArrayList<>();
        for (int i = 0; i < assigned.length; i++) {
            result.add(new ColumnWidth(columns.get(i).id(), x, assigned[i]));
            x += assigned[i] + 1;
        }
        return List.copyOf(result);
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
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        for (int index = 0; index < columns.size(); index++) {
            Column<R, ?> column = columns.get(index);
            int measured = measureText.applyAsInt(column.header()) + 10;
            desired[index] = Math.max(column.minimumWidth(), measured);
            totalWeight += column.weight();
            if (column.equalWidthGroup != null)
                groups.computeIfAbsent(column.equalWidthGroup, ignored -> new ArrayList<>()).add(index);
        }
        for (List<Integer> group : groups.values()) {
            int maximum = group.stream().mapToInt(index -> desired[index]).max().orElse(0);
            for (int index : group) desired[index] = maximum;
        }
        for (int value : desired) desiredTotal += value;
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
        int remainder = 0;
        for (List<Integer> group : groups.values()) {
            int total = group.stream().mapToInt(index -> assigned[index]).sum();
            int equal = total / group.size();
            for (int index : group) assigned[index] = equal;
            remainder += total % group.size();
        }
        // Prefer the flexible text column; all-grouped schemas leave at most a few trailing pixels.
        int remainderColumn = -1;
        for (int index = 0; index < columns.size(); index++) {
            if (columns.get(index).equalWidthGroup == null && (remainderColumn < 0
                    || columns.get(index).weight() > columns.get(remainderColumn).weight())) remainderColumn = index;
        }
        if (remainderColumn >= 0) assigned[remainderColumn] += remainder;
        int x = 0;
        List<ColumnWidth> result = new ArrayList<>(columns.size());
        for (int index = 0; index < columns.size(); index++) {
            result.add(new ColumnWidth(columns.get(index).id(), x, assigned[index]));
            x += assigned[index] + actualGap;
        }
        return List.copyOf(result);
    }
}
