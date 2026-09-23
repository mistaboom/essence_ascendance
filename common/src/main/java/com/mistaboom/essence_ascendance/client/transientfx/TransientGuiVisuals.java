package com.mistaboom.essence_ascendance.client.transientfx;

import com.mistaboom.essence_ascendance.client.ClientPacketDispatch;
import com.mistaboom.essence_ascendance.network.GuiVisualEventPayload;
import com.mistaboom.essence_ascendance.visual.transientfx.GuiVisualEvent;
import com.mistaboom.essence_ascendance.visual.transientfx.SemanticVisualColor;
import com.mistaboom.essence_ascendance.visual.transientfx.VisualIntensity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Screen-scoped lifecycle and load shedding for short-lived GUI recipes. */
public final class TransientGuiVisuals {
    private static final int ACTIVE_BUDGET = 128;
    private static final int DETAIL_BUDGET = 40;
    private static final List<Active> ACTIVE = new ArrayList<>();
    private static boolean initialized;

    private TransientGuiVisuals() { }

    public static void init() {
        if (initialized) return;
        NetworkManager.registerReceiver(NetworkManager.Side.S2C,
                GuiVisualEventPayload.TYPE, GuiVisualEventPayload.CODEC,
                (payload, context) -> ClientPacketDispatch.queue(context, () -> {
                    var client = Minecraft.getInstance();
                    if (payload.containerId() >= 0 && (client.player == null
                            || client.player.containerMenu.containerId != payload.containerId()
                            || !(client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>))) return;
                    emit(payload.event());
                }));
        initialized = true;
    }

    private static void emit(GuiVisualEvent event) {
        GuiAnchor anchor = switch (event.anchor()) {
            case RESULT_SLOT -> GuiAnchor.resultSlot();
            case SLOT -> GuiAnchor.slot(event.slotIndex());
            case CENTERED -> GuiAnchor.centered(event.offsetX(), event.offsetY());
        };
        emit(event.recipeId(), anchor, event.scale(), event.intensity(), event.presentation(), event.color(),
                event.lifetimeTicks(), event.seed(), event.parameterA(), event.parameterB());
    }

    /** Captures the current screen identity; null intentionally means the normal HUD. */
    public static void emit(ResourceLocation recipeId, GuiAnchor anchor, float scale, float intensity,
                            VisualIntensity presentation, SemanticVisualColor color,
                            int lifetimeTicks, long seed, float parameterA, float parameterB) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(presentation, "presentation");
        Objects.requireNonNull(color, "color");
        if (GuiVisualRecipes.get(recipeId) == null || !Float.isFinite(scale) || scale <= 0 || scale > 16
                || !Float.isFinite(intensity) || intensity < 0 || intensity > 4
                || lifetimeTicks < 1 || lifetimeTicks > 20 * 30
                || !Float.isFinite(parameterA) || !Float.isFinite(parameterB)) return;
        tick();
        int incoming = presentation.budgetCost();
        while (budgetUsed() + incoming > ACTIVE_BUDGET) {
            Active victim = ACTIVE.stream()
                    .filter(active -> active.presentation().ordinal() <= presentation.ordinal())
                    .min(Comparator.comparingInt(active -> active.presentation().ordinal()))
                    .orElse(null);
            if (victim == null) return;
            ACTIVE.remove(victim);
        }
        ACTIVE.add(new Active(recipeId, anchor, Minecraft.getInstance().screen, Minecraft.getInstance().level,
                System.nanoTime(), lifetimeTicks * 50_000_000L, scale, intensity,
                presentation, color, seed, parameterA, parameterB));
    }

    public static void clear() { ACTIVE.clear(); }

    /** Cleanup must also run with F1, a hidden world, or no GUI render pass. */
    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        long now = System.nanoTime();
        ACTIVE.removeIf(active -> active.screen() != client.screen || active.level() != client.level
                || now - active.startedNanos() >= active.durationNanos() || now < active.startedNanos());
    }

    public static void renderHud(GuiGraphics graphics) { render(graphics, true); }
    public static void renderScreen(GuiGraphics graphics) { render(graphics, false); }

    private static void render(GuiGraphics graphics, boolean hudPass) {
        Screen screen = Minecraft.getInstance().screen;
        long now = System.nanoTime();
        tick();
        if (ACTIVE.isEmpty() || hudPass != (screen == null)) return;
        Map<Active, Boolean> detailed = new IdentityHashMap<>();
        int detailCost = 0;
        for (Active active : ACTIVE.stream()
                .sorted(Comparator.comparingInt((Active value) -> value.presentation().ordinal()).reversed())
                .toList()) {
            int cost = active.presentation().budgetCost();
            boolean allow = active.presentation() != VisualIntensity.MICRO
                    && detailCost + cost <= DETAIL_BUDGET;
            detailed.put(active, allow);
            if (allow) detailCost += cost;
        }
        for (Active active : List.copyOf(ACTIVE)) {
            GuiAnchor.Rect anchor = active.anchor().resolve(screen, graphics.guiWidth(), graphics.guiHeight());
            if (anchor == null) continue;
            double progress = (now - active.startedNanos()) / (double) active.durationNanos();
            GuiVisualRecipe recipe = GuiVisualRecipes.get(active.recipeId());
            if (recipe == null) continue;
            GuiVisualRenderContext context = new GuiVisualRenderContext(graphics, anchor, progress,
                    active.scale(), active.intensity(), active.presentation(), active.color(),
                    active.seed(), active.parameterA(), active.parameterB());
            recipe.renderPrimary(context);
            if (detailed.getOrDefault(active, false)) {
                recipe.renderDetail(context);
            }
        }
    }

    private static int budgetUsed() {
        return ACTIVE.stream().mapToInt(active -> active.presentation().budgetCost()).sum();
    }

    private record Active(ResourceLocation recipeId, GuiAnchor anchor, Screen screen, ClientLevel level,
                          long startedNanos, long durationNanos, float scale, float intensity,
                          VisualIntensity presentation, SemanticVisualColor color, long seed,
                          float parameterA, float parameterB) { }
}
