package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.attunement.AttunementContribution;
import com.mistaboom.essence_ascendance.attunement.AttunementSnapshot;
import com.mistaboom.essence_ascendance.client.nexus.NexusConstellationLayout;
import com.mistaboom.essence_ascendance.client.nexus.NexusNavigationState;
import com.mistaboom.essence_ascendance.client.procedural.GuiProceduralGeometry;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** The Nexus constellation and its flowing semantic detail page share one registry-derived model. */
public final class NexusAttunementView {
    public enum Click { NONE, HANDLED, ASCEND }
    private static final int TEXT = 0xFFF0EDF4;
    private static final int MUTED = 0xFFB6AFBF;
    private static final int DIM = 0xFF7F7889;
    private static final int COMPLETE = 0xFF78C69A;
    private String selectedCategory;
    private int focusedIndex = -1;
    private int scroll;
    private int maximumScroll;
    private int left, top, width, height;
    private NexusConstellationLayout layout;
    private AttunementSnapshot snapshot = AttunementSnapshot.empty();
    private SemanticTooltip hovered;

    public NexusNavigationState.Attunement navigation() {
        return new NexusNavigationState.Attunement(selectedCategory, scroll, focusedIndex);
    }

    public void restoreNavigation(NexusNavigationState.Attunement navigation) {
        selectedCategory = navigation.category(); scroll = navigation.scroll(); focusedIndex = navigation.focusedIndex();
        // The next authoritative render resolves removed categories and clamps scrolling to the current layout.
    }

