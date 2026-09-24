package com.mistaboom.essence_ascendance.client.archive;

import com.mistaboom.essence_ascendance.client.EssenceYieldFormat;
import com.mistaboom.essence_ascendance.client.ItemEssenceTooltipClientState;
import com.mistaboom.essence_ascendance.client.presentation.PresentationContext;
import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.client.ui.content.ItemPresentation;
import com.mistaboom.essence_ascendance.client.ui.data.ReadOnlyDataTable;
import com.mistaboom.essence_ascendance.client.ui.data.ReadOnlyDataTableView;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenComposition;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenControls;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenLayout;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import com.mistaboom.essence_ascendance.visual.AscendanceUiPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Dedicated bounded/virtualized Item Yields browser; ordinary article tables never enter this viewport. */
public final class ItemYieldBrowser {
    private static final int CONTROL_HEIGHT = 18;
    private static final int GAP = 4;

    public record SearchMetadata(ResourceLocation itemId, Component localizedName, String target,
                                 List<Component> terms) { }

    public record BrowserRow(ResourceLocation itemId, Component name, ItemPresentation item,
                             Map<ResourceLocation, Long> yields) {
        public BrowserRow { yields = Map.copyOf(yields); }
        long yield(ResourceLocation essence) { return yields.getOrDefault(essence, 0L); }
    }

    private record CacheKey(ItemEssenceTooltipClientState.YieldSnapshot snapshot,
                            PresentationContext.Revision revision, String query,
                            Set<ResourceLocation> selected, ArchiveNavigationState.YieldMatch match) { }
    private record ProjectionKey(ItemEssenceTooltipClientState.YieldSnapshot snapshot,
                                 PresentationContext.Revision revision) { }

    private final Consumer<ArchiveNavigationState.YieldBrowser> changed;
    private final ArchiveSearchField search;
    private ArchiveNavigationState.YieldBrowser state;
    private CacheKey cacheKey;
    private ProjectionKey projectionKey;
    private List<BrowserRow> projectedRows = List.of();
    private ReadOnlyDataTableView<BrowserRow> table;
    private List<BrowserRow> visibleRows = List.of();
    private UiBounds tableBounds = new UiBounds(0, 0, 0, 0);

    public ItemYieldBrowser(ArchiveNavigationState.YieldBrowser initial,
                            Consumer<ArchiveNavigationState.YieldBrowser> changed) {
        this.state = Objects.requireNonNull(initial);
        this.changed = Objects.requireNonNull(changed);
        search = new ArchiveSearchField(initial.query(), g("archive.reference.item_yields.filter.placeholder"),
                query -> update(new ArchiveNavigationState.YieldBrowser(query, state.selectedEssences(), state.match(),
                        state.sortColumn(), state.sortDirection(), state.selectedRow(), 0)));
    }

    public ArchiveNavigationState.YieldBrowser state() { return state; }

    public void restore(ArchiveNavigationState.YieldBrowser restored) {
        if (Objects.equals(state, restored)) return;
        state = Objects.requireNonNull(restored);
        search.query(state.query());
        cacheKey = null;
    }

