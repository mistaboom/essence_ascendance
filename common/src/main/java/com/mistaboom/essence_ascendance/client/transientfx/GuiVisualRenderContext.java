package com.mistaboom.essence_ascendance.client.transientfx;

import com.mistaboom.essence_ascendance.visual.ProceduralMotion;
import com.mistaboom.essence_ascendance.visual.transientfx.SemanticVisualColor;
import com.mistaboom.essence_ascendance.visual.transientfx.VisualIntensity;
import net.minecraft.client.gui.GuiGraphics;

/** Resolved screen anchor and lifecycle values supplied to a GUI recipe. */
public record GuiVisualRenderContext(
        GuiGraphics graphics,
        GuiAnchor.Rect anchor,
        double progress,
        float scale,
        float intensity,
        VisualIntensity presentation,
        SemanticVisualColor color,
        long seed,
        float parameterA,
        float parameterB
) {
    public double easedProgress() { return ProceduralMotion.smoothStep(progress); }
    public double envelope() { return ProceduralMotion.fadeEnvelope(progress, 8.0, 5.0); }
    public int rgb() { return color.rgb(); }
    public double variation(int lane) {
        long value = seed + 0x9E3779B97F4A7C15L * (lane + 1L);
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }
}
