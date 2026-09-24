package com.mistaboom.essence_ascendance.client.ui.content;

import com.mistaboom.essence_ascendance.client.ui.UiBounds;
import com.mistaboom.essence_ascendance.client.ui.StyledTextLayout;
import com.mistaboom.essence_ascendance.client.ui.data.ReadOnlyDataTable;
import com.mistaboom.essence_ascendance.client.ui.data.ReadOnlyDataTableView;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenComposition;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenControls;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenScroll;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenViewport;
import com.mistaboom.essence_ascendance.visual.AscendanceUiPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Reusable article viewport for semantic documents. It owns wrapping,
 * clipping, scrolling, link hit testing and embedded read-only data views;
 * documents only declare content.
 */
public final class ContentViewport implements FullscreenComposition.Input {
    private static final int PADDING = 9;
    private static final int GAP = 8;

    private record BlockLayout(int index, SemanticDocument.Block block, int y, int height, Object detail) { }
    private record LinkHit(UiBounds bounds, String target) { }
    private record ItemHit(UiBounds bounds, Component label) { }
    private record LinkRowLayout(int y, int height, List<FormattedCharSequence> lines) { }
    private record StatRowLayout(int y, int height, List<FormattedCharSequence> label,
                                 List<FormattedCharSequence> value) { }
    private record RequirementRowLayout(int y, int height, List<FormattedCharSequence> label,
                                        List<FormattedCharSequence> detail) { }

    private final FullscreenScroll scroll = new FullscreenScroll();
    private final IllustrationView.Renderer illustrations;
    private final Consumer<String> navigate;
    private final Map<Integer, ReadOnlyDataTableView<?>> tableViews = new HashMap<>();
    private SemanticDocument document;
    private UiBounds bounds = new UiBounds(0, 0, 0, 0);
    private Font font;
    private List<BlockLayout> layout = List.of();
    private List<LinkHit> linkHits = List.of();
    private List<ItemHit> itemHits = List.of();
    private int contentHeight;
    private Integer activeTable;

    public ContentViewport(IllustrationView.Renderer illustrations, Consumer<String> navigate) {
        this.illustrations = Objects.requireNonNull(illustrations, "Illustration renderer");
        this.navigate = Objects.requireNonNull(navigate, "Navigation callback");
    }

    public int scrollOffset() { return scroll.offset(); }
    public void restore(int offset) { scroll.restore(offset); }

