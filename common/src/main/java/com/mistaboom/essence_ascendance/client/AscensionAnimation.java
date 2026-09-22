package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.client.procedural.GuiProceduralGeometry;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import com.mistaboom.essence_ascendance.visual.ProceduralMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Client-only Ascension event and replaceable first presentation. Never infers success from a request. */
public final class AscensionAnimation {
    private static final List<Consumer<ResourceLocation>> LISTENERS = new ArrayList<>();
    private static final long DURATION_NANOS = 3_200_000_000L;
    private static ResourceLocation tier;
    private static long started;

    private AscensionAnimation() { }

    /** Optional client presentation extensions may add final art, sound or world particles here. */
    public static void register(Consumer<ResourceLocation> listener) { LISTENERS.add(Objects.requireNonNull(listener)); }

    /** Called after a successful transaction acknowledgement AND its authoritative state snapshot. */
    public static void confirmed(ResourceLocation newTier) {
        tier = Objects.requireNonNull(newTier);
        started = System.nanoTime();
        for (var listener : List.copyOf(LISTENERS)) listener.accept(newTier);
    }

    public static void clear() { tier = null; started = 0; }

    public static void render(GuiGraphics graphics) {
        if (tier == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) { clear(); return; }
        double age = (System.nanoTime() - started) / (double) DURATION_NANOS;
        if (age >= 1 || age < 0) { clear(); return; }
        if (client.screen != null || client.options.hideGui) return;
        double fade = ProceduralMotion.fadeEnvelope(age, 7, 4);
        int cx = graphics.guiWidth() / 2;
        int cy = Math.max(36, graphics.guiHeight() / 3);
        int radius = 17 + (int) (age * 36);
        int alpha = (int) (fade * 165);
        var categories = EssenceRegistry.values().stream().toList();
        for (int i = 0; i < categories.size(); i++) {
            double angle = Math.PI * 2 * i / categories.size() + age * 0.6;
            int x = cx + (int) (Math.cos(angle) * radius * 1.4);
            int y = cy + (int) (Math.sin(angle) * radius * 0.6);
            int color = GuiProceduralGeometry.opacity(AscendancePalette.categoryRgb(categories.get(i).id()), alpha);
            GuiProceduralGeometry.orbit(graphics, x, y, 3, 4, 4, age, color);
            GuiProceduralGeometry.beam(graphics, x, y, cx, cy, GuiProceduralGeometry.opacity(color, alpha / 3));
        }
        GuiProceduralGeometry.orbit(graphics, cx, cy, radius * 1.6, radius * 0.65, 48, age,
                GuiProceduralGeometry.opacity(AscendancePalette.tierMetalRgb(tier), alpha / 2));
        var tierName = AscendanceTierRegistry.get(tier).map(EssenceText::ascendanceTier)
                .orElseGet(() -> EssenceText.gui("nexus.attunement.title"));
        var title = EssenceText.gui("nexus.attunement.ascended", tierName);
        float scale = Math.min(1, (graphics.guiWidth() - 24) / (float) Math.max(1, client.font.width(title)));
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy - 4, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawCenteredString(client.font, title, 0, 0,
                GuiProceduralGeometry.opacity(AscendancePalette.tierMetalRgb(tier), Math.max(8, (int) (fade * 255))));
        graphics.pose().popPose();
    }
}
