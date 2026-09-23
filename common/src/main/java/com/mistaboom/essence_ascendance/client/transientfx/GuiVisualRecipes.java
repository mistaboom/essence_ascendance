package com.mistaboom.essence_ascendance.client.transientfx;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.procedural.GuiProceduralGeometry;
import com.mistaboom.essence_ascendance.visual.transientfx.TransientVisualIds;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** GUI recipe identities remain local and use the existing shared GUI primitive toolkit. */
public final class GuiVisualRecipes {
    public static final ResourceLocation ACKNOWLEDGE = id("acknowledge");
    public static final ResourceLocation ANVIL_COMPRESSION = TransientVisualIds.GUI_ANVIL_COMPRESSION;
    public static final ResourceLocation GRINDSTONE_SWEEP = TransientVisualIds.GUI_GRINDSTONE_SWEEP;
    public static final ResourceLocation ENCHANTING_GLYPH = TransientVisualIds.GUI_ENCHANTING_GLYPH;
    public static final ResourceLocation TRADE_ACKNOWLEDGE = TransientVisualIds.GUI_TRADE_ACKNOWLEDGE;
    public static final ResourceLocation PROCESSOR_COMPLETE = TransientVisualIds.GUI_PROCESSOR_COMPLETE;
    private static final Map<ResourceLocation, GuiVisualRecipe> RECIPES = new LinkedHashMap<>();

    static {
        register(ACKNOWLEDGE, new Acknowledge());
        register(ANVIL_COMPRESSION, new AnvilCompression());
        register(GRINDSTONE_SWEEP, new GrindstoneSweep());
        register(ENCHANTING_GLYPH, new EnchantingGlyph());
        register(TRADE_ACKNOWLEDGE, new TradeAcknowledge());
        register(PROCESSOR_COMPLETE, new ProcessorComplete());
    }

    private GuiVisualRecipes() { }