    public void prepare(Font font, UiBounds bounds, SemanticDocument document) {
        this.font = Objects.requireNonNull(font, "Font");
        this.bounds = bounds;
        boolean changedDocument = this.document != document;
        this.document = Objects.requireNonNull(document, "Document");
        if (changedDocument) {
            tableViews.clear();
            activeTable = null;
        }
        int width = Math.max(1, bounds.width() - PADDING * 2 - 3);
        int y = PADDING;
        List<BlockLayout> measured = new ArrayList<>();
        int titleHeight = wrappedHeight(font, document.title(), width, font.lineHeight + 2);
        measured.add(new BlockLayout(-1, null, y, titleHeight, font.split(document.title(), width)));
        y += titleHeight + GAP;
        for (int index = 0; index < document.blocks().size(); index++) {
            SemanticDocument.Block block = document.blocks().get(index);
            Object detail;
            int height;
            if (block instanceof SemanticDocument.Heading heading) {
                int lineHeight = heading.level() == SemanticDocument.HeadingLevel.SECTION
                        ? font.lineHeight + 2 : font.lineHeight + 1;
                List<FormattedCharSequence> lines = font.split(heading.text(), width);
                detail = lines;
                height = Math.max(lineHeight, lines.size() * lineHeight + 2);
            } else if (block instanceof SemanticDocument.Paragraph paragraph) {
                List<FormattedCharSequence> lines = font.split(paragraph.text(), width);
                detail = lines;
                height = Math.max(font.lineHeight, lines.size() * (font.lineHeight + 2));
            } else if (block instanceof SemanticDocument.OrderedSteps steps) {
                List<List<FormattedCharSequence>> lines = new ArrayList<>();
                height = 0;
                for (Component step : steps.steps()) {
                    List<FormattedCharSequence> wrapped = font.split(step, Math.max(1, width - 22));
                    lines.add(wrapped);
                    height += Math.max(1, wrapped.size()) * (font.lineHeight + 2) + 3;
                }
                detail = List.copyOf(lines);
            } else if (block instanceof SemanticDocument.Callout callout) {
                List<FormattedCharSequence> lines = font.split(callout.text(), Math.max(1, width - 14));
                detail = lines;
                height = font.lineHeight + 8 + Math.max(1, lines.size()) * (font.lineHeight + 2) + 6;
            } else if (block instanceof SemanticDocument.Illustration illustration) {
                IllustrationView.Layout illustrationLayout = IllustrationView.layout(font, illustration,
                        new UiBounds(0, 0, width, illustration.preferredHeight() + font.lineHeight * 3 + 12));
                detail = illustrationLayout;
                height = illustrationLayout.bounds().height();
            } else if (block instanceof SemanticDocument.ItemIllustration illustration) {
                SemanticDocument.Illustration adapter = itemIllustrationAdapter(illustration);
                IllustrationView.Layout illustrationLayout = IllustrationView.layout(font, adapter,
                        new UiBounds(0, 0, width, illustration.preferredHeight() + font.lineHeight * 3 + 12));
                detail = illustrationLayout;
                height = illustrationLayout.bounds().height();
            } else if (block instanceof SemanticDocument.Icon) {
                detail = null;
                height = 24;
            } else if (block instanceof SemanticDocument.ItemRow items) {
                int columns = Math.max(1, width / 38);
                detail = columns;
                height = Math.max(1, (items.items().size() + columns - 1) / columns) * 38;
            } else if (block instanceof SemanticDocument.StatRows stats) {
                List<StatRowLayout> rows = new ArrayList<>();
                int rowY = 0;
                int columnWidth = Math.max(1, (width - 12) / 2);
                for (SemanticDocument.StatRow row : stats.rows()) {
                    List<FormattedCharSequence> labels = font.split(row.label(), columnWidth);
                    List<FormattedCharSequence> values = font.split(row.value(), columnWidth);
                    int rowHeight = Math.max(16, Math.max(labels.size(), values.size()) * (font.lineHeight + 1) + 6);
                    rows.add(new StatRowLayout(rowY, rowHeight, labels, values));
                    rowY += rowHeight;
                }
                detail = List.copyOf(rows);
                height = rowY + 2;
            } else if (block instanceof SemanticDocument.Requirements requirements) {
                List<RequirementRowLayout> rows = new ArrayList<>();
                int rowY = 0;
                int textWidth = requirementTextWidth(width);
                for (SemanticDocument.Requirement requirement : requirements.rows()) {
                    List<FormattedCharSequence> labels = font.split(requirement.label(), textWidth);
                    List<FormattedCharSequence> details = font.split(requirement.detail(), textWidth);
                    int rowHeight = Math.max(1, labels.size()) * (font.lineHeight + 1)
                            + Math.max(1, details.size()) * (font.lineHeight + 2) + 10;
                    rows.add(new RequirementRowLayout(rowY, rowHeight, labels, details));
                    rowY += rowHeight;
                }
                detail = List.copyOf(rows);
                height = rowY + 2;
            } else if (block instanceof SemanticDocument.Table<?> table) {
                ReadOnlyDataTableView<?> view = tableViews.computeIfAbsent(index, ignored -> tableView(table));
                int rowHeight = view.uniformRowHeight(font, width);
                height = ReadOnlyDataTableView.HEADER_HEIGHT
                        + visibleTableRows(table) * rowHeight + 2;
                detail = view;
            } else if (block instanceof SemanticDocument.Links links) {
                List<LinkRowLayout> rows = new ArrayList<>();
                int rowY = 0;
                for (SemanticDocument.Link link : links.links()) {
                    var lines = font.split(linkLabel(link), Math.max(1, width - 10));
                    int rowHeight = Math.max(18, lines.size() * (font.lineHeight + 1) + 6);
                    rows.add(new LinkRowLayout(rowY, rowHeight, lines));
                    rowY += rowHeight;
                }
                detail = List.copyOf(rows);
                height = rowY;
            } else {
                throw new IllegalStateException("Unknown semantic content block " + block);
            }
            measured.add(new BlockLayout(index, block, y, height, detail));
            y += height + GAP;
        }
        contentHeight = Math.max(0, y - GAP + PADDING);
        layout = List.copyOf(measured);
        scroll.configure(contentHeight, bounds.height());
        prepareInteractiveChildren();
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        FullscreenControls.panel(graphics, bounds, AscendanceUiPalette.argb(AscendanceUiPalette.SURFACE),
                AscendanceUiPalette.argb(AscendanceUiPalette.BORDER));
        UiBounds clip = bounds.inset(1);
        FullscreenViewport.withClip(graphics, clip, () -> {
            for (BlockLayout block : layout) {
                int y = screenY(block.y());
                if (y + block.height() < clip.y() || y >= clip.bottom()) continue;
                if (block.index() == -1) {
                    drawLines(graphics, castLines(block.detail()), bounds.x() + PADDING, y,
                            font.lineHeight + 2, AscendanceUiPalette.argb(AscendanceUiPalette.PRIMARY_TEXT));
                } else {
                    renderBlock(graphics, block, y, mouseX, mouseY, partialTick);
                }
            }
        });
        renderScrollbar(graphics);
    }

