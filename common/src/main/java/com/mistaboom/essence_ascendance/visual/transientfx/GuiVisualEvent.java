package com.mistaboom.essence_ascendance.visual.transientfx;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** One compact, server-confirmed invocation of a shared GUI visual recipe. */
public record GuiVisualEvent(
        ResourceLocation recipeId,
        Anchor anchor,
        int slotIndex,
        int offsetX,
        int offsetY,
        float scale,
        float intensity,
        VisualIntensity presentation,
        SemanticVisualColor color,
        int lifetimeTicks,
        long seed,
        float parameterA,
        float parameterB
) {
    public static final int MAX_LIFETIME_TICKS = 20 * 30;

    public GuiVisualEvent {
        Objects.requireNonNull(recipeId, "recipeId");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(presentation, "presentation");
        Objects.requireNonNull(color, "color");
        if (slotIndex < -1 || slotIndex > 4096)
            throw new IllegalArgumentException("GUI visual slot is out of bounds");
        if (!Float.isFinite(scale) || scale <= 0.0F || scale > 16.0F)
            throw new IllegalArgumentException("GUI visual scale is out of bounds");
        if (!Float.isFinite(intensity) || intensity < 0.0F || intensity > 4.0F)
            throw new IllegalArgumentException("GUI visual intensity is out of bounds");
        if (lifetimeTicks < 1 || lifetimeTicks > MAX_LIFETIME_TICKS)
            throw new IllegalArgumentException("GUI visual lifetime is out of bounds");
        if (!Float.isFinite(parameterA) || !Float.isFinite(parameterB))
            throw new IllegalArgumentException("GUI visual parameters must be finite");
    }

    public enum Anchor { RESULT_SLOT, SLOT, CENTERED }
}