    public void compose(FullscreenComposition.Builder builder, UiBounds bounds, Font font,
                        ItemEssenceTooltipClientState.YieldSnapshot snapshot,
                        PresentationContext.Revision revision) {
        ProjectionKey nextProjection = new ProjectionKey(snapshot, revision);
        if (!nextProjection.equals(projectionKey)) {
            projectedRows = snapshot.ready() ? project(snapshot) : List.of();
            projectionKey = nextProjection;
            cacheKey = null;
        }
        CacheKey nextKey = new CacheKey(snapshot, revision, state.query(), state.selectedEssences(), state.match());
        if (!nextKey.equals(cacheKey)) rebuild(snapshot, nextKey);

        FullscreenLayout.Bands bands = FullscreenLayout.bands(bounds, 24, 0, 6);
        UiBounds query = bands.header();
        search.bounds(query);
        builder.region(new FullscreenComposition.Region("archive/yields/query", query,
                (graphics, x, ignored, tick) -> search.render(graphics, font), search, true));
        int y = bands.body().y();

        List<EssenceDefinition> essences = List.copyOf(EssenceRegistry.values());
        int columns = Math.max(1, Math.min(essences.size(), bounds.width() / 76));
        int rows = (essences.size() + columns - 1) / columns;
        int cellWidth = Math.max(1, (bounds.width() - GAP * (columns - 1)) / columns);
        for (int index = 0; index < essences.size(); index++) {
            EssenceDefinition essence = essences.get(index);
            int column = index % columns;
            int row = index / columns;
            UiBounds cell = new UiBounds(bounds.x() + column * (cellWidth + GAP), y + row * (CONTROL_HEIGHT + GAP),
                    cellWidth, CONTROL_HEIGHT);
            Set<ResourceLocation> selected = state.selectedEssences();
            builder.control(new FullscreenComposition.Control("archive/yields/category/" + essence.id(), cell,
                    EssenceText.essenceShort(essence), true, selected.contains(essence.id()),
                    FullscreenControls.Style.TAB, AscendancePalette.categoryRgb(essence.id()),
                    () -> toggle(essence.id())));
        }
        y += rows * CONTROL_HEIGHT + Math.max(0, rows - 1) * GAP + GAP;

        int third = Math.max(1, (bounds.width() - GAP * 4) / 5);
        builder.control(control("archive/yields/any", new UiBounds(bounds.x(), y, third, CONTROL_HEIGHT),
                g("archive.reference.item_yields.match.any"), true,
                state.match() == ArchiveNavigationState.YieldMatch.ANY,
                () -> match(ArchiveNavigationState.YieldMatch.ANY)));
        builder.control(control("archive/yields/all", new UiBounds(bounds.x() + third + GAP, y, third, CONTROL_HEIGHT),
                g("archive.reference.item_yields.match.all"), true,
                state.match() == ArchiveNavigationState.YieldMatch.ALL,
                () -> match(ArchiveNavigationState.YieldMatch.ALL)));
        builder.control(control("archive/yields/reset", new UiBounds(bounds.x() + (third + GAP) * 2, y, third, CONTROL_HEIGHT),
                g("archive.control.reset"), !state.equals(ArchiveNavigationState.YieldBrowser.initial()), false, this::reset));
        builder.control(control("archive/yields/clear", new UiBounds(bounds.x() + (third + GAP) * 3, y, third, CONTROL_HEIGHT),
                g("archive.control.clear"), !state.query().isEmpty(), false, this::clearQuery));
        UiBounds count = new UiBounds(bounds.x() + (third + GAP) * 4, y,
                Math.max(0, bounds.right() - (bounds.x() + (third + GAP) * 4)), CONTROL_HEIGHT);
        builder.region(new FullscreenComposition.Region("archive/yields/count", count,
                (graphics, x, ignored, tick) -> graphics.drawCenteredString(font,
                        snapshot.ready() ? g("archive.reference.item_yields.result_count", visibleRows.size())
                                : g("archive.reference.item_yields.unavailable_short"),
                        count.x() + count.width() / 2, count.y() + 5,
                        AscendanceUiPalette.argb(AscendanceUiPalette.MUTED_TEXT)),
                FullscreenComposition.Input.NONE, false));
        y += CONTROL_HEIGHT + GAP;

        tableBounds = new UiBounds(bounds.x(), y, bounds.width(), Math.max(0, bounds.bottom() - y));
        if (table != null) table.prepare(font, tableBounds);
        builder.region(new FullscreenComposition.Region("archive/yields/table", tableBounds,
                (graphics, x, mouseY, tick) -> { if (table != null) table.render(graphics, font, x, mouseY); },
                new TableInput(), true)).primaryInput("archive/yields/table");
    }