    @Override public boolean click(double x, double y, int button) {
        if (!bounds.contains(x, y)) return false;
        for (LinkHit link : linkHits) {
            if (link.bounds().contains(x, y)) {
                activeTable = null;
                if (button == 0) navigate.accept(link.target());
                return true;
            }
        }
        for (BlockLayout block : layout) {
            if (!(block.detail() instanceof ReadOnlyDataTableView<?> table)) continue;
            UiBounds tableBounds = blockBounds(block);
            if (tableBounds.contains(x, y)) {
                activeTable = block.index();
                return table.click(x, y, button);
            }
        }
        activeTable = null;
        return true;
    }

    @Override public boolean scroll(double x, double y, double dx, double dy) {
        for (BlockLayout block : layout) {
            if (block.detail() instanceof ReadOnlyDataTableView<?> table && blockBounds(block).contains(x, y)
                    && table.hasOverflow() && table.scroll(dy)) return true;
        }
        scroll.wheel(dy, 28);
        prepareInteractiveChildren();
        return true;
    }

    @Override public boolean key(int key, int scan, int modifiers) {
        if (activeTable != null) {
            for (BlockLayout block : layout) {
                if (block.index() == activeTable && block.block() instanceof SemanticDocument.Table<?> definition
                        && definition.expanded() && block.detail() instanceof ReadOnlyDataTableView<?> table
                        && (key == 264 || key == 265)) {
                    table.key(key);
                    var rows = table.orderedRows();
                    for (int index = 0; index < rows.size(); index++) if (rows.get(index).key().equals(table.selectedKey()))
                        scroll.ensureVisible(block.y() + ReadOnlyDataTableView.HEADER_HEIGHT + 1 + index * table.rowHeight(), table.rowHeight());
                    prepareInteractiveChildren();
                    return true;
                }
                if (block.index() == activeTable && block.detail() instanceof ReadOnlyDataTableView<?> table
                        && (!(block.block() instanceof SemanticDocument.Table<?> definition) || !definition.expanded()
                            || key == 257 || key == 335 || key == 32)
                        && table.key(key)) return true;
            }
        }
        boolean handled = scroll.key(key, 14, Math.max(14, bounds.height() - 24));
        if (handled) prepareInteractiveChildren();
        return handled;
    }

    @Override public void focused(boolean focused) {
        if (!focused) activeTable = null;
    }