    public void render(GuiGraphics graphics, Font font, AttunementSnapshot state,
                       Component currentTier, Component nextTier, int left, int top,
                       int width, int height, int mouseX, int mouseY, double time) {
        this.snapshot = state;
        focusedIndex = Math.clamp(focusedIndex, -1, state.categories().size());
        this.left = left;
        this.top = top;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        hovered = null;
        AttunementSnapshot.Category selected = selected();
        if (selected != null) {
            renderDetail(graphics, font, selected);
            return;
        }
        selectedCategory = null;
        layout = NexusConstellationLayout.create(left, top, this.width, this.height, state.categories().size());
        int cx = layout.centerX(), cy = layout.centerY(), centerRadius = layout.medallionRadius();
        boolean maximum = state.maximumTier();
        boolean ready = maximum || state.ready();
        graphics.enableScissor(left, top, left + this.width, top + this.height);
        try {
            // Faint procedural star field: stable across frames, independent of category identity.
            for (int i = 0; i < 32; i++) {
                int sx = left + Math.floorMod(i * 127 + 29, this.width);
                int sy = top + Math.floorMod(i * 73 + 17, this.height);
                int alpha = 18 + (int) (15 * (1 + Math.sin(time / 31 + i)));
                graphics.fill(sx, sy, sx + 1, sy + 1, GuiProceduralGeometry.opacity(0xFFC3C3CA, alpha));
            }
            for (var node : layout.nodes()) {
                var category = state.categories().get(node.index());
                boolean complete = maximum || category.completed();
                int color = color(category);
                GuiProceduralGeometry.beam(graphics, node.x(), node.y(), cx, cy,
                        GuiProceduralGeometry.opacity(color, complete ? 150 : 24));
                if (complete) {
                    double phase = (time / 60 + node.index() * 0.19) % 1;
                    GuiProceduralGeometry.travelingCrystal(graphics, node.x(), node.y(), cx, cy,
                            phase, 2, 1, color);
                }
            }
            int centralColor = AscendancePalette.tierPrimaryArgb(ClientEssenceState.snapshot().tierId());
            int centralMetal = AscendancePalette.tierMetalArgb(ClientEssenceState.snapshot().tierId());
            GuiProceduralGeometry.orbit(graphics, cx, cy, centerRadius + 4, centerRadius + 4,
                    12, time / 240, GuiProceduralGeometry.opacity(centralMetal, ready ? 170 : 100));
            GuiProceduralGeometry.crystal(graphics, cx, cy, centerRadius,
                    maximum ? 1 : state.completedCategories() / (double) Math.max(1, state.requiredCategories()), centralColor);
            // Tier names remain inside the medallion and scale to the allocated central width.
            centeredFit(graphics, font, currentTier, cx, cy - 11, centerRadius * 2 - 2,
                    AscendancePalette.tierMetalArgb(ClientEssenceState.snapshot().tierId()));
            centeredFit(graphics, font, maximum ? EssenceText.gui("nexus.maximum") : Component.literal("↓"),
                    cx, cy - 1, centerRadius * 2 - 2, TEXT);
            centeredFit(graphics, font, maximum ? currentTier : nextTier, cx, cy + 9,
                    centerRadius * 2 - 2, TEXT);
            int sockets = maximum ? state.categories().size() : state.requiredCategories();
            for (int i = 0; i < sockets; i++) {
                double a = Math.PI * 2 * i / Math.max(1, sockets) - Math.PI / 2;
                int sx = cx + (int) (Math.cos(a) * (centerRadius + 7));
                int sy = cy + (int) (Math.sin(a) * (centerRadius + 7));
                GuiProceduralGeometry.crystal(graphics, sx, sy, 2,
                        maximum || i < state.completedCategories() ? 1 : 0, centralColor);
            }
            for (var node : layout.nodes()) {
                var category = state.categories().get(node.index());
                int color = color(category);
                boolean complete = maximum || category.completed();
                boolean available = category.methods().stream().anyMatch(AttunementSnapshot.Method::available);
                boolean active = complete || category.progress() > 0;
                boolean focus = focusedIndex == node.index() || node.contains(mouseX, mouseY);
                int radius = node.radius();
                if (active || focus) {
                    int alpha = focus ? 180 : 60 + (int) (30 * (1 + Math.sin(time / 15 + node.index())));
                    GuiProceduralGeometry.orbit(graphics, node.x(), node.y(), radius + 4, radius + 4, 4,
                            0, GuiProceduralGeometry.opacity(color, alpha));
                }
                GuiProceduralGeometry.crystal(graphics, node.x(), node.y(), radius,
                        complete ? 1 : category.percent() / 100.0, available || complete ? color : DIM);
                int methods = category.methods().size();
                for (int m = 0; m < methods; m++) {
                    var method = category.methods().get(m);
                    double a = Math.PI * 2 * m / Math.max(1, methods) + time / 450;
                    int gx = node.x() + (int) (Math.cos(a) * (radius + 8));
                    int gy = node.y() + (int) (Math.sin(a) * (radius + 8));
                    boolean repeated = category.recent().stream().anyMatch(r -> r.activityId().equals(method.activityId())
                            && r.repetitionMultiplier() < 0.65);
                    int glyphColor = !method.available() ? DIM : repeated ? 0xFFD1B36A
                            : method.progress() > 0 || maximum ? color : GuiProceduralGeometry.opacity(color, 100);
                    GuiProceduralGeometry.orbit(graphics, gx, gy, 2, 2, 3 + m % 3, a, glyphColor);
                }
                int labelY = node.y() + (Math.sin(node.angle()) < -0.1 ? -radius - 27 : radius + 13);
                Component label = this.width < 420 ? compactName(category) : categoryName(category);
                centeredFit(graphics, font, label, node.x(), labelY, Math.max(24, this.width / 4 - 4), color);
                centeredFit(graphics, font, EssenceText.gui("nexus.attunement.percent", complete ? 100 : category.percent()),
                        node.x(), labelY + 10, Math.max(24, this.width / 4 - 4), complete ? COMPLETE : MUTED);
                if (focus) hovered = overview(category, maximum);
            }
            if (layout.medallionContains(mouseX, mouseY) || focusedIndex == state.categories().size()) {
                hovered = new SemanticTooltip().title(EssenceText.gui("nexus.attunement.title"), centralMetal)
                        .description(maximum ? EssenceText.gui("nexus.maximum_achieved")
                                : EssenceText.gui("nexus.attunement.seals", state.completedCategories(), state.requiredCategories()))
                        .hint(EssenceText.gui(ready && !maximum ? "nexus.ready_to_ascend" : "nexus.attunement.choice"));
            }
        } finally { graphics.disableScissor(); }
    }