    public static void register(ResourceLocation id, GuiVisualRecipe recipe) {
        if (RECIPES.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(recipe)) != null)
            throw new IllegalArgumentException("Duplicate GUI visual recipe: " + id);
    }

    public static GuiVisualRecipe get(ResourceLocation id) { return RECIPES.get(id); }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, path);
    }

    private static final class Acknowledge implements GuiVisualRecipe {
        @Override public void renderPrimary(GuiVisualRenderContext context) {
            int x = context.anchor().centerX();
            int y = context.anchor().centerY();
            double base = Math.max(5.0, Math.max(context.anchor().width(), context.anchor().height()) * 0.55);
            double radius = (base + 18.0 * context.easedProgress()) * context.scale();
            int alpha = (int) (context.envelope() * (90 + 34 * context.intensity()));
            int color = GuiProceduralGeometry.opacity(context.rgb(), alpha);
            double rotation = context.variation(0) * Math.PI * 2.0 + context.progress();
            GuiProceduralGeometry.brokenOrbit(context.graphics(), x, y, radius, radius * 0.62,
                    4, 3, rotation, 0.2, color);
            int crystalRadius = Math.max(2, (int) Math.round((5.0 - context.progress() * 2.0) * context.scale()));
            GuiProceduralGeometry.crystal(context.graphics(), x, y, crystalRadius,
                    1.0 - context.progress() * 0.35, color);
        }

        @Override public void renderDetail(GuiVisualRenderContext context) {
            int x = context.anchor().centerX();
            int y = context.anchor().centerY();
            double base = Math.max(7.0, Math.max(context.anchor().width(), context.anchor().height()) * 0.6);
            double radius = (base + 11.0 * context.easedProgress()) * context.scale();
            int color = GuiProceduralGeometry.opacity(context.rgb(), (int) (context.envelope() * 72));
            double rotation = context.variation(1) * Math.PI * 2.0 - context.progress() * 1.4;
            GuiProceduralGeometry.orbit(context.graphics(), x, y, radius * 0.72, radius * 0.45,
                    8, rotation, color);
            GuiProceduralGeometry.spokes(context.graphics(), x, y, radius * 0.82, radius,
                    8, rotation, color);
        }
    }

    private static final class AnvilCompression implements GuiVisualRecipe {
        @Override public void renderPrimary(GuiVisualRenderContext context) {
            int x = context.anchor().centerX(), y = context.anchor().centerY();
            double p = context.easedProgress();
            double radius = (13.0 - 7.0 * p) * context.scale();
            int color = GuiProceduralGeometry.opacity(context.rgb(),
                    (int) (context.envelope() * (125 + context.intensity() * 25)));
            GuiProceduralGeometry.brokenOrbit(context.graphics(), x, y, radius, radius * 0.62,
                    4, 2, context.variation(0) * Math.PI * 2.0 + p, 0.28, color);
            GuiProceduralGeometry.spokes(context.graphics(), x, y, radius + 1, radius + 3,
                    4, Math.PI * 0.25, GuiProceduralGeometry.opacity(context.rgb(), (int) (context.envelope() * 48)));
        }
    }

    private static final class GrindstoneSweep implements GuiVisualRecipe {
        @Override public void renderPrimary(GuiVisualRenderContext context) {
            int x = context.anchor().centerX(), y = context.anchor().centerY();
            double p = context.easedProgress();
            int color = GuiProceduralGeometry.opacity(context.rgb(), (int) (context.envelope() * 145));
            double start = -Math.PI * 0.85 + p * Math.PI * 1.25;
            GuiProceduralGeometry.arc(context.graphics(), x, y, 10 * context.scale(), 7 * context.scale(),
                    start, Math.PI * 1.2, 7, color);
            GuiProceduralGeometry.arc(context.graphics(), x, y, 7 * context.scale(), 5 * context.scale(),
                    start + 0.55, Math.PI * 0.65, 4,
                    GuiProceduralGeometry.opacity(context.rgb(), (int) (context.envelope() * 52)));
        }
    }

    private static final class EnchantingGlyph implements GuiVisualRecipe {
        @Override public void renderPrimary(GuiVisualRenderContext context) {
            int x = context.anchor().centerX(), y = context.anchor().centerY();
            double radius = (8.0 + context.easedProgress() * 3.0) * context.scale();
            int color = GuiProceduralGeometry.opacity(context.rgb(), (int) (context.envelope() * 135));
            GuiProceduralGeometry.orbit(context.graphics(), x, y, radius, radius * 0.62,
                    6, context.variation(0) * Math.PI * 2.0 + context.progress() * 1.3, color);
            GuiProceduralGeometry.spokes(context.graphics(), x, y, radius + 1, radius + 3,
                    6, Math.PI / 6.0, GuiProceduralGeometry.opacity(context.rgb(), (int) (context.envelope() * 45)));
        }
    }

    private static final class TradeAcknowledge implements GuiVisualRecipe {
        @Override public void renderPrimary(GuiVisualRenderContext context) {
            int x = context.anchor().centerX(), y = context.anchor().centerY();
            double p = context.easedProgress();
            int color = GuiProceduralGeometry.opacity(context.rgb(), (int) (context.envelope() * 135));
            GuiProceduralGeometry.arc(context.graphics(), x, y, 11 * context.scale(), 7 * context.scale(),
                    -Math.PI * 0.8 + p, Math.PI * 1.35, 7, color);
            int dx = (int) Math.round(Math.cos(p * Math.PI * 1.5) * 7 * context.scale());
            int dy = (int) Math.round(Math.sin(p * Math.PI * 1.5) * 4 * context.scale());
            GuiProceduralGeometry.crystal(context.graphics(), x + dx, y + dy, 2, 1.0, color);
            GuiProceduralGeometry.beam(context.graphics(), x - 5, y + 4, x + 5, y - 3,
                    GuiProceduralGeometry.opacity(context.rgb(), (int) (context.envelope() * 38)));
        }
    }

    private static final class ProcessorComplete implements GuiVisualRecipe {
        @Override public void renderPrimary(GuiVisualRenderContext context) {
            int x = context.anchor().centerX(), y = context.anchor().centerY();
            double p = context.easedProgress();
            int color = GuiProceduralGeometry.opacity(context.rgb(), (int) (context.envelope() * 140));
            GuiProceduralGeometry.crystal(context.graphics(), x, y - (int) Math.round(p * 4),
                    3, 1.0 - p * 0.18, color);
            GuiProceduralGeometry.arc(context.graphics(), x, y, (8 + p * 4) * context.scale(),
                    (5 + p * 2) * context.scale(), -Math.PI * 0.8 + p,
                    Math.PI * 1.25, 7, color);
        }

        @Override public void renderDetail(GuiVisualRenderContext context) {
            int x = context.anchor().centerX(), y = context.anchor().centerY();
            int color = GuiProceduralGeometry.opacity(context.rgb(), (int) (context.envelope() * 42));
            GuiProceduralGeometry.beam(context.graphics(), x - 5, y + 5, x + 5, y - 4, color);
        }
    }
}