    /** Called by the host's foreground tooltip pass, after article/table scissors are released. */
    public void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!bounds.inset(1).contains(mouseX, mouseY)) return;
        for (ItemHit hit : itemHits) {
            if (!hit.bounds().contains(mouseX, mouseY)) continue;
            graphics.renderTooltip(font, font.split(hit.label(), Math.max(1, Math.min(320, graphics.guiWidth() - 24))),
                    mouseX, mouseY);
            return;
        }
        for (BlockLayout block : layout) {
            if (!(block.detail() instanceof ReadOnlyDataTableView<?> table)) continue;
            var overflow = table.overflowTextAt(mouseX, mouseY);
            if (overflow.isPresent()) {
                graphics.renderTooltip(font, font.split(overflow.get(), Math.max(1, Math.min(320, graphics.guiWidth() - 24))),
                        mouseX, mouseY);
                return;
            }
        }
    }

    private void renderBlock(GuiGraphics graphics, BlockLayout measured, int y,
                             int mouseX, int mouseY, float partialTick) {
        int x = bounds.x() + PADDING;
        int width = Math.max(0, bounds.width() - PADDING * 2 - 3);
        SemanticDocument.Block block = measured.block();
        if (block instanceof SemanticDocument.Heading heading) {
            int color = heading.level() == SemanticDocument.HeadingLevel.SECTION
                    ? AscendanceUiPalette.argb(AscendanceUiPalette.INFORMATION)
                    : AscendanceUiPalette.argb(AscendanceUiPalette.SPECIAL);
            drawLines(graphics, castLines(measured.detail()), x, y,
                    heading.level() == SemanticDocument.HeadingLevel.SECTION ? font.lineHeight + 2 : font.lineHeight + 1,
                    color);
            graphics.fill(x, y + measured.height() - 1, x + width, y + measured.height(),
                    AscendanceUiPalette.argb(AscendanceUiPalette.DIVIDER));
        } else if (block instanceof SemanticDocument.Paragraph) {
            drawLines(graphics, castLines(measured.detail()), x, y, font.lineHeight + 2,
                    AscendanceUiPalette.argb(AscendanceUiPalette.PRIMARY_TEXT));
        } else if (block instanceof SemanticDocument.OrderedSteps) {
            int lineY = y;
            List<List<FormattedCharSequence>> all = castNestedLines(measured.detail());
            for (int index = 0; index < all.size(); index++) {
                Component marker = Component.translatable("gui.essence_ascendance.content.step", index + 1);
                graphics.drawString(font, marker, x, lineY,
                        AscendanceUiPalette.argb(AscendanceUiPalette.INTERACTIVE), false);
                drawLines(graphics, all.get(index), x + 22, lineY, font.lineHeight + 2,
                        AscendanceUiPalette.argb(AscendanceUiPalette.PRIMARY_TEXT));
                lineY += Math.max(1, all.get(index).size()) * (font.lineHeight + 2) + 3;
            }
        } else if (block instanceof SemanticDocument.Callout callout) {
            UiBounds calloutBounds = new UiBounds(x, y, width, measured.height());
            int accent = switch (callout.kind()) {
                case NOTE -> AscendanceUiPalette.INFORMATION;
                case WARNING -> AscendanceUiPalette.WARNING;
                case TIP -> AscendanceUiPalette.SUCCESS;
            };
            FullscreenControls.panel(graphics, calloutBounds,
                    AscendanceUiPalette.controlHoverArgb(accent), AscendanceUiPalette.argb(accent));
            graphics.drawString(font, callout.title(), x + 7, y + 5, AscendanceUiPalette.argb(accent), false);
            drawLines(graphics, castLines(measured.detail()), x + 7, y + font.lineHeight + 10,
                    font.lineHeight + 2, AscendanceUiPalette.argb(AscendanceUiPalette.PRIMARY_TEXT));
        } else if (block instanceof SemanticDocument.Illustration illustration) {
            IllustrationView.Layout raw = (IllustrationView.Layout) measured.detail();
            IllustrationView.Layout shifted = shift(raw, x + Math.max(0, (width - raw.bounds().width()) / 2), y);
            IllustrationView.render(graphics, font, illustration, shifted, partialTick, illustrations);
        } else if (block instanceof SemanticDocument.ItemIllustration illustration) {
            IllustrationView.Layout raw = (IllustrationView.Layout) measured.detail();
            IllustrationView.Layout shifted = shift(raw, x + Math.max(0, (width - raw.bounds().width()) / 2), y);
            IllustrationView.render(graphics, font, itemIllustrationAdapter(illustration), shifted, partialTick,
                    (draw, ignored, figure, tick) -> ItemIllustrationRenderer.renderStack(
                            draw, illustration.item().stack(), figure));
        } else if (block instanceof SemanticDocument.Icon icon) {
            UiBounds figure = new UiBounds(x + 2, y + 2, 20, 20);
            SemanticDocument.Illustration adapter = new SemanticDocument.Illustration(
                    icon.resource(), Component.empty(), 20, 20);
            graphics.flush();
            graphics.pose().pushPose();
            try {
                FullscreenViewport.withClip(graphics, figure,
                        () -> illustrations.render(graphics, adapter, figure, partialTick));
                graphics.flush();
            } finally {
                graphics.pose().popPose();
            }
            graphics.drawString(font, icon.label(), x + 29, y + 7,
                    AscendanceUiPalette.argb(AscendanceUiPalette.PRIMARY_TEXT), false);
        } else if (block instanceof SemanticDocument.ItemRow items) {
            int columns = (Integer) measured.detail();
            for (int index = 0; index < items.items().size(); index++) {
                UiBounds figure = itemBounds(x, y, columns, index);
                ItemIllustrationRenderer.renderStack(graphics, items.items().get(index).stack(), figure);
            }
        } else if (block instanceof SemanticDocument.StatRows stats) {
            List<StatRowLayout> layouts = castStatRows(measured.detail());
            for (int index = 0; index < stats.rows().size(); index++) {
                StatRowLayout row = layouts.get(index);
                int rowY = y + row.y();
                drawLines(graphics, row.label(), x + 4, rowY + 3, font.lineHeight + 1,
                        AscendanceUiPalette.argb(AscendanceUiPalette.MUTED_TEXT));
                int valueY = rowY + 3;
                for (FormattedCharSequence line : row.value()) {
                    graphics.drawString(font, line, x + width - 4 - font.width(line), valueY,
                            AscendanceUiPalette.argb(AscendanceUiPalette.PRIMARY_TEXT), false);
                    valueY += font.lineHeight + 1;
                }
                graphics.fill(x, rowY + row.height() - 1, x + width, rowY + row.height(), 0x66535B68);
            }
        } else if (block instanceof SemanticDocument.Requirements requirements) {
            List<RequirementRowLayout> layouts = castRequirementRows(measured.detail());
            for (int index = 0; index < requirements.rows().size(); index++) {
                SemanticDocument.Requirement requirement = requirements.rows().get(index);
                RequirementRowLayout row = layouts.get(index);
                int rowY = y + row.y();
                int color = switch (requirement.status()) {
                    case MET -> AscendanceUiPalette.SUCCESS;
                    case UNMET -> AscendanceUiPalette.ERROR;
                    case INFORMATION -> AscendanceUiPalette.INFORMATION;
                };
                Component status = Component.translatable("gui.essence_ascendance.requirement."
                        + requirement.status().name().toLowerCase(java.util.Locale.ROOT));
                graphics.drawString(font, status, x + 4, rowY + 3, AscendanceUiPalette.argb(color), false);
                drawLines(graphics, row.label(), x + 25, rowY + 3, font.lineHeight + 1,
                        AscendanceUiPalette.argb(AscendanceUiPalette.PRIMARY_TEXT));
                int detailY = rowY + 6 + Math.max(1, row.label().size()) * (font.lineHeight + 1);
                drawLines(graphics, row.detail(), x + 25, detailY, font.lineHeight + 2,
                        AscendanceUiPalette.argb(AscendanceUiPalette.MUTED_TEXT));
            }
        } else if (measured.detail() instanceof ReadOnlyDataTableView<?> table) {
            table.render(graphics, font, mouseX, mouseY);
        } else if (block instanceof SemanticDocument.Links links) {
            List<LinkRowLayout> rows = castLinkRows(measured.detail());
            for (int index = 0; index < links.links().size(); index++) {
                LinkRowLayout row = rows.get(index);
                UiBounds linkBounds = new UiBounds(x, y + row.y(), width, row.height());
                boolean hovered = linkBounds.contains(mouseX, mouseY);
                int color = hovered
                        ? AscendanceUiPalette.argb(AscendanceUiPalette.INTERACTIVE)
                        : AscendanceUiPalette.argb(AscendanceUiPalette.INFORMATION);
                List<FormattedCharSequence> lines = hovered ? row.lines().stream()
                        .map(line -> StyledTextLayout.recolor(line, AscendanceUiPalette.INTERACTIVE)).toList()
                        : row.lines();
                drawLines(graphics, lines, x + 5, y + row.y() + 3, font.lineHeight + 1, color);
            }
        }
    }

    private void prepareInteractiveChildren() {
        if (font == null || document == null) return;
        List<LinkHit> hits = new ArrayList<>();
        List<ItemHit> figures = new ArrayList<>();
        for (BlockLayout block : layout) {
            UiBounds blockBounds = blockBounds(block);
            if (block.detail() instanceof ReadOnlyDataTableView<?> table) table.prepare(font, blockBounds);
            if (block.block() instanceof SemanticDocument.ItemRow items) {
                for (int index = 0; index < items.items().size(); index++)
                    figures.add(new ItemHit(itemBounds(blockBounds.x(), blockBounds.y(), (Integer) block.detail(), index),
                            items.items().get(index).label()));
            }
            if (block.block() instanceof SemanticDocument.ItemIllustration illustration) {
                IllustrationView.Layout raw = (IllustrationView.Layout) block.detail();
                int width = Math.max(0, bounds.width() - PADDING * 2 - 3);
                IllustrationView.Layout shifted = shift(raw,
                        blockBounds.x() + Math.max(0, (width - raw.bounds().width()) / 2), blockBounds.y());
                figures.add(new ItemHit(shifted.figure(), illustration.item().label()));
            }
            if (block.block() instanceof SemanticDocument.Links links) {
                List<LinkRowLayout> rows = castLinkRows(block.detail());
                for (int index = 0; index < links.links().size(); index++) {
                    LinkRowLayout row = rows.get(index);
                    hits.add(new LinkHit(new UiBounds(blockBounds.x(), blockBounds.y() + row.y(),
                            blockBounds.width(), row.height()), links.links().get(index).target()));
                }
            }
        }
        linkHits = List.copyOf(hits);
        itemHits = List.copyOf(figures);
    }

    private UiBounds blockBounds(BlockLayout block) {
        return new UiBounds(bounds.x() + PADDING, screenY(block.y()),
                Math.max(0, bounds.width() - PADDING * 2 - 3), block.height());
    }

    private int screenY(int contentY) { return bounds.y() + contentY - scroll.offset(); }

    private static SemanticDocument.Illustration itemIllustrationAdapter(
            SemanticDocument.ItemIllustration illustration) {
        return new SemanticDocument.Illustration(illustration.item().resource(), illustration.item().caption(),
                illustration.preferredWidth(), illustration.preferredHeight());
    }

    private void renderScrollbar(GuiGraphics graphics) {
        if (scroll.maximumOffset() <= 0 || bounds.height() <= 2) return;
        int trackHeight = bounds.height() - 2;
        int thumbHeight = Math.max(10, trackHeight * bounds.height() / Math.max(bounds.height(), contentHeight));
        int travel = Math.max(0, trackHeight - thumbHeight);
        int y = bounds.y() + 1 + travel * scroll.offset() / Math.max(1, scroll.maximumOffset());
        graphics.fill(bounds.right() - 3, bounds.y() + 1, bounds.right() - 1, bounds.bottom() - 1, 0x88535B68);
        graphics.fill(bounds.right() - 3, y, bounds.right() - 1, y + thumbHeight,
                AscendanceUiPalette.argb(AscendanceUiPalette.BORDER));
    }

    private static int wrappedHeight(Font font, Component component, int width, int lineHeight) {
        return Math.max(lineHeight, font.split(component, Math.max(1, width)).size() * lineHeight);
    }

    private void drawLines(GuiGraphics graphics, List<FormattedCharSequence> lines,
                           int x, int y, int lineHeight, int color) {
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, y, color, false);
            y += lineHeight;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<FormattedCharSequence> castLines(Object value) {
        return (List<FormattedCharSequence>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<List<FormattedCharSequence>> castNestedLines(Object value) {
        return (List<List<FormattedCharSequence>>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<StatRowLayout> castStatRows(Object value) {
        return (List<StatRowLayout>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<RequirementRowLayout> castRequirementRows(Object value) {
        return (List<RequirementRowLayout>) value;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ReadOnlyDataTableView<?> tableView(SemanticDocument.Table<?> table) {
        return new ReadOnlyDataTableView((ReadOnlyDataTable) table.data(), navigate);
    }

    /** Labels and explanations share the available line width, not competing columns. */
    public static int requirementTextWidth(int width) { return Math.max(1, width - 29); }

    public static int visibleTableRows(SemanticDocument.Table<?> table) {
        return Math.max(1, table.expanded() ? table.data().rows().size() : Math.min(7, table.data().rows().size()));
    }

    private static UiBounds itemBounds(int x, int y, int columns, int index) {
        return new UiBounds(x + (index % columns) * 38 + 3, y + (index / columns) * 38 + 3, 32, 32);
    }

    private static Component linkLabel(SemanticDocument.Link link) {
        return Component.translatable("gui.essence_ascendance.link."
                + link.relation().name().toLowerCase(java.util.Locale.ROOT), link.label());
    }

    @SuppressWarnings("unchecked")
    private static List<LinkRowLayout> castLinkRows(Object detail) { return (List<LinkRowLayout>) detail; }

    private static IllustrationView.Layout shift(IllustrationView.Layout raw, int x, int y) {
        int dx = x - raw.bounds().x();
        int dy = y - raw.bounds().y();
        return new IllustrationView.Layout(shift(raw.bounds(), dx, dy), shift(raw.figure(), dx, dy),
                shift(raw.caption(), dx, dy), raw.captionLines());
    }

    private static UiBounds shift(UiBounds bounds, int dx, int dy) {
        return new UiBounds(bounds.x() + dx, bounds.y() + dy, bounds.width(), bounds.height());
    }

}