    private SemanticTooltip overview(AttunementSnapshot.Category category, boolean maximum) {
        SemanticTooltip text = new SemanticTooltip().title(categoryName(category), color(category))
                .field(EssenceText.gui("nexus.attunement.percent", maximum ? 100 : category.percent()))
                .description(EssenceText.gui("nexus.attunement.methods_mix"))
                .field(EssenceText.gui("nexus.attunement.acceleration", acceleration(category)))
                .hint(EssenceText.gui(maximum || category.completed()
                        ? "nexus.attunement.complete" : "nexus.attunement.open"));
        return text;
    }

    private void renderDetail(GuiGraphics graphics, Font font, AttunementSnapshot.Category category) {
        int headerHeight = font.lineHeight + 14;
        MachineScreenUi.outline(graphics, left + 3, top + 1, Math.min(width - 6, 112), headerHeight, color(category));
        graphics.drawString(font, EssenceText.gui("nexus.attunement.back"), left + 8, top + 6, TEXT, false);
        List<FormattedCharSequence> lines = detail(category).wrap(font, Math.max(1, width - 28));
        int lineHeight = font.lineHeight + 3;
        int bodyTop = top + headerHeight + 7;
        int bodyBottom = top + height - lineHeight - 4;
        int rows = Math.max(1, (bodyBottom - bodyTop) / lineHeight);
        maximumScroll = Math.max(0, lines.size() - rows);
        scroll = Math.clamp(scroll, 0, maximumScroll);
        graphics.enableScissor(left + 6, bodyTop, left + width - 6, Math.max(bodyTop + 1, bodyBottom));
        try {
            for (int i = scroll; i < Math.min(lines.size(), scroll + rows); i++)
                graphics.drawString(font, lines.get(i), left + 12, bodyTop + (i - scroll) * lineHeight, TEXT, false);
        } finally { graphics.disableScissor(); }
        if (maximumScroll > 0) {
            Component hint = EssenceText.gui("nexus.attunement.scroll", scroll + 1, maximumScroll + 1);
            centeredFit(graphics, font, hint, left + width / 2, top + height - font.lineHeight - 2, width - 20, MUTED);
        }
    }