    public void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (table == null || !tableBounds.contains(mouseX, mouseY)) return;
        table.overflowTextAt(mouseX, mouseY).ifPresent(value -> graphics.renderTooltip(
                net.minecraft.client.Minecraft.getInstance().font, value, mouseX, mouseY));
    }

    public static List<SearchMetadata> searchMetadata(ItemEssenceTooltipClientState.YieldSnapshot snapshot) {
        if (!snapshot.ready()) return List.of();
        return project(snapshot).stream().map(row -> new SearchMetadata(row.itemId(), row.name(),
                ArchiveNavigator.yieldTarget(row.itemId()), List.of(row.name()))).toList();
    }

    private void rebuild(ItemEssenceTooltipClientState.YieldSnapshot snapshot, CacheKey nextKey) {
        List<BrowserRow> rows = snapshot.ready() ? filterRows(projectedRows, state.query(),
                state.selectedEssences(), state.match()) : List.of();
        visibleRows = rows;
        ReadOnlyDataTable<BrowserRow> data = tableData(rows);
        table = new ReadOnlyDataTableView<>(data);
        table.restore(state.selectedRow() == null ? null : state.selectedRow().toString(), state.scroll(),
                state.sortColumn(), direction(state.sortDirection()));
        cacheKey = nextKey;
    }

    public static ReadOnlyDataTable<BrowserRow> tableData(List<BrowserRow> rows) {
        List<ReadOnlyDataTable.Column<BrowserRow, ?>> columns = new ArrayList<>();
        columns.add(ReadOnlyDataTable.Column.item("image", g("archive.reference.item_yields.table.item"), 38, BrowserRow::item));
        columns.add(ReadOnlyDataTable.Column.text("name", g("archive.table.name"), 92, 2, BrowserRow::name,
                Comparator.comparing(component -> component.getString().toLowerCase(Locale.ROOT))));
        for (EssenceDefinition essence : EssenceRegistry.values()) {
            Component header = EssenceText.essenceShort(essence).copy().withStyle(
                    style -> style.withColor(AscendancePalette.categoryRgb(essence.id())));
            columns.add(ReadOnlyDataTable.Column.<BrowserRow, Long>number(essence.id().toString(), header,
                    42, 1, row -> row.yield(essence.id()),
                    microUnits -> amount(microUnits, essence.id())).equalWidthGroup("essence"));
        }
        return new ReadOnlyDataTable<>(columns,
                rows.stream().map(row -> new ReadOnlyDataTable.Row<>(row.itemId().toString(), row)).toList());
    }

    public static List<BrowserRow> project(ItemEssenceTooltipClientState.YieldSnapshot snapshot) {
        List<BrowserRow> result = new ArrayList<>();
        for (ItemEssenceTooltipClientState.YieldRow source : snapshot.rows()) {
            if (!BuiltInRegistries.ITEM.containsKey(source.itemId())) continue;
            ItemStack stack = BuiltInRegistries.ITEM.get(source.itemId()).getDefaultInstance();
            if (stack.isEmpty()) continue;
            Component name = stack.getHoverName();
            Map<ResourceLocation, Long> yields = new LinkedHashMap<>();
            source.outputs().forEach(output -> yields.put(output.essenceId(), output.microUnits()));
            result.add(new BrowserRow(source.itemId(), name,
                    new ItemPresentation(source.itemId(), DataComponentPatch.EMPTY, name), Map.copyOf(yields)));
        }
        return List.copyOf(result);
    }

    public static List<BrowserRow> filterRows(List<BrowserRow> rows, String query,
                                               Set<ResourceLocation> selected,
                                               ArchiveNavigationState.YieldMatch match) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Set<ResourceLocation> categories = selected == null ? Set.of() : selected;
        ArchiveNavigationState.YieldMatch policy = match == null ? ArchiveNavigationState.YieldMatch.ANY : match;
        return rows.stream().filter(row -> needle.isEmpty()
                        || row.name().getString().toLowerCase(Locale.ROOT).contains(needle))
                .filter(row -> categories.isEmpty() || (policy == ArchiveNavigationState.YieldMatch.ALL
                        ? categories.stream().allMatch(id -> row.yield(id) > 0L)
                        : categories.stream().anyMatch(id -> row.yield(id) > 0L)))
                .toList();
    }

    public static List<BrowserRow> sortRows(List<BrowserRow> rows, String column,
                                             ArchiveNavigationState.YieldSortDirection direction) {
        Comparator<BrowserRow> comparator;
        ResourceLocation essence = ResourceLocation.tryParse(column);
        if ("name".equals(column)) comparator = Comparator.comparing(
                row -> row.name().getString().toLowerCase(Locale.ROOT));
        else if (essence != null) comparator = Comparator.comparingLong(row -> row.yield(essence));
        else return List.copyOf(rows);
        if (direction == ArchiveNavigationState.YieldSortDirection.DESCENDING) comparator = comparator.reversed();
        return rows.stream().sorted(comparator.thenComparing(row -> row.itemId().toString())).toList();
    }

    private FullscreenComposition.Control control(String id, UiBounds bounds, Component label,
                                                   boolean enabled, boolean selected, Runnable action) {
        return new FullscreenComposition.Control(id, bounds, label, enabled, selected,
                FullscreenControls.Style.ACTION, AscendanceUiPalette.INTERACTIVE, action);
    }

    private void clearQuery() {
        search.query("");
        update(new ArchiveNavigationState.YieldBrowser("", state.selectedEssences(), state.match(),
                state.sortColumn(), state.sortDirection(), state.selectedRow(), 0));
    }

    private void toggle(ResourceLocation id) {
        Set<ResourceLocation> selected = new LinkedHashSet<>(state.selectedEssences());
        if (!selected.add(id)) selected.remove(id);
        update(new ArchiveNavigationState.YieldBrowser(state.query(), selected, state.match(), state.sortColumn(),
                state.sortDirection(), state.selectedRow(), 0));
    }

    private void match(ArchiveNavigationState.YieldMatch match) {
        if (match == state.match()) return;
        update(new ArchiveNavigationState.YieldBrowser(state.query(), state.selectedEssences(), match,
                state.sortColumn(), state.sortDirection(), state.selectedRow(), 0));
    }

    private void reset() {
        search.query("");
        update(ArchiveNavigationState.YieldBrowser.initial());
    }

    private void update(ArchiveNavigationState.YieldBrowser next) {
        state = next;
        cacheKey = null;
        changed.accept(next);
    }

    private void publishTableState() {
        if (table == null) return;
        ResourceLocation selected = table.selectedKey() == null ? null : ResourceLocation.tryParse(table.selectedKey());
        ArchiveNavigationState.YieldBrowser next = new ArchiveNavigationState.YieldBrowser(state.query(),
                state.selectedEssences(), state.match(), table.sortColumn(),
                table.sortDirection() == ReadOnlyDataTable.SortDirection.DESCENDING
                        ? ArchiveNavigationState.YieldSortDirection.DESCENDING
                        : ArchiveNavigationState.YieldSortDirection.ASCENDING,
                selected, table.scrollOffset());
        if (!next.equals(state)) { state = next; changed.accept(next); }
    }

    private final class TableInput implements FullscreenComposition.Input {
        @Override public boolean click(double x, double y, int button) {
            if (table == null) return false;
            boolean result = table.click(x, y, button); publishTableState(); return result;
        }
        @Override public boolean scroll(double x, double y, double dx, double dy) {
            if (table == null) return false;
            boolean result = table.scroll(dy); publishTableState(); return result;
        }
        @Override public boolean key(int key, int scan, int modifiers) {
            if (table == null) return false;
            boolean result = table.key(key); publishTableState(); return result;
        }
    }

    private static Component amount(long microUnits, ResourceLocation essenceId) {
        if (microUnits <= 0L) return Component.literal("—").withStyle(
                style -> style.withColor(AscendanceUiPalette.MUTED_TEXT));
        return Component.literal(EssenceYieldFormat.formatMicros(microUnits)).withStyle(
                style -> style.withColor(AscendancePalette.categoryRgb(essenceId)));
    }
    private static ReadOnlyDataTable.SortDirection direction(ArchiveNavigationState.YieldSortDirection value) {
        return value == ArchiveNavigationState.YieldSortDirection.DESCENDING
                ? ReadOnlyDataTable.SortDirection.DESCENDING : ReadOnlyDataTable.SortDirection.ASCENDING;
    }
    private static Component g(String path, Object... args) { return EssenceText.guide(path, args); }
}