    private SemanticTooltip detail(AttunementSnapshot.Category category) {
        SemanticTooltip text = overview(category, snapshot.maximumTier())
                .gap().section(EssenceText.gui("nexus.attunement.methods"));
        for (var method : category.methods()) {
            text.field(EssenceText.gui("nexus.attunement.method_progress", Component.translatable(method.labelKey()), method.percent())
                    .withStyle(method.available() ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
            text.detail(Component.translatable(method.descriptionKey()));
            if (!method.available()) text.detail(EssenceText.gui("nexus.attunement.method_unavailable"));
        }
        text.gap().section(EssenceText.gui("nexus.attunement.recent"));
        if (category.recent().isEmpty()) text.detail(EssenceText.gui("nexus.attunement.no_recent"));
        for (AttunementContribution result : category.recent()) {
            Component name = category.methods().stream().filter(m -> m.activityId().equals(result.activityId()))
                    .findFirst().map(m -> Component.translatable(m.labelKey()))
                    .orElseGet(() -> EssenceText.gui("nexus.attunement.activity"));
            text.field(EssenceText.gui(result.credited() ? "nexus.attunement.credited" : "nexus.attunement.not_credited", name));
            if (result.credited()) {
                text.detail(EssenceText.gui("nexus.attunement.efficiency",
                        (int) Math.round(result.repetitionMultiplier() * 100),
                        Math.max(0, (int) Math.round((result.varietyMultiplier() - 1) * 100))));
            } else {
                text.detail(Component.translatableWithFallback("attunement.essence_ascendance.reason." + result.rejectionReason(),
                        EssenceText.gui("nexus.attunement.no_outcome").getString()));
            }
        }
        return text;
    }

    public void renderTooltip(GuiGraphics graphics, Font font, int mouseX, int mouseY, int screenWidth) {
        if (hovered == null || selectedCategory != null) return;
        graphics.renderTooltip(font, hovered.wrap(font, TooltipLayout.compactWidth(180, screenWidth)), mouseX, mouseY);
    }

    public Click click(double x, double y) {
        if (selectedCategory != null) {
            if (x >= left + 3 && x < left + Math.min(width - 3, 115) && y >= top && y < top + 25) back();
            return within(x, y) ? Click.HANDLED : Click.NONE;
        }
        if (layout == null) return Click.NONE;
        for (var node : layout.nodes()) if (node.contains(x, y)) {
            open(node.index());
            return Click.HANDLED;
        }
        return layout.medallionContains(x, y) ? Click.ASCEND : Click.NONE;
    }

    public boolean scroll(double x, double y, double amount) {
        if (selectedCategory == null || !within(x, y)) return false;
        scroll = Math.clamp(scroll - (int) Math.signum(amount) * 3, 0, maximumScroll);
        return true;
    }

    /** Existing keyboard input, with no gameplay keybind added. */
    public Click key(int key, boolean backwards) {
        if (selectedCategory != null) {
            if (key == 256 || key == 259) { back(); return Click.HANDLED; }
            if (key == 264 || key == 267) { scroll = Math.min(maximumScroll, scroll + (key == 267 ? 8 : 1)); return Click.HANDLED; }
            if (key == 265 || key == 266) { scroll = Math.max(0, scroll - (key == 266 ? 8 : 1)); return Click.HANDLED; }
            return Click.NONE;
        }
        if (key == 258 || key == 262 || key == 263 || key == 264 || key == 265) {
            int delta = key == 263 || key == 265 || (key == 258 && backwards) ? -1 : 1;
            focusedIndex = Math.floorMod(focusedIndex + delta, snapshot.categories().size() + 1);
            return Click.HANDLED;
        }
        if ((key == 257 || key == 335 || key == 32) && focusedIndex >= 0) {
            if (focusedIndex >= snapshot.categories().size()) return Click.ASCEND;
            open(focusedIndex);
            return Click.HANDLED;
        }
        return Click.NONE;
    }

    public boolean back() {
        if (selectedCategory == null) return false;
        selectedCategory = null;
        scroll = 0;
        return true;
    }

    private void open(int index) { selectedCategory = snapshot.categories().get(index).categoryId(); scroll = 0; }
    private boolean within(double x, double y) { return x >= left && x < left + width && y >= top && y < top + height; }
    private AttunementSnapshot.Category selected() {
        return snapshot.categories().stream().filter(c -> c.categoryId().equals(selectedCategory)).findFirst().orElse(null);
    }
    private static int acceleration(AttunementSnapshot.Category c) { return Math.max(0, (int) Math.round((c.investmentMultiplier() - 1) * 100)); }
    private static int color(AttunementSnapshot.Category category) {
        ResourceLocation id = ResourceLocation.tryParse(category.categoryId());
        return id == null ? MUTED : AscendancePalette.categoryArgb(id);
    }
    private static Component categoryName(AttunementSnapshot.Category category) {
        ResourceLocation id = ResourceLocation.tryParse(category.categoryId());
        return id == null ? EssenceText.gui("nexus.attunement.activity")
                : EssenceRegistry.get(id).map(EssenceText::essenceShort).orElseGet(() -> Component.literal(id.getPath()));
    }
    private static Component compactName(AttunementSnapshot.Category category) {
        ResourceLocation id = ResourceLocation.tryParse(category.categoryId());
        return id == null ? categoryName(category) : ItemEssenceTooltipClientState.compactNameComponent(id);
    }
    private static void centeredFit(GuiGraphics graphics, Font font, Component text,
                                    int x, int y, int maxWidth, int color) {
        float scale = Math.min(1, Math.max(1, maxWidth) / (float) Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawCenteredString(font, text, 0, 0, color);
        graphics.pose().popPose();
    }
}
